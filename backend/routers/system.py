"""PC resource usage and server logs."""

import asyncio
import time

from fastapi import APIRouter, Query, WebSocket, WebSocketDisconnect

from core.responses import api_response
from middleware.public_auth import ws_bearer_ok
from services import system_monitor

router = APIRouter(tags=["system"])

_SNAPSHOT_INTERVAL = 2.0
_LOG_TAIL_INTERVAL = 0.5


@router.get("/system")
def get_system():
    return api_response(data=system_monitor.snapshot())


@router.get("/system/logs")
def get_system_logs(after: int = Query(0, ge=0), limit: int = Query(200, ge=1, le=500)):
    return api_response(data=system_monitor.logs(after, limit))


@router.websocket("/system/ws")
async def system_ws(ws: WebSocket):
    if not ws_bearer_ok(ws):
        await ws.close(code=1008)
        return
    await ws.accept()
    offset = 0
    next_snapshot = 0.0
    try:
        while True:
            now = time.monotonic()
            if now >= next_snapshot:
                next_snapshot = now + _SNAPSHOT_INTERVAL
                snapshot = await asyncio.to_thread(system_monitor.snapshot)
                await ws.send_json({"type": "system", **snapshot})
            chunk = await asyncio.to_thread(system_monitor.logs, offset)
            offset = chunk["offset"]
            if chunk["items"]:
                await ws.send_json({"type": "logs", "items": chunk["items"]})
            await asyncio.sleep(_LOG_TAIL_INTERVAL)
    except (WebSocketDisconnect, RuntimeError):
        pass
