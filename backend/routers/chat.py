"""Interactive chat over WebSocket — drives Claude Code and streams events back."""

import asyncio
import hmac
import json
import uuid

from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from loguru import logger
from pydantic import ValidationError

from core.config import DEFAULT_CWD, PUBLIC_ACCESS_TOKEN, permission_modes
from schemas.chat import PromptMessage, SetPermissionMessage, StartMessage
from services import sessions as sessions_service
from services.claude_runtime import run_prompt

router = APIRouter(tags=["Chat"])


class _InteractionBroker:
    """Pairs interaction_request events with the user's interaction_response by id."""

    def __init__(self):
        self._pending: dict[str, asyncio.Future] = {}

    async def ask(self, send, payload: dict) -> dict:
        rid = uuid.uuid4().hex
        loop = asyncio.get_running_loop()
        future: asyncio.Future = loop.create_future()
        self._pending[rid] = future
        try:
            await send({"type": "interaction_request", "id": rid, **payload})
            return await future
        finally:
            self._pending.pop(rid, None)

    def resolve(self, rid: str, response: dict) -> bool:
        future = self._pending.get(rid)
        if future is None or future.done():
            return False
        future.set_result(response)
        return True

    def cancel_all(self):
        for future in self._pending.values():
            if not future.done():
                future.cancel()
        self._pending.clear()


def _resolve_model(model: str | None) -> str | None:
    """Treat empty / "default" as "let the CLI pick"."""
    return None if model in (None, "", "default") else model


class _Session:
    def __init__(self):
        self.cwd: str | None = None
        self.permission_mode: str = "default"
        self.session_id: str | None = None
        self.fork: bool = False
        self.model: str | None = None
        self.effort: str = "max"
        self.partial: bool = False
        self.base_url: str | None = None


async def _stream_prompt(ws: WebSocket, send_lock: asyncio.Lock, state: _Session, broker: _InteractionBroker, text: str):
    async def send(payload: dict):
        async with send_lock:
            await ws.send_json(payload)

    name = text.strip()[:80] if state.session_id is None else None
    async for event in run_prompt(
        prompt=text,
        cwd=state.cwd,
        permission_mode=state.permission_mode,
        resume=state.session_id,
        fork=state.fork,
        model=_resolve_model(state.model),
        effort=state.effort,
        partial=state.partial,
        name=name,
        ask_user=lambda payload: broker.ask(send, payload),
        base_url=state.base_url,
    ):
        if event.get("type") == "result" and event.get("session_id"):
            state.session_id = event["session_id"]
            state.fork = False
            if state.cwd:
                sessions_service.record_prompt_history(state.cwd, state.session_id, text)
        await send(event)
    if state.cwd and state.session_id:
        sessions_service.normalize_session_entrypoint(state.cwd, state.session_id)
    await send({"type": "done"})


def _ws_bearer_ok(ws: WebSocket) -> bool:
    """Validate the WS handshake's Authorization header. No-op when no token is set."""
    if PUBLIC_ACCESS_TOKEN is None:
        return True
    scheme, _, value = ws.headers.get("authorization", "").partition(" ")
    return scheme.lower() == "bearer" and hmac.compare_digest(value.strip(), PUBLIC_ACCESS_TOKEN)


@router.websocket("/chat/ws")
async def chat_ws(ws: WebSocket):
    if not _ws_bearer_ok(ws):
        # Close before accept() so the handshake is rejected at the HTTP layer.
        await ws.close(code=1008)
        return
    await ws.accept()
    state = _Session()
    send_lock = asyncio.Lock()
    broker = _InteractionBroker()
    task: asyncio.Task | None = None

    async def send(payload: dict):
        async with send_lock:
            await ws.send_json(payload)

    try:
        while True:
            try:
                raw = await ws.receive_json()
            except json.JSONDecodeError:
                await send({"type": "error", "message": "invalid JSON"})
                continue
            mtype = raw.get("type")

            if mtype == "start":
                try:
                    msg = StartMessage(**raw)
                except ValidationError as exc:
                    await send({"type": "error", "message": exc.errors()})
                    continue
                if msg.permission_mode not in permission_modes():
                    await send({"type": "error", "message": f"invalid permission_mode: {msg.permission_mode}"})
                    continue
                state.cwd = msg.cwd or DEFAULT_CWD
                state.permission_mode = msg.permission_mode
                state.session_id = msg.resume
                state.fork = msg.fork
                state.model = msg.model
                state.effort = msg.effort
                state.partial = msg.partial
                state.base_url = msg.base_url
                await send({"type": "ready", "session_id": state.session_id})

            elif mtype == "prompt":
                if state.cwd is None:
                    await send({"type": "error", "message": "send a 'start' message first"})
                    continue
                if task and not task.done():
                    await send({"type": "error", "message": "busy: a prompt is already running"})
                    continue
                try:
                    msg = PromptMessage(**raw)
                except ValidationError as exc:
                    await send({"type": "error", "message": exc.errors()})
                    continue
                task = asyncio.create_task(_stream_prompt(ws, send_lock, state, broker, msg.text))

            elif mtype == "set_permission_mode":
                try:
                    msg = SetPermissionMessage(**raw)
                except ValidationError as exc:
                    await send({"type": "error", "message": exc.errors()})
                    continue
                if msg.mode not in permission_modes():
                    await send({"type": "error", "message": f"invalid permission_mode: {msg.mode}"})
                    continue
                state.permission_mode = msg.mode
                await send({"type": "permission_mode", "mode": msg.mode})

            elif mtype == "interrupt":
                if task and not task.done():
                    task.cancel()
                    await send({"type": "interrupted"})

            elif mtype == "interaction_response":
                rid = raw.get("id")
                if isinstance(rid, str):
                    broker.resolve(rid, {
                        "option_id": raw.get("option_id"),
                        "free_text": raw.get("free_text"),
                    })

            elif mtype == "load_history":
                project = raw.get("project") or state.cwd
                sid = raw.get("session_id")
                limit = raw.get("limit") or 200
                before_index = raw.get("before_index")
                if not isinstance(sid, str) or not isinstance(limit, int):
                    await send({"type": "error", "message": "invalid load_history payload"})
                    continue
                if limit < 1 or limit > 500:
                    await send({"type": "error", "message": "invalid limit"})
                    continue
                try:
                    items = sessions_service.get_session_messages(project, sid)
                except ValueError as exc:
                    await send({"type": "error", "message": str(exc)})
                    continue
                total = len(items)
                end = total if before_index is None else max(0, min(before_index, total))
                start = max(0, end - limit)
                slice_ = [dict(item, index=i) for i, item in enumerate(items[start:end], start=start)]
                await send({
                    "type": "history_chunk",
                    "session_id": sid,
                    "items": slice_,
                    "start_index": start,
                    "has_more": start > 0,
                })

            else:
                await send({"type": "error", "message": f"unknown message type: {mtype}"})

    except WebSocketDisconnect:
        pass
    except Exception as exc:
        logger.error(f"chat_ws error: {type(exc).__name__}: {exc}")
    finally:
        broker.cancel_all()
        if task and not task.done():
            task.cancel()
