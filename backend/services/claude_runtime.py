"""Wraps the Claude Agent SDK query() into a stream of normalized event dicts."""

from typing import Any, AsyncIterator, Optional

from loguru import logger


def _blocks_to_events(content: Any) -> list[dict]:
    events: list[dict] = []
    for block in content or []:
        kind = type(block).__name__
        if kind == "TextBlock":
            events.append({"type": "assistant_text", "text": getattr(block, "text", "")})
        elif kind == "ThinkingBlock":
            events.append({"type": "thinking", "text": getattr(block, "thinking", "") or getattr(block, "text", "")})
        elif kind == "ToolUseBlock":
            events.append({
                "type": "tool_use",
                "id": getattr(block, "id", None),
                "name": getattr(block, "name", None),
                "input": getattr(block, "input", None),
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
) -> AsyncIterator[dict]:
    """Yield normalized events for one prompt. The SDK import is deferred because the
    package is installed/upgraded during app startup."""
    from claude_agent_sdk import (
        query,
        ClaudeAgentOptions,
        AssistantMessage,
        SystemMessage,
        ResultMessage,
    )

    options = ClaudeAgentOptions(
        cwd=cwd,
        permission_mode=permission_mode,
        resume=resume,
        fork_session=fork,
        model=model,
    )

    try:
        async for message in query(prompt=prompt, options=options):
            if isinstance(message, AssistantMessage):
                for event in _blocks_to_events(message.content):
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
