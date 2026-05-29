"""Expose runtime capabilities (models, permission modes, effort levels) to the app."""

from fastapi import APIRouter

from core.config import (
    COLORS,
    DEFAULT_EFFORT,
    DEFAULT_MODEL,
    DEFAULT_PERMISSION_MODE,
    MODELS,
    PERMISSION_LABELS,
    ULTRACODE_EFFORT,
    effort_levels,
    permission_modes,
)
from core.responses import api_response

router = APIRouter(tags=["Capabilities"])


@router.get("/capabilities")
def get_capabilities():
    return api_response(data={
        "permission_modes": [{"id": m, "label": PERMISSION_LABELS.get(m, m)} for m in permission_modes()],
        "effort_levels": ["default"] + list(effort_levels()) + [ULTRACODE_EFFORT],
        "models": MODELS,
        "colors": COLORS,
        "defaults": {
            "permission_mode": DEFAULT_PERMISSION_MODE,
            "effort": DEFAULT_EFFORT,
            "model": DEFAULT_MODEL,
            "partial": False,
        },
    })
