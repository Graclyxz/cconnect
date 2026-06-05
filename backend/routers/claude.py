"""Claude Code management: user prompt, plugins, skills and MCP overview."""

from fastapi import APIRouter
from pydantic import BaseModel

from core.responses import api_response
from services import claude_assets

router = APIRouter(tags=["Claude"])


class PromptBody(BaseModel):
    text: str


@router.get("/claude/prompt")
def get_user_prompt():
    return api_response(data={"text": claude_assets.get_user_prompt()})


@router.put("/claude/prompt")
def set_user_prompt(body: PromptBody):
    claude_assets.set_user_prompt(body.text)
    return api_response()


@router.get("/claude/plugins")
def get_plugins():
    return api_response(data={
        "plugins": claude_assets.list_plugins(),
        "marketplaces": claude_assets.list_marketplaces(),
    })


@router.get("/claude/skills")
def get_skills():
    return api_response(data=claude_assets.list_skills())


@router.get("/claude/mcp")
def get_mcp():
    return api_response(data=claude_assets.list_mcp_servers())
