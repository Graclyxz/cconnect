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


class PromptMessage(BaseModel):
    type: Literal["prompt"]
    text: str


class SetPermissionMessage(BaseModel):
    type: Literal["set_permission_mode"]
    mode: str


class InterruptMessage(BaseModel):
    type: Literal["interrupt"]
