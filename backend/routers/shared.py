"""Browse, download, upload and manage files in the backend's shared/ folder from the mobile app."""

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import FileResponse
from pydantic import BaseModel

from core.responses import api_response
from services import shared as shared_service

router = APIRouter(tags=["Shared"])


class FolderBody(BaseModel):
    path: str


class RenameEntryBody(BaseModel):
    path: str
    name: str


class TransferBody(BaseModel):
    paths: list[str]
    dest: str


class PathsBody(BaseModel):
    paths: list[str]


@router.get("/shared")
def list_shared(path: str = ""):
    try:
        return api_response(data=shared_service.list_entries(path))
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))


@router.post("/shared/folder")
def create_shared_folder(body: FolderBody):
    try:
        shared_service.create_folder(body.path)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    return api_response()


@router.post("/shared/rename")
def rename_shared(body: RenameEntryBody):
    try:
        renamed = shared_service.rename_entry(body.path, body.name)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    if not renamed:
        raise HTTPException(status_code=404, detail="not found")
    return api_response()


@router.post("/shared/paths")
def shared_paths(body: PathsBody):
    try:
        return api_response(data=shared_service.absolute_paths(body.paths))
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))


@router.post("/shared/move")
def move_shared(body: TransferBody):
    try:
        count = shared_service.move_entries(body.paths, body.dest)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    return api_response(data={"count": count})


@router.post("/shared/copy")
def copy_shared(body: TransferBody):
    try:
        count = shared_service.copy_entries(body.paths, body.dest)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    return api_response(data={"count": count})


@router.put("/shared/{path:path}")
async def upload_shared(path: str, request: Request):
    try:
        saved = await shared_service.save_upload(path, request.stream())
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    return api_response(data={"path": saved})


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
