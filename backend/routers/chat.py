"""Interactive chat over WebSocket — drives Claude Code and streams events back."""

import asyncio

from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from loguru import logger
from pydantic import ValidationError

from core.config import permission_modes
from schemas.chat import PromptMessage, SetPermissionMessage, StartMessage
from services import sessions as sessions_service
from services.claude_runtime import run_prompt

router = APIRouter(tags=["Chat"])


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


async def _stream_prompt(ws: WebSocket, send_lock: asyncio.Lock, state: _Session, text: str):
    is_new_session = state.session_id is None
    async for event in run_prompt(
        prompt=text,
        cwd=state.cwd,
        permission_mode=state.permission_mode,
        resume=state.session_id,
        fork=state.fork,
        model=_resolve_model(state.model),
        effort=state.effort,
        partial=state.partial,
    ):
        if event.get("type") == "result" and event.get("session_id"):
            new_id = event["session_id"]
            if is_new_session and state.cwd:
                sessions_service.write_session_title(state.cwd, new_id, text)
                is_new_session = False
            state.session_id = new_id
            state.fork = False
        async with send_lock:
            await ws.send_json(event)
    async with send_lock:
        await ws.send_json({"type": "done"})


@router.websocket("/chat/ws")
async def chat_ws(ws: WebSocket):
    await ws.accept()
    state = _Session()
    send_lock = asyncio.Lock()
    task: asyncio.Task | None = None

    async def send(payload: dict):
        async with send_lock:
            await ws.send_json(payload)

    try:
        while True:
            raw = await ws.receive_json()
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
                state.cwd = msg.cwd
                state.permission_mode = msg.permission_mode
                state.session_id = msg.resume
                state.fork = msg.fork
                state.model = msg.model
                state.effort = msg.effort
                state.partial = msg.partial
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
                task = asyncio.create_task(_stream_prompt(ws, send_lock, state, msg.text))

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

            else:
                await send({"type": "error", "message": f"unknown message type: {mtype}"})

    except WebSocketDisconnect:
        pass
    except Exception as exc:
        logger.error(f"chat_ws error: {type(exc).__name__}: {exc}")
    finally:
        if task and not task.done():
            task.cancel()
