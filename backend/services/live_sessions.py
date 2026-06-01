"""In-memory live chat sessions decoupled from the WebSocket connection.

A ``LiveSession`` owns the running turn (its worker task), survives the socket
dropping, and lets a reconnecting socket re-attach. The connection becomes a
detachable transport; the work no longer dies with it.
"""

import asyncio
import collections
import time
import uuid

from loguru import logger

# How many recent stamped events to retain per session for replay on reconnect.
# A very long disconnect can outrun this; the client then falls back to the
# on-disk transcript via load_history.
OUTBOX_MAX = 5000


class LiveSession:
    def __init__(self, channel, state):
        self.channel = channel
        self.state = state
        self._sink = None
        self._worker = None
        self._pending = {}  # rid -> {"future": Future, "event": stamped dict | None}
        self._seq = 0
        self._outbox = collections.deque(maxlen=OUTBOX_MAX)
        self._lock = asyncio.Lock()  # serialize emit vs. replay so seq order holds

    @property
    def running(self):
        return self._worker is not None and not self._worker.done()

    @property
    def attached(self):
        return self._sink is not None

    async def attach(self, sink, last_seq=0):
        """Bind the socket and replay everything it missed: buffered events with
        ``seq > last_seq``, then re-emit any still-pending permission prompt the
        client had already passed (so an app that lost the dialog gets it back)."""
        async with self._lock:
            self._sink = sink
            for stamped in list(self._outbox):
                if stamped["seq"] > last_seq:
                    await self._send(stamped)
            for entry in list(self._pending.values()):
                event = entry.get("event")
                if event is not None and event["seq"] <= last_seq:
                    await self._send(event)

    async def detach(self, sink):
        """Drop the socket. The worker keeps running — it is not cancelled,
        and pending permission waits are left intact."""
        async with self._lock:
            if self._sink is sink:
                self._sink = None

    async def _emit(self, event):
        async with self._lock:
            self._seq += 1
            stamped = {**event, "seq": self._seq}
            self._outbox.append(stamped)
            await self._send(stamped)
            return stamped

    async def _send(self, stamped):
        sink = self._sink
        if sink is None:
            return
        try:
            await sink(stamped)
        except Exception:
            # The socket died mid-send. Drop it, but keep the worker alive so a
            # reconnecting client can re-attach — that is the whole point.
            if self._sink is sink:
                self._sink = None

    async def _ask(self, payload):
        """Bridge the SDK's permission/question callback to the client and wait
        for the answer. The wait lives on the session, so it survives reconnects."""
        rid = uuid.uuid4().hex
        future = asyncio.get_running_loop().create_future()
        self._pending[rid] = {"future": future, "event": None}
        try:
            self._pending[rid]["event"] = await self._emit(
                {"type": "interaction_request", "id": rid, **payload}
            )
            return await future
        finally:
            self._pending.pop(rid, None)

    def resolve(self, rid, response):
        entry = self._pending.get(rid)
        if entry is None or entry["future"].done():
            return False
        entry["future"].set_result(response)
        return True

    def start(self, runner_factory):
        if self.running:
            return False
        self._worker = asyncio.create_task(self._run(runner_factory))
        return True

    async def interrupt(self):
        """User-requested stop. Cancels the worker, waits for it to unwind, then
        emits ``interrupted`` (and no ``done``). A no-op if no turn is running, so
        it never races a naturally-completing turn into a spurious ``interrupted``."""
        worker = self._worker
        if worker is None or worker.done():
            return
        worker.cancel()
        try:
            await worker
        except asyncio.CancelledError:
            pass
        if worker.cancelled():
            await self._emit({"type": "interrupted"})

    async def _run(self, runner_factory):
        try:
            async for event in runner_factory(self._ask):
                await self._emit(event)
        except asyncio.CancelledError:
            raise  # interrupt(): stop without a trailing `done`
        except Exception as exc:
            logger.error(f"live session worker failed: {type(exc).__name__}: {exc}")
            await self._emit({"type": "error", "message": f"{type(exc).__name__}: {exc}"})
            await self._emit({"type": "done"})
        else:
            await self._emit({"type": "done"})


class SessionRegistry:
    """Process-global store of live sessions, keyed by channel. Idle sessions
    (not running and with no socket attached) are reaped after ``grace`` seconds."""

    def __init__(self, *, grace=300.0, clock=time.monotonic):
        self._sessions = {}
        self._idle_since = {}
        self._grace = grace
        self._clock = clock

    def create(self, state):
        self._sweep()
        channel = uuid.uuid4().hex
        session = LiveSession(channel, state)
        self._sessions[channel] = session
        return session

    def get(self, channel):
        return self._sessions.get(channel)

    def _sweep(self):
        now = self._clock()
        for channel, session in list(self._sessions.items()):
            if session.running or session.attached:
                self._idle_since.pop(channel, None)
                continue
            since = self._idle_since.setdefault(channel, now)
            if now - since >= self._grace:
                del self._sessions[channel]
                self._idle_since.pop(channel, None)


registry = SessionRegistry()
