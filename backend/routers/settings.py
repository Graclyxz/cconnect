"""Backend-owned settings (model, effort, permission, streaming, CLI source), shared by
every client that connects to this backend. The app reads/writes these instead of keeping
them locally; only app-local prefs (theme, language, accent) stay on the device."""

from typing import Any

from fastapi import APIRouter, HTTPException

from core.responses import api_response
from services import settings_store

router = APIRouter(tags=["Settings"])


@router.get("/settings")
def get_settings():
    return api_response(data=settings_store.describe())


@router.post("/settings")
def update_settings(body: dict[str, Any]):
    try:
        for key, value in body.items():
            settings_store.set(key, value)
    except (KeyError, ValueError) as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    return api_response(data=settings_store.describe())


@router.post("/settings/reset")
def reset_settings():
    settings_store.reset()
    return api_response(data=settings_store.describe())
