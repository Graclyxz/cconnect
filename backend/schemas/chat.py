"""Inbound WebSocket message models for the chat channel."""

from typing import Literal, Optional

from pydantic import BaseModel


class StartMessage(BaseModel):
    type: Literal["start"]
    cwd: str
    permission_mode: str = "default"
    resume: Optional[str] = None
    fork: bool = False
    model: Optional[str] = None
    effort: str = "max"
    partial: bool = False
    base_url: Optional[str] = None
    channel: Optional[str] = None
    last_seq: int = 0


class PromptMessage(BaseModel):
    type: Literal["prompt"]
    text: str
    attachments: list[str] = []
    id: Optional[str] = None


class SetPermissionMessage(BaseModel):
    type: Literal["set_permission_mode"]
    mode: str


class InterruptMessage(BaseModel):
    type: Literal["interrupt"]
