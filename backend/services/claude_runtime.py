"""Wraps the Claude Agent SDK query() into a stream of normalized event dicts."""

import difflib
import json
import os
from pathlib import Path
from typing import Any, AsyncIterator, Awaitable, Callable, Optional

from loguru import logger

from core.config import AI_WORKDIR, PORT, SHARED_DIR
from mcps import build_cconnect_server

_AGENT_PROMPT_FILE = Path(__file__).resolve().parent.parent / "prompts" / "agent.md"
_FILE_EDIT_TOOLS = frozenset({"Edit", "Write", "MultiEdit", "NotebookEdit"})


def _agent_append(base_url: Optional[str]) -> str:
    """Read on every call so the markdown can be edited without restarting the server."""
    try:
        text = _AGENT_PROMPT_FILE.read_text(encoding="utf-8")
    except OSError:
        return ""
    effective = base_url or f"http://localhost:{PORT}/api"
    text = text.replace("{{SHARED_DIR}}", SHARED_DIR).replace("{{BASE_URL}}", effective.rstrip("/"))
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


def _flatten_result_content(content: Any) -> str:
    """Tool results can be a string, a list of {type:'text', text:...} blocks, or raw text."""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for r in content:
            if isinstance(r, dict) and r.get("type") == "text":
                parts.append(r.get("text", ""))
            elif isinstance(r, str):
                parts.append(r)
        return "\n".join(p for p in parts if p)
    return "" if content is None else str(content)


def _unified_diff(old: str, new: str, path: str) -> list[str]:
    return list(
        difflib.unified_diff(
            (old or "").splitlines(),
            (new or "").splitlines(),
            fromfile=path,
            tofile=path,
            lineterm="",
        )
    )


# +/- is stripped from text because kind already encodes it.
def _classify_diff_lines(lines: list[str]) -> list[dict[str, str]]:
    out: list[dict[str, str]] = []
    for line in lines:
        if line.startswith("---") or line.startswith("+++"):
            out.append({"kind": "header", "text": line})
        elif line.startswith("@@"):
            out.append({"kind": "hunk", "text": line})
        elif line.startswith("+"):
            out.append({"kind": "add", "text": line[1:]})
        elif line.startswith("-"):
            out.append({"kind": "del", "text": line[1:]})
        else:
            out.append({"kind": "ctx", "text": line[1:] if line.startswith(" ") else line})
    return out


def _build_file_diff(name: str, raw_input: dict, path: str) -> list[dict[str, str]]:
    if name == "Edit":
        return _classify_diff_lines(
            _unified_diff(raw_input.get("old_string") or "", raw_input.get("new_string") or "", path)
        )
    if name == "MultiEdit":
        merged: list[str] = []
        for edit in raw_input.get("edits") or []:
            if not isinstance(edit, dict):
                continue
            chunk = _unified_diff(edit.get("old_string") or "", edit.get("new_string") or "", path)
            if chunk:
                merged.extend(chunk)
        return _classify_diff_lines(merged)
    if name == "Write":
        return _classify_diff_lines(_unified_diff("", raw_input.get("content") or "", path))
    if name == "NotebookEdit":
        return _classify_diff_lines(_unified_diff("", raw_input.get("new_source") or "", path))
    return []


def _file_change_event(block: Any, raw_input: Any, hidden: set[str]) -> dict | None:
    if not isinstance(raw_input, dict):
        return None
    path = raw_input.get("file_path") or raw_input.get("notebook_path")
    if not isinstance(path, str) or not path:
        return None
    name = (getattr(block, "name", None) or "").strip()
    diff_lines = _build_file_diff(name, raw_input, path)
    bid = getattr(block, "id", None)
    if bid:
        hidden.add(bid)
    return {"type": "file_change", "id": bid, "path": path, "diff_lines": diff_lines}


