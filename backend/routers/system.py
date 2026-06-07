"""PC resource usage and server logs."""

from fastapi import APIRouter, Query

from core.responses import api_response
from services import system_monitor

router = APIRouter(tags=["system"])


@router.get("/system")
def get_system():
    return api_response(data=system_monitor.snapshot())


@router.get("/system/logs")
def get_system_logs(after: int = Query(0, ge=0), limit: int = Query(200, ge=1, le=500)):
    return api_response(data=system_monitor.logs(after, limit))
