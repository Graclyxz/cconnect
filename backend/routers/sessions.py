"""Browse Claude Code projects and session transcripts."""

from fastapi import APIRouter, HTTPException

from core.responses import api_response
from services import sessions as sessions_service

router = APIRouter(tags=["Sessions"])


@router.get("/projects")
def get_projects():
    return api_response(data=sessions_service.list_projects())


@router.get("/sessions")
def get_sessions(project: str):
    try:
        return api_response(data=sessions_service.list_sessions(project))
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))


@router.get("/sessions/{session_id}/messages")
def get_session_messages(session_id: str, project: str):
    try:
        return api_response(data=sessions_service.get_session_messages(project, session_id))
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
