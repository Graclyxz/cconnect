"""Browse, download and delete files in the backend's shared/ folder from the mobile app."""

from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse

from core.responses import api_response
from services import shared as shared_service

router = APIRouter(tags=["Shared"])


@router.get("/shared")
def list_shared(path: str = ""):
    try:
        return api_response(data=shared_service.list_entries(path))
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))


@router.get("/shared/{path:path}")
def download_shared(path: str):
    try:
        resolved = shared_service.resolve_file(path)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    if resolved is None:
        raise HTTPException(status_code=404, detail="file not found")
    return FileResponse(resolved, filename=resolved.name)


@router.delete("/shared/{path:path}")
def delete_shared(path: str):
    try:
        deleted = shared_service.delete_entry(path)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    if not deleted:
        raise HTTPException(status_code=404, detail="not found")
    return api_response()
