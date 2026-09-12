"""One sampler of machine metrics shared by every connection, replayed from a ring buffer on subscribe."""

import asyncio
import threading
import time
from typing import Optional

from services import system_monitor

_SNAPSHOT_INTERVAL = 2.0
_LOG_TAIL_INTERVAL = 0.5
_HISTORY_CAP = 90
_LOG_CAP = 300


class SystemWatchHub:
    def __init__(self):
        self._subscribers: set[asyncio.Queue] = set()
        self._loop: Optional[asyncio.AbstractEventLoop] = None
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._started = False
        self._history: list[dict] = []
        self._logs: list[dict] = []
        self._offset = 0

    async def start(self):
        self._loop = asyncio.get_running_loop()

    def stop(self):
        self._stop.set()

    def subscribe(self) -> asyncio.Queue:
        queue: asyncio.Queue = asyncio.Queue()
        with self._lock:
            self._subscribers.add(queue)
            first = len(self._subscribers) == 1
            replay = {"type": "history", "items": list(self._history), "logs": list(self._logs)}
        queue.put_nowait(replay)
        if first and not self._started:
            self._started = True
            self._stop.clear()
            threading.Thread(target=self._sample_loop, name="system-watch", daemon=True).start()
        return queue

    def unsubscribe(self, queue: asyncio.Queue):
        with self._lock:
            self._subscribers.discard(queue)
            idle = not self._subscribers
        if idle and self._started:
            self._started = False
            self.stop()

    def _sample_loop(self):
        next_snapshot = 0.0
        while not self._stop.is_set():
            now = time.monotonic()
            if now >= next_snapshot:
                next_snapshot = now + _SNAPSHOT_INTERVAL
                self._push_snapshot()
            self._push_logs()
            self._stop.wait(_LOG_TAIL_INTERVAL)

    def _push_snapshot(self):
        try:
            snapshot = system_monitor.snapshot()
        except Exception:
            return
        event = {"type": "system", **snapshot}
        with self._lock:
            self._history.append(event)
            del self._history[:-_HISTORY_CAP]
        self._broadcast(event)

    def _push_logs(self):
        try:
            chunk = system_monitor.logs(self._offset)
        except Exception:
            return
        self._offset = chunk["offset"]
        items = chunk["items"]
        if not items:
            return
        with self._lock:
            self._logs.extend(items)
            del self._logs[:-_LOG_CAP]
        self._broadcast({"type": "logs", "items": items})

    def _broadcast(self, event: dict):
        loop = self._loop
        if loop is None:
            return
        with self._lock:
            targets = list(self._subscribers)
        for queue in targets:
            loop.call_soon_threadsafe(queue.put_nowait, event)


hub = SystemWatchHub()
