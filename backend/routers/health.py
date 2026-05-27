"""Health endpoint — liveness plus SDK status."""

from fastapi import APIRouter

from core.responses import api_response
from core.sdk import sdk_status

router = APIRouter(tags=["Health"])


@router.get("/health")
def health():
    return api_response(data={"sdk": sdk_status()})
