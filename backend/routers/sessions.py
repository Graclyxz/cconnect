"""Browse Claude Code projects and session transcripts."""

from typing import Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from core.config import COLORS
from core.responses import api_response
from services import sessions as sessions_service

router = APIRouter(tags=["Sessions"])


class RenameBody(BaseModel):
    project: str
    title: str


class ProjectBody(BaseModel):
    project: str


class ColorBody(BaseModel):
    project: str
    color: str


@router.delete("/sessions/{session_id}")
def delete_session(session_id: str, project: str):
    try:
        deleted = sessions_service.delete_session(project, session_id)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    if not deleted:
        raise HTTPException(status_code=404, detail="session not found")
    return api_response(message="deleted")


@router.post("/sessions/{session_id}/rename")
def rename_session(session_id: str, body: RenameBody):
    try:
        renamed = sessions_service.rename_session(body.project, session_id, body.title)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    if not renamed:
        raise HTTPException(status_code=404, detail="session not found")
    return api_response(message="renamed")


@router.post("/sessions/{session_id}/auto-rename")
async def auto_rename_session(session_id: str, body: ProjectBody):
    try:
        title = await sessions_service.auto_generate_title(body.project, session_id)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    if not title:
        raise HTTPException(status_code=404, detail="session not found or empty")
    return api_response(data={"title": title})


@router.post("/sessions/{session_id}/color")
def set_session_color(session_id: str, body: ColorBody):
    if body.color and body.color not in COLORS:
        raise HTTPException(status_code=400, detail="invalid color")
    try:
        updated = sessions_service.set_session_color(body.project, session_id, body.color)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    if not updated:
        raise HTTPException(status_code=404, detail="session not found")
    return api_response(message="updated")


@router.get("/projects")
def get_projects():
    return api_response(data=sessions_service.list_projects())


@router.get("/sessions")
def get_sessions(project: Optional[str] = None):
    try:
        items = (
            sessions_service.list_sessions(project)
            if project
            else sessions_service.list_all_sessions()
        )
        return api_response(data=items)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))


@router.get("/sessions/{session_id}/messages")
def get_session_messages(session_id: str, project: str):
    try:
        return api_response(data=sessions_service.get_session_messages(project, session_id))
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
