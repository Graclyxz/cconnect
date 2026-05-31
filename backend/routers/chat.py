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
from services import settings_store
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


def _compact_visibility(data: dict) -> dict:
    """Drop the summary when compact visibility is 'label' (block shows stats only)."""
    if settings_store.visibility_mode("compact") == "label":
        return {**data, "summary": ""}
    return data


class _Session:
    def __init__(self):
        self.cwd: str = DEFAULT_CWD
        self.permission_mode: str = "default"
        self.session_id: str | None = None
        self.fork: bool = False
        self.base_url: str | None = None


async def _stream_prompt(ws: WebSocket, send_lock: asyncio.Lock, state: _Session, broker: _InteractionBroker, text: str):
    async def send(payload: dict):
        async with send_lock:
            await ws.send_json(payload)

    name = text.strip()[:80] if state.session_id is None else None
    compacted = False
    is_compact_cmd = text.strip() == "/compact" or text.strip().startswith("/compact ")
    boundaries_before = (
        sessions_service.compact_boundary_count(state.cwd, state.session_id)
        if is_compact_cmd and state.cwd and state.session_id else 0
    )
    async for event in run_prompt(
        prompt=text,
        cwd=state.cwd,
        permission_mode=state.permission_mode,
        resume=state.session_id,
        fork=state.fork,
        model=_resolve_model(settings_store.get("model")),
        effort=settings_store.get("effort"),
        partial=settings_store.get("streaming"),
        name=name,
        ask_user=lambda payload: broker.ask(send, payload),
        base_url=state.base_url,
    ):
        if event.get("type") == "compact":
            compacted = True
        if event.get("type") == "result" and event.get("session_id"):
            state.session_id = event["session_id"]
            state.fork = False
            if state.cwd:
                sessions_service.record_prompt_history(state.cwd, state.session_id, text)
        await send(event)
    if state.cwd and state.session_id:
        sessions_service.normalize_session_entrypoint(state.cwd, state.session_id)
        # The token counts and summary are written to the transcript only after the turn.
        if is_compact_cmd:
            after = sessions_service.compact_boundary_count(state.cwd, state.session_id)
            if after > boundaries_before:
                data = sessions_service.latest_compact(state.cwd, state.session_id)
                if data:
                    await send({"type": "compact", **_compact_visibility(data)})
        elif compacted:
            data = sessions_service.latest_compact(state.cwd, state.session_id)
            if data:
                await send({"type": "compact_summary", **_compact_visibility(data)})
    await send({"type": "done"})


async def _run_side_question(ws: WebSocket, send_lock: asyncio.Lock, state: _Session, question: str):
    """Side question answered by an isolated lightweight session — runs concurrently and
    streams ask_text/ask_done without touching the main turn."""
    async def send(payload: dict):
        async with send_lock:
            await ws.send_json(payload)

    from services.claude_runtime import ask_side_question
    context = sessions_service.session_context(state.cwd, state.session_id or "")
    try:
        async for ev in ask_side_question(question, context, partial=settings_store.get("streaming")):
            await send(ev)
        await send({"type": "ask_done"})
    except Exception as exc:
        logger.debug(f"side question ended: {type(exc).__name__}: {exc}")


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
                state.cwd = msg.cwd or DEFAULT_CWD
                state.permission_mode = settings_store.get("permission_mode")
                state.session_id = msg.resume
                state.fork = msg.fork
                state.base_url = msg.base_url
                await send({
                    "type": "ready",
                    "session_id": state.session_id,
                    "project": sessions_service.project_key_for(state.cwd or ""),
                })
                # Restore the task indicator on resume.
                if state.session_id:
                    for t in sessions_service.session_tasks(state.session_id):
                        await send({"type": "task", **t})

            elif mtype == "prompt":
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

            elif mtype == "ask":
                # Side question: a concurrent, independent task — never touches the main turn.
                question = (raw.get("text") or "").strip()
                if question:
                    asyncio.create_task(_run_side_question(ws, send_lock, state, question))

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
