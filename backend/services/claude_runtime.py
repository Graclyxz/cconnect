"""Wraps the Claude Agent SDK query() into a stream of normalized event dicts."""

import asyncio
import json
import os
import subprocess
from pathlib import Path
from typing import Any, AsyncIterator, Awaitable, Callable, Optional

from loguru import logger

from core.config import AI_WORKDIR, PORT, SHARED_DIR

_AGENT_PROMPT_FILE = Path(__file__).resolve().parent.parent / "prompts" / "agent.md"


def _share_url_base() -> str:
    """Tailscale IP resolved live since it can change; falls back to localhost."""
    host = "localhost"
    try:
        out = subprocess.run(["tailscale", "ip", "-4"], capture_output=True, text=True, timeout=3)
        ip = out.stdout.strip().splitlines()[0].strip() if out.returncode == 0 else ""
        if ip:
            host = ip
    except Exception:
        pass
    return f"http://{host}:{PORT}/api/shared"


def _agent_append() -> str:
    """Read on every call so the markdown can be edited without restarting the server."""
    try:
        text = _AGENT_PROMPT_FILE.read_text(encoding="utf-8")
    except OSError:
        return ""
    text = text.replace("{{SHARED_DIR}}", SHARED_DIR).replace("{{SHARE_URL_BASE}}", _share_url_base())
    return text.strip()


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


def _task_events_from_result(result: Any) -> list[dict]:
    """Task* tools return their task(s) in tool_use_result, not in the tool block."""
    if not isinstance(result, dict):
        return []
    if isinstance(result.get("task"), dict):
        tasks = [result["task"]]
    elif isinstance(result.get("tasks"), list):
        tasks = [t for t in result["tasks"] if isinstance(t, dict)]
    else:
        return []
    return [
        {"type": "task", "id": str(t.get("id") or ""), "content": t.get("subject"), "status": t.get("status")}
        for t in tasks
        if t.get("id")
    ]


def _todo_items(todos: Any) -> list[dict]:
    if not isinstance(todos, list):
        return []
    return [
        {
            "content": t.get("content", ""),
            "status": t.get("status", "pending"),
            "active_form": t.get("activeForm", ""),
        }
        for t in todos
        if isinstance(t, dict)
    ]


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
            name = (getattr(block, "name", None) or "").strip() or "tool"
            raw_input = getattr(block, "input", None)
            if name == "TodoWrite" and isinstance(raw_input, dict):
                events.append({"type": "todos", "items": _todo_items(raw_input.get("todos"))})
            elif name == "TaskUpdate" and isinstance(raw_input, dict):
                events.append({
                    "type": "task",
                    "id": str(raw_input.get("taskId") or ""),
                    "content": raw_input.get("subject"),
                    "status": raw_input.get("status"),
                })
            elif not name.startswith("Task"):
                events.append({
                    "type": "tool_use",
                    "id": getattr(block, "id", None),
                    "name": name,
                    "input": _format_tool_input(raw_input),
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
    ask_user: Optional[Callable[[dict], Awaitable[dict]]] = None,
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
        UserMessage,
        PermissionResultAllow,
        PermissionResultDeny,
    )

    effort_level = None if effort in (None, "", "default") else effort
    extra_args = {"name": name} if name else {}

    system_prompt: dict = {"type": "preset", "preset": "claude_code"}
    append = _agent_append()
    if append:
        system_prompt["append"] = append

    async def _can_use_tool(tool_name: str, tool_input: dict, ctx) -> Any:
        if tool_name == "AskUserQuestion" and isinstance(tool_input, dict):
            q = (tool_input.get("questions") or [{}])[0]
            options = [
                {"id": f"q_{i}", "label": opt.get("label", ""), "description": opt.get("description")}
                for i, opt in enumerate(q.get("options", []))
                if isinstance(opt, dict)
            ]
            response = await ask_user({
                "kind": "question",
                "title": q.get("question") or q.get("header"),
                "tool_name": q.get("header"),
                "input": "",
                "options": options,
                "free_text": "optional",
            })
            free_text = (response.get("free_text") or "").strip()
            chosen_id = response.get("option_id")
            chosen_label = next((o["label"] for o in options if o["id"] == chosen_id), "")
            answer = free_text or chosen_label
            return PermissionResultDeny(message=answer or "(no answer)")

        response = await ask_user({
            "kind": "permission",
            "tool_name": tool_name,
            "input": _format_tool_input(tool_input),
            "options": [
                {"id": "allow"},
                {"id": "allow_always"},
                {"id": "deny"},
            ],
            "free_text": "optional",
        })
        option = response.get("option_id")
        free_text = (response.get("free_text") or "").strip()
        if option == "allow_always":
            return PermissionResultAllow(updated_permissions=list(getattr(ctx, "suggestions", []) or []))
        if option == "allow":
            return PermissionResultAllow()
        return PermissionResultDeny(message=free_text)

    options = ClaudeAgentOptions(
        cwd=cwd,
        permission_mode=permission_mode,
        resume=resume,
        fork_session=fork,
        model=model,
        effort=effort_level,
        system_prompt=system_prompt,
        setting_sources=["user", "project"],
        thinking={"type": "adaptive", "display": "summarized"},
        include_partial_messages=partial,
        extra_args=extra_args,
        can_use_tool=_can_use_tool if ask_user is not None else None,
    )

    # can_use_tool requires the SDK in streaming mode → wrap the prompt as an AsyncIterable.
    done = asyncio.Event()

    async def _prompt_stream():
        yield {
            "type": "user",
            "message": {"role": "user", "content": prompt},
            "parent_tool_use_id": None,
        }
        await done.wait()
    prompt_arg = _prompt_stream() if ask_user is not None else prompt

    try:
        async for message in query(prompt=prompt_arg, options=options):
            if isinstance(message, StreamEvent):
                for event in _stream_event_to_events(message.event):
                    yield event
            elif isinstance(message, AssistantMessage):
                for event in _blocks_to_events(message.content, skip_streamed=partial):
                    yield event
            elif isinstance(message, UserMessage):
                for event in _task_events_from_result(getattr(message, "tool_use_result", None)):
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
    finally:
        done.set()


async def generate_title(transcript: str) -> str:
    """Ask a fast model for a short conversation title and return ONLY the text — the
    caller does the actual rename. Runs in the isolated AI workspace, so its session
    stays a plain SDK session under that project key (never normalized to "cli", never
    recorded in history like a chat) and is filtered out of the app's history."""
    from claude_agent_sdk import query, ClaudeAgentOptions, AssistantMessage

    os.makedirs(AI_WORKDIR, exist_ok=True)
    options = ClaudeAgentOptions(
        cwd=AI_WORKDIR,
        permission_mode="default",
        model="haiku",
        system_prompt="You write conversation titles. Reply with ONLY the title: 3-6 words, Title Case, no quotes, no trailing punctuation.",
        setting_sources=[],
    )

    parts: list[str] = []
    try:
        async for message in query(prompt=f"Write a title for this conversation:\n\n{transcript}", options=options):
            if isinstance(message, AssistantMessage):
                for block in message.content:
                    if type(block).__name__ == "TextBlock":
                        parts.append(getattr(block, "text", ""))
    except Exception as exc:
        logger.error(f"generate_title failed: {type(exc).__name__}: {exc}")
    return "".join(parts).strip()
