"""Admin panel: ``router`` is the token-gated JSON API under ``/api/admin``,
``ws_router`` the live-state WebSocket, and ``page_router`` the static panel at
``/admin`` (all mounted in ``main.py``)."""

import asyncio
from pathlib import Path
from typing import Any

from fastapi import APIRouter, Depends, Header, HTTPException, WebSocket, WebSocketDisconnect

from fastapi.responses import FileResponse

from core.responses import api_response
from services import admin as admin_service


def require_admin(authorization: str | None = Header(default=None)):
    scheme, _, value = (authorization or "").partition(" ")
    provided = value.strip() if scheme.lower() == "bearer" else ""
    if not admin_service.check_token(provided):
        raise HTTPException(status_code=401, detail="admin authentication required")


router = APIRouter(tags=["Admin"], dependencies=[Depends(require_admin)])
ws_router = APIRouter()
page_router = APIRouter(include_in_schema=False)

_ASSETS = Path(__file__).resolve().parent / "admin_assets"


@router.get("/admin/state")
def admin_state():
    return api_response(data=admin_service.snapshot())


@router.post("/admin/config")
async def admin_config(body: dict[str, Any]):
    try:
        data = admin_service.update_config(body)
    except (ValueError, TypeError) as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    return api_response(data=data)


@router.post("/admin/connections/{conn_id}/close")
async def admin_close_connection(conn_id: str):
    if not await admin_service.close_connection(conn_id):
        raise HTTPException(status_code=404, detail="connection not found")
    return api_response()


@router.post("/admin/connections/close-all")
async def admin_close_all():
    return api_response(data={"closed": await admin_service.close_all()})


@router.post("/admin/sessions/{channel}/interrupt")
async def admin_interrupt(channel: str):
    if not await admin_service.interrupt_session(channel):
        raise HTTPException(status_code=404, detail="session not found")
    return api_response()


@router.post("/admin/sessions/{channel}/terminate")
async def admin_terminate(channel: str):
    if not await admin_service.terminate_session(channel):
        raise HTTPException(status_code=404, detail="session not found")
    return api_response()


@ws_router.websocket("/admin/ws")
async def admin_ws(ws: WebSocket):
    if not admin_service.check_token(ws.query_params.get("token", "")):
        await ws.close(code=1008)
        return
    await ws.accept()
    lock = asyncio.Lock()

    async def send(payload):
        async with lock:
            await ws.send_json(payload)

    admin_service.attach(send)
    try:
        await send(admin_service.snapshot())
        while True:
            await ws.receive_text()
    except WebSocketDisconnect:
        pass
    finally:
        admin_service.detach(send)


@page_router.get("/admin")
def admin_page():
    return FileResponse(_ASSETS / "index.html", media_type="text/html")


@page_router.get("/admin/admin.css")
def admin_css():
    return FileResponse(_ASSETS / "admin.css", media_type="text/css")


@page_router.get("/admin/admin.js")
def admin_js():
    return FileResponse(_ASSETS / "admin.js", media_type="text/javascript")
