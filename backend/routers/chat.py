"""Interactive chat over WebSocket.

The connection is a detachable transport over a connection-independent
``LiveSession`` (``services/live_sessions``): the running turn lives in the
session's worker, survives the socket dropping, and a reconnecting socket
re-attaches by ``channel`` — getting the current ``running`` state and any
still-pending permission prompt re-emitted.
"""

import asyncio
import hmac
import json

from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from loguru import logger
from pydantic import ValidationError

from core.config import DEFAULT_CWD, PUBLIC_ACCESS_TOKEN, permission_modes
from schemas.chat import PromptMessage, SetPermissionMessage, StartMessage
from services import sessions as sessions_service
from services import settings_store
from services.claude_runtime import run_prompt
from services.live_sessions import registry

router = APIRouter(tags=["Chat"])


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


def _build_turn_runner(state: _Session, text: str):
    """Build the async-gen factory the LiveSession runs for one prompt. It wraps
    the run_prompt loop plus the post-turn session bookkeeping; the LiveSession
    appends the trailing ``done`` event itself."""

    def factory(ask_user):
        async def gen():
            name = text.strip()[:80] if state.session_id is None else None
            compacted = False
            is_compact_cmd = text.strip() == "/compact" or text.strip().startswith("/compact ")
            is_local_cmd = text.strip().startswith("/") and not is_compact_cmd
            boundaries_before = (
                sessions_service.compact_boundary_count(state.cwd, state.session_id)
                if is_compact_cmd and state.cwd and state.session_id else 0
            )
            local_cmds_before = (
                sessions_service.local_command_count(state.cwd, state.session_id)
                if is_local_cmd and state.cwd and state.session_id else 0
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
                ask_user=ask_user,
                base_url=state.base_url,
            ):
                if event.get("type") == "compact":
                    compacted = True
                if event.get("type") == "result" and event.get("session_id"):
                    state.session_id = event["session_id"]
                    state.fork = False
                    if state.cwd:
                        sessions_service.record_prompt_history(state.cwd, state.session_id, text)
                yield event
            if state.cwd and state.session_id:
                sessions_service.normalize_session_entrypoint(state.cwd, state.session_id)
                # The token counts and summary are written to the transcript only after the turn.
                if is_compact_cmd:
                    after = sessions_service.compact_boundary_count(state.cwd, state.session_id)
                    if after > boundaries_before:
                        data = sessions_service.latest_compact(state.cwd, state.session_id)
                        if data:
                            yield {"type": "compact", **_compact_visibility(data)}
                elif compacted:
                    data = sessions_service.latest_compact(state.cwd, state.session_id)
                    if data:
                        yield {"type": "compact_summary", **_compact_visibility(data)}
                elif is_local_cmd and sessions_service.local_command_count(state.cwd, state.session_id) > local_cmds_before:
                    md = sessions_service.latest_local_command(state.cwd, state.session_id)
                    if md:
                        yield {"type": "command", "markdown": md}

        return gen()

    return factory


async def _run_side_question(send, state: _Session, question: str, resume_id: str | None):
    """Side question answered by an isolated lightweight session — runs concurrently and
    streams ask_text/ask_done without touching the main turn."""
    from services.claude_runtime import ask_side_question

    context = sessions_service.session_context(state.cwd, state.session_id or "")
    try:
        async for ev in ask_side_question(question, context, resume_id, partial=settings_store.get("streaming")):
            await send(ev)
        await send({"type": "ask_done"})
    except Exception as exc:
        logger.debug(f"side question ended: {type(exc).__name__}: {exc}")


async def _run_usage(send):
    """Send the plan-usage report as a one-off markdown message."""
    from services.usage import usage_markdown

    try:
        md = await usage_markdown()
        await send({"type": "command", "markdown": md})
    except Exception as exc:
        logger.debug(f"usage report ended: {type(exc).__name__}: {exc}")


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
    send_lock = asyncio.Lock()
    session = None
    bg_tasks: set = set()  # keep refs so fire-and-forget tasks aren't GC'd mid-run

    async def send(payload: dict):
        async with send_lock:
            await ws.send_json(payload)

    def spawn(coro):
        task = asyncio.create_task(coro)
        bg_tasks.add(task)
        task.add_done_callback(bg_tasks.discard)

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
                # Re-attach to a still-live session when the client supplies a known
                # channel; otherwise start a fresh one.
                existing = registry.get(msg.channel) if msg.channel else None
                reattaching = existing is not None
                if reattaching:
                    session = existing
                    if msg.base_url:
                        session.state.base_url = msg.base_url
                else:
                    state = _Session()
                    state.cwd = msg.cwd or DEFAULT_CWD
                    state.permission_mode = settings_store.get("permission_mode")
                    state.session_id = msg.resume
                    state.fork = msg.fork
                    state.base_url = msg.base_url
                    session = registry.create(state)
                await send({
                    "type": "ready",
                    "session_id": session.state.session_id,
                    "project": sessions_service.project_key_for(session.state.cwd or ""),
                    "channel": session.channel,
                    "running": session.running,
                })
                await session.attach(send, last_seq=msg.last_seq)
                # On a fresh (re)open, restore the task indicators from disk. On a
                # live re-attach the replayed stream already carries them.
                if not reattaching and session.state.session_id:
                    for t in sessions_service.session_tasks(session.state.session_id):
                        await send({"type": "task", **t})

            elif mtype == "prompt":
                if session is None:
                    await send({"type": "error", "message": "send 'start' first"})
                    continue
                try:
                    msg = PromptMessage(**raw)
                except ValidationError as exc:
                    await send({"type": "error", "message": exc.errors()})
                    continue
                if not session.start(_build_turn_runner(session.state, msg.text)):
                    await send({"type": "error", "message": "busy: a prompt is already running"})

            elif mtype == "set_permission_mode":
                try:
                    msg = SetPermissionMessage(**raw)
                except ValidationError as exc:
                    await send({"type": "error", "message": exc.errors()})
                    continue
                if msg.mode not in permission_modes():
                    await send({"type": "error", "message": f"invalid permission_mode: {msg.mode}"})
                    continue
                if session is not None:
                    session.state.permission_mode = msg.mode
                await send({"type": "permission_mode", "mode": msg.mode})

            elif mtype == "ask":
                question = (raw.get("text") or "").strip()
                if question and session is not None:
                    resume_id = raw.get("resume") if isinstance(raw.get("resume"), str) else None
                    spawn(_run_side_question(send, session.state, question, resume_id))

            elif mtype == "usage":
                spawn(_run_usage(send))

            elif mtype == "interrupt":
                if session is not None:
                    await session.interrupt()

            elif mtype == "interaction_response":
                rid = raw.get("id")
                if isinstance(rid, str) and session is not None:
                    session.resolve(rid, {
                        "option_id": raw.get("option_id"),
                        "free_text": raw.get("free_text"),
                    })

            elif mtype == "load_history":
                project = raw.get("project") or (session.state.cwd if session else DEFAULT_CWD)
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
        # Detach only — the worker keeps running so a reconnect can re-attach.
        if session is not None:
            await session.detach(send)
