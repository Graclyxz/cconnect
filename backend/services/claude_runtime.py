"""Wraps the Claude Agent SDK query() into a stream of normalized event dicts."""

import json
from typing import Any, AsyncIterator, Optional

from loguru import logger


def _format_tool_input(inp: Any) -> str:
    """Human-readable tool input ("key: value" per line) instead of raw JSON."""
    if isinstance(inp, dict):
        lines = []
        for key, value in inp.items():
            text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False)
            lines.append(f"{key}: {text}")
        return "\n".join(lines)
    return "" if inp is None else str(inp)


def _stream_event_to_events(event: Any) -> list[dict]:
    """Map a raw Anthropic streaming event into incremental delta events."""
    if not isinstance(event, dict) or event.get("type") != "content_block_delta":
        return []
    delta = event.get("delta") or {}
    dtype = delta.get("type")
    if dtype == "text_delta":
        return [{"type": "assistant_text", "text": delta.get("text", "")}]
    if dtype == "thinking_delta":
        return [{"type": "thinking", "text": delta.get("thinking", "")}]
    return []


def _blocks_to_events(content: Any, skip_streamed: bool = False) -> list[dict]:
    """Convert final message blocks to events. When ``skip_streamed`` is set,
    text and thinking are omitted because they already arrived as deltas."""
    events: list[dict] = []
    for block in content or []:
        kind = type(block).__name__
        if kind == "TextBlock":
            if skip_streamed:
                continue
            events.append({"type": "assistant_text", "text": getattr(block, "text", "")})
        elif kind == "ThinkingBlock":
            if skip_streamed:
                continue
            events.append({"type": "thinking", "text": getattr(block, "thinking", "") or getattr(block, "text", "")})
        elif kind == "ToolUseBlock":
            events.append({
                "type": "tool_use",
                "id": getattr(block, "id", None),
                "name": getattr(block, "name", None),
                "input": _format_tool_input(getattr(block, "input", None)),
            })
        elif kind == "ToolResultBlock":
            events.append({
                "type": "tool_result",
                "tool_use_id": getattr(block, "tool_use_id", None),
                "content": getattr(block, "content", None),
                "is_error": getattr(block, "is_error", None),
            })
    return events


async def run_prompt(
    prompt: str,
    cwd: str,
    permission_mode: str = "default",
    resume: Optional[str] = None,
    fork: bool = False,
    model: Optional[str] = None,
    effort: str = "max",
    partial: bool = False,
    name: Optional[str] = None,
) -> AsyncIterator[dict]:
    """Yield normalized events for one prompt. The SDK import is deferred because the
    package is installed/upgraded during app startup."""
    from claude_agent_sdk import (
        query,
        ClaudeAgentOptions,
        AssistantMessage,
        SystemMessage,
        ResultMessage,
        StreamEvent,
    )

    effort_level = None if effort in (None, "", "default") else effort
    extra_args = {"name": name} if name else {}

    options = ClaudeAgentOptions(
        cwd=cwd,
        permission_mode=permission_mode,
        resume=resume,
        fork_session=fork,
        model=model,
        effort=effort_level,
        system_prompt={"type": "preset", "preset": "claude_code"},
        setting_sources=["user", "project"],
        thinking={"type": "adaptive", "display": "summarized"},
        include_partial_messages=partial,
        extra_args=extra_args,
    )

    try:
        async for message in query(prompt=prompt, options=options):
            if isinstance(message, StreamEvent):
                for event in _stream_event_to_events(message.event):
                    yield event
            elif isinstance(message, AssistantMessage):
                for event in _blocks_to_events(message.content, skip_streamed=partial):
                    yield event
            elif isinstance(message, SystemMessage):
                yield {
                    "type": "system",
                    "subtype": getattr(message, "subtype", None),
                    "data": getattr(message, "data", None),
                }
            elif isinstance(message, ResultMessage):
                yield {
                    "type": "result",
                    "session_id": getattr(message, "session_id", None),
                    "cost_usd": getattr(message, "total_cost_usd", None),
                    "is_error": getattr(message, "is_error", None),
                }
    except Exception as exc:
        logger.error(f"run_prompt failed: {type(exc).__name__}: {exc}")
        yield {"type": "error", "message": f"{type(exc).__name__}: {exc}"}