def _blocks_to_events(content: Any, skip_streamed: bool = False, hidden_tool_ids: set[str] | None = None) -> list[dict]:
    """Convert message blocks to events. When ``skip_streamed`` is set,
    text and thinking are omitted because they already arrived as deltas.
    ``hidden_tool_ids`` collects tool_use ids that should be suppressed
    (used to hide AskUserQuestion's tool_use AND its matching tool_result)."""
    events: list[dict] = []
    hidden = hidden_tool_ids if hidden_tool_ids is not None else set()
    for block in content or []:
        kind = type(block).__name__
        if kind == "TextBlock":
            if skip_streamed:
                continue
            events.append({"type": "assistant_text", "text": getattr(block, "text", "").strip()})
        elif kind == "ThinkingBlock":
            if skip_streamed:
                continue
            events.append({"type": "thinking", "text": (getattr(block, "thinking", "") or getattr(block, "text", "")).strip()})
        elif kind == "ToolUseBlock":
            name = (getattr(block, "name", None) or "").strip() or "tool"
            raw_input = getattr(block, "input", None)
            if name == "AskUserQuestion":
                # Surfaced as an interaction block; don't render the tool_use itself.
                bid = getattr(block, "id", None)
                if bid:
                    hidden.add(bid)
                continue
            if name == "TodoWrite" and isinstance(raw_input, dict):
                events.append({"type": "todos", "items": _todo_items(raw_input.get("todos"))})
            elif name == "TaskUpdate" and isinstance(raw_input, dict):
                events.append({
                    "type": "task",
                    "id": str(raw_input.get("taskId") or ""),
                    "content": raw_input.get("subject"),
                    "status": raw_input.get("status"),
                })
            elif name in _FILE_EDIT_TOOLS:
                event = _file_change_event(block, raw_input, hidden)
                if event is not None:
                    events.append(event)
            elif not name.startswith("Task"):
                events.append({
                    "type": "tool_use",
                    "id": getattr(block, "id", None),
                    "name": name,
                    "input": _format_tool_input(raw_input),
                })
        elif kind == "ToolResultBlock":
            tuid = getattr(block, "tool_use_id", None)
            if tuid and tuid in hidden:
                continue
            events.append({
                "type": "tool_result",
                "tool_use_id": tuid,
                "content": _flatten_result_content(getattr(block, "content", None)).strip(),
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
    base_url: Optional[str] = None,
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
        HookMatcher,
    )

    effort_level = None if effort in (None, "", "default") else effort
    extra_args = {"name": name} if name else {}

    system_prompt: dict = {"type": "preset", "preset": "claude_code"}
    append = _agent_append(base_url)
    if append:
        system_prompt["append"] = append

    async def _can_use_tool(tool_name: str, tool_input: dict, ctx) -> Any:
        if tool_name == "AskUserQuestion" and isinstance(tool_input, dict):
            questions = tool_input.get("questions") or []
            answers: dict[str, Any] = {}
            for q in questions:
                if not isinstance(q, dict):
                    continue
                opts = [
                    {"id": f"q_{i}", "label": str(opt.get("label", "")), "description": opt.get("description")}
                    for i, opt in enumerate(q.get("options", []))
                    if isinstance(opt, dict)
                ]
                response = await ask_user({
                    "kind": "question",
                    "title": q.get("question") or q.get("header"),
                    "tool_name": q.get("header"),
                    "input": "",
                    "options": opts,
                    "free_text": "optional",
                })
                free_text = (response.get("free_text") or "").strip()
                chosen_id = response.get("option_id")
                chosen_label = next((o["label"] for o in opts if o["id"] == chosen_id), "")
                answers[str(q.get("question", ""))] = free_text or chosen_label
            return PermissionResultAllow(updated_input={"questions": questions, "answers": answers})

        response = await ask_user({
            "kind": "permission",
            "tool_name": tool_name,
            "tool_use_id": getattr(ctx, "tool_use_id", None),
            "input": _format_tool_input(tool_input),
            "options": [
                {"id": "allow"},
                {"id": "allow_always"},
                {"id": "deny"},
                {"id": "different"},
            ],
            "free_text": "off",
        })
        option = response.get("option_id")
        if option == "allow_always":
            persist = [
                s for s in (getattr(ctx, "suggestions", []) or [])
                if getattr(s, "destination", None) == "localSettings"
            ]
            return PermissionResultAllow(updated_input=tool_input, updated_permissions=persist)
        if option == "allow":
            return PermissionResultAllow(updated_input=tool_input)
        if option == "different":
            return PermissionResultDeny(
                message="User declined this action and wants to redirect. Ask them how they'd like you to proceed instead."
            )
        return PermissionResultDeny(message="User declined")

    # Required by the SDK: a no-op PreToolUse hook keeps the prompt stream open while
    # can_use_tool waits for the user's decision (without it, the stream closes first).
    async def _keep_stream_open(input_data, tool_use_id, context):
        return {"continue_": True}

    options_kwargs: dict[str, Any] = dict(
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
        mcp_servers={"cconnect": build_cconnect_server()},
    )
    if ask_user is not None:
        options_kwargs["can_use_tool"] = _can_use_tool
        options_kwargs["hooks"] = {"PreToolUse": [HookMatcher(matcher=None, hooks=[_keep_stream_open])]}
    options = ClaudeAgentOptions(**options_kwargs)

    async def _prompt_stream():
        yield {"type": "user", "message": {"role": "user", "content": prompt}}
    prompt_arg = _prompt_stream() if ask_user is not None else prompt

    hidden_tool_ids: set[str] = set()

    try:
        async for message in query(prompt=prompt_arg, options=options):
            if isinstance(message, StreamEvent):
                for event in _stream_event_to_events(message.event):
                    yield event
            elif isinstance(message, AssistantMessage):
                for event in _blocks_to_events(message.content, skip_streamed=partial, hidden_tool_ids=hidden_tool_ids):
                    yield event
            elif isinstance(message, UserMessage):
                for block in getattr(message, "content", None) or []:
                    if type(block).__name__ != "ToolResultBlock":
                        continue
                    tuid = getattr(block, "tool_use_id", None)
                    if tuid and tuid in hidden_tool_ids:
                        continue
                    yield {
                        "type": "tool_result",
                        "tool_use_id": tuid,
                        "content": _flatten_result_content(getattr(block, "content", None)),
                        "is_error": getattr(block, "is_error", None),
                    }
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


