"""Share files from the backend's shared/ folder to the mobile app (download-only)."""

from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse

from core.responses import api_response
from services import shared as shared_service

router = APIRouter(tags=["Shared"])


@router.get("/shared")
def list_shared():
    return api_response(data=shared_service.list_files())


@router.get("/shared/{name}")
def download_shared(name: str):
    try:
        path = shared_service.resolve_file(name)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    if path is None:
        raise HTTPException(status_code=404, detail="file not found")
    return FileResponse(path, filename=path.name)
