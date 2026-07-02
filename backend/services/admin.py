"""Admin panel state: live WebSocket connections plus a few runtime limits.

Connections are in-memory; config and admin token persist to a gitignored JSON
file. Limits are enforced in ``routers/chat.py``.
"""

import asyncio
import collections
import hmac
import json
import os
import secrets
import time
import uuid
from dataclasses import asdict, dataclass, field
from pathlib import Path

from loguru import logger

from services import live_sessions
from services.live_sessions import registry

_CONFIG_FILE = Path(__file__).resolve().parent.parent / "admin_config.json"
_RATE_WINDOW = 60.0

_ADMIN_ENV = (os.environ.get("CCONNECT_ADMIN_TOKEN") or "").strip() or None
_token: str | None = None


@dataclass
class AdminConfig:
    accept_connections: bool = True
    max_connections: int = 0
    rate_limit_per_min: int = 0
    max_queue: int = 0


_config = AdminConfig()


@dataclass
class Conn:
    id: str
    ip: str
    since: float
    ws: object = field(repr=False)
    channel: str | None = None
    session_id: str | None = None
    _prompts: collections.deque = field(default_factory=collections.deque, repr=False)


_conns: dict[str, Conn] = {}

_sinks: set = set()
_broadcast_scheduled = False


def load() -> None:
    global _config, _token
    live_sessions.state_changed = notify
    _config = AdminConfig()
    _token = None
    try:
        raw = json.loads(_CONFIG_FILE.read_text(encoding="utf-8"))
        cfg = raw.get("config", {})
        _config = AdminConfig(**{k: cfg[k] for k in cfg if k in AdminConfig.__dataclass_fields__})
        _token = raw.get("token") or None
    except (OSError, ValueError, TypeError, AttributeError):
        pass
    if _ADMIN_ENV is None and _token is None:
        _token = secrets.token_urlsafe(24)
        _save()
    where = "CCONNECT_ADMIN_TOKEN env" if _ADMIN_ENV else str(_CONFIG_FILE)
    logger.info(f"admin console token ready (source: {where})")


def _effective_token() -> str | None:
    return _ADMIN_ENV or _token


def current_token() -> str | None:
    return _effective_token()


def check_token(provided: str) -> bool:
    token = _effective_token()
    if not token:
        return False
    return hmac.compare_digest(provided or "", token)


def _save() -> None:
    try:
        _CONFIG_FILE.write_text(
            json.dumps({"config": asdict(_config), "token": _token}, indent=2),
            encoding="utf-8",
        )
    except OSError as exc:
        logger.warning(f"admin config save failed: {exc}")


def get_config() -> dict:
    return asdict(_config)


def update_config(patch: dict) -> dict:
    if "accept_connections" in patch:
        _config.accept_connections = bool(patch["accept_connections"])
    for key in ("max_connections", "rate_limit_per_min", "max_queue"):
        if key in patch and patch[key] is not None:
            setattr(_config, key, max(0, int(patch[key])))
    _save()
    notify()
    return get_config()


def can_accept() -> bool:
    if not _config.accept_connections:
        return False
    if _config.max_connections and len(_conns) >= _config.max_connections:
        return False
    return True


def register(ws, ip: str) -> Conn:
    conn = Conn(id=uuid.uuid4().hex[:12], ip=ip or "?", since=time.time(), ws=ws)
    _conns[conn.id] = conn
    notify()
    return conn


def unregister(conn_id: str) -> None:
    if _conns.pop(conn_id, None) is not None:
        notify()


def note_start(conn: Conn | None, channel: str | None, session_id: str | None) -> None:
    if conn is not None:
        conn.channel = channel
        conn.session_id = session_id
        notify()


def allow_prompt(conn: Conn | None) -> bool:
    if conn is None or not _config.rate_limit_per_min:
        return True
    now = time.monotonic()
    stamps = conn._prompts
    cutoff = now - _RATE_WINDOW
    while stamps and stamps[0] < cutoff:
        stamps.popleft()
    if len(stamps) >= _config.rate_limit_per_min:
        return False
    stamps.append(now)
    return True


def queue_full(session) -> bool:
    return bool(_config.max_queue) and session.queued_count >= _config.max_queue


async def close_connection(conn_id: str) -> bool:
    conn = _conns.get(conn_id)
    if conn is None:
        return False
    try:
        await conn.ws.close(code=1012)
    except Exception:
        pass
    return True


async def close_all() -> int:
    victims = list(_conns.values())
    for conn in victims:
        try:
            await conn.ws.close(code=1012)
        except Exception:
            pass
    return len(victims)


async def interrupt_session(channel: str) -> bool:
    session = registry.get(channel)
    if session is None:
        return False
    await session.interrupt()
    return True


async def terminate_session(channel: str) -> bool:
    session = registry.get(channel)
    if session is None:
        return False
    await session.interrupt()
    registry.discard(channel)
    return True


def snapshot() -> dict:
    sessions = registry.all()
    return {
        "config": get_config(),
        "stats": {
            "connections": len(_conns),
            "sessions": len(sessions),
            "running": sum(1 for s in sessions if s.running),
        },
        "connections": [
            {
                "id": c.id,
                "ip": c.ip,
                "since": c.since,
                "channel": c.channel,
                "session_id": c.session_id,
            }
            for c in _conns.values()
        ],
        "sessions": [
            {
                "channel": s.channel,
                "session_id": s.state.session_id,
                "cwd": s.state.cwd,
                "running": s.running,
                "sockets": s.socket_count,
                "queued": s.queued_count,
            }
            for s in sessions
        ],
    }


def attach(send) -> None:
    _sinks.add(send)


def detach(send) -> None:
    _sinks.discard(send)


async def _broadcast() -> None:
    global _broadcast_scheduled
    await asyncio.sleep(0.05)
    _broadcast_scheduled = False
    if not _sinks:
        return
    snap = snapshot()
    for send in list(_sinks):
        try:
            await send(snap)
        except Exception:
            _sinks.discard(send)


def notify() -> None:
    global _broadcast_scheduled
    if not _sinks or _broadcast_scheduled:
        return
    try:
        loop = asyncio.get_running_loop()
    except RuntimeError:
        return
    _broadcast_scheduled = True
    loop.create_task(_broadcast())
