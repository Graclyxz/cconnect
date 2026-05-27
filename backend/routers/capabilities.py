"""Expose runtime capabilities (models, permission modes, effort levels) to the app."""

from fastapi import APIRouter

from core.config import (
    DEFAULT_EFFORT,
    DEFAULT_MODEL,
    DEFAULT_PERMISSION_MODE,
    MODELS,
    effort_levels,
    permission_modes,
)
from core.responses import api_response

router = APIRouter(tags=["Capabilities"])


@router.get("/capabilities")
def get_capabilities():
    return api_response(data={
        "permission_modes": list(permission_modes()),
        "effort_levels": ["default"] + list(effort_levels()),
        "models": MODELS,
        "defaults": {
            "permission_mode": DEFAULT_PERMISSION_MODE,
            "effort": DEFAULT_EFFORT,
            "model": DEFAULT_MODEL,
            "partial": False,
        },
    })
