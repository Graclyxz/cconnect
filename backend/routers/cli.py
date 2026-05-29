"""Select and update the Claude Code CLI the backend drives, from the phone."""

from typing import Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from core import cli_manager
from core.responses import api_response

router = APIRouter(tags=["CLI"])


class CliSourceBody(BaseModel):
    source: str
    custom_path: Optional[str] = None


@router.get("/cli")
def get_cli():
    return api_response(data=cli_manager.status())


@router.post("/cli")
def set_cli(body: CliSourceBody):
    try:
        cli_manager.set_source(body.source, body.custom_path)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    return api_response(data=cli_manager.status())


@router.post("/cli/update")
def update_cli():
    return api_response(data=cli_manager.update_cli())
