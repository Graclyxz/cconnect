"""Health endpoint — liveness plus SDK status and the version contract.
"""

from fastapi import APIRouter

from core.config import SERVER_VERSION, SUPPORTED_APP
from core.responses import api_response
from core.sdk import sdk_status

router = APIRouter(tags=["Health"])


@router.get("/health")
def health():
    return api_response(data={
        "sdk": sdk_status(),
        "version": SERVER_VERSION,
        "supported_app": SUPPORTED_APP,
    })
