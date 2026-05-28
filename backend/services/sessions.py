"""Read Claude Code projects and session transcripts from ~/.claude/projects."""

import json
import re
import time
from pathlib import Path
from typing import Any, Optional

from core.config import AI_WORKDIR, CLAUDE_PROJECTS_DIR

_KEY_RE = re.compile(r"^[A-Za-z0-9._-]+$")
_SESSION_RE = re.compile(r"^[A-Za-z0-9._-]+$")

# Project key for the internal AI workspace, hidden from history listings.
_AI_PROJECT_KEY = re.sub(r"[^A-Za-z0-9]", "-", AI_WORKDIR)

_ASK_ANSWERS_RE = re.compile(r'"([^"]+)"="([^"]*)"')


def _base() -> Path:
    return Path(CLAUDE_PROJECTS_DIR)


def record_prompt_history(cwd: str, session_id: str, text: str):
    if not session_id:
        return
    path = _base().parent / "history.jsonl"
    entry = {
        "display": text,
        "pastedContents": {},
        "timestamp": int(time.time() * 1000),
        "project": cwd,
        "sessionId": session_id,
    }
    with path.open("a", encoding="utf-8") as fh:
        fh.write(json.dumps(entry, ensure_ascii=False) + "\n")


def normalize_session_entrypoint(cwd: str, session_id: str):
    """Rewrite the SDK's "sdk-*" entrypoint to "cli"; `claude --resume` hides sdk sessions."""
    if not _SESSION_RE.match(session_id or ""):
        return
    encoded = re.sub(r"[^A-Za-z0-9]", "-", cwd)
    path = _base() / encoded / f"{session_id}.jsonl"
    if not path.is_file():
        return
    text = path.read_text(encoding="utf-8")
    fixed = re.sub(r'"entrypoint":"sdk-[A-Za-z]+"', '"entrypoint":"cli"', text)
    if fixed != text:
        path.write_text(fixed, encoding="utf-8")


def _project_dir(project_key: str) -> Path:
    if not _KEY_RE.match(project_key):
        raise ValueError("invalid project key")
    path = (_base() / project_key).resolve()
    if _base().resolve() not in path.parents and path != _base().resolve():
        raise ValueError("project key escapes the projects directory")
    return path


def _session_file(project_key: str, session_id: str) -> Path:
    if not _SESSION_RE.match(session_id):
        raise ValueError("invalid session id")
    path = (_project_dir(project_key) / f"{session_id}.jsonl").resolve()
    if path.parent != _project_dir(project_key):
        raise ValueError("session id escapes the project directory")
    return path


def _iter_lines(path: Path):
    with path.open("r", encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            try:
                yield json.loads(line)
            except json.JSONDecodeError:
                continue


def _text_from_content(content: Any) -> str:
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for block in content:
            if not isinstance(block, dict):
                continue
            btype = block.get("type")
            if btype == "text":
                parts.append(block.get("text", ""))
            elif btype == "tool_use":
                parts.append(f"[tool: {block.get('name', '')}]")
            elif btype == "tool_result":
                parts.append("[tool result]")
        return "\n".join(p for p in parts if p)
    return ""


def _read_cwd(path: Path) -> Optional[str]:
    for entry in _iter_lines(path):
        cwd = entry.get("cwd")
        if cwd:
            return cwd
    return None


def _preview(path: Path, limit: int = 120) -> Optional[str]:
    for entry in _iter_lines(path):
        if entry.get("type") == "user":
            text = _text_from_content(entry.get("message", {}).get("content"))
            if text:
                return text[:limit]
    return None


def _session_meta(path: Path) -> tuple[Optional[str], Optional[str], Optional[str], Optional[str], Optional[str]]:
    """Single pass over a transcript: (cwd, first-user preview, title, color, entrypoint).
    Title prefers the user's `custom-title`, falling back to the CLI's `ai-title`. Color
    is the CLI's `agent-color`."""
    cwd = preview = title = ai_title = color = entrypoint = None
    for entry in _iter_lines(path):
        etype = entry.get("type")
        if cwd is None and entry.get("cwd"):
            cwd = entry.get("cwd")
        if entrypoint is None and entry.get("entrypoint"):
            entrypoint = entry.get("entrypoint")
        if etype == "custom-title" and entry.get("customTitle"):
            title = entry.get("customTitle")
        elif etype == "ai-title" and entry.get("aiTitle"):
            ai_title = entry.get("aiTitle")
        elif etype == "agent-color" and entry.get("agentColor"):
            color = entry.get("agentColor")
        elif preview is None and etype == "user":
            text = _text_from_content(entry.get("message", {}).get("content"))
            if text:
                preview = text[:120]
    return cwd, preview, title or ai_title, color, entrypoint


def list_projects() -> list[dict]:
    base = _base()
    if not base.is_dir():
        return []
    projects = []
    for directory in base.iterdir():
        if not directory.is_dir() or directory.name == _AI_PROJECT_KEY:
            continue
        sessions = sorted(directory.glob("*.jsonl"), key=lambda p: p.stat().st_mtime, reverse=True)
        projects.append({
            "project_key": directory.name,
            "path": _read_cwd(sessions[0]) if sessions else None,
            "session_count": len(sessions),
            "last_active": sessions[0].stat().st_mtime if sessions else None,
        })
    projects.sort(key=lambda p: p["last_active"] or 0, reverse=True)
    return projects


def _sessions_from_files(files: list[tuple[Path, str]]) -> list[dict]:
    files = sorted(files, key=lambda t: t[0].stat().st_mtime, reverse=True)
    items: list[dict] = []
    for file, project_key in files:
        stat = file.stat()
        cwd, preview, title, color, entrypoint = _session_meta(file)
        if entrypoint != "cli":
            continue
        items.append({
            "session_id": file.stem,
            "project_key": project_key,
            "path": cwd,
            "last_active": stat.st_mtime,
            "size": stat.st_size,
            "preview": preview,
            "title": title,
            "color": color,
        })
    return items


def list_sessions(project_key: str) -> list[dict]:
    directory = _project_dir(project_key)
    if not directory.is_dir():
        return []
    files = [(file, project_key) for file in directory.glob("*.jsonl")]
    return _sessions_from_files(files)


def list_all_sessions() -> list[dict]:
    base = _base()
    if not base.is_dir():
        return []
    files = [
        (file, directory.name)
        for directory in base.iterdir() if directory.is_dir() and directory.name != _AI_PROJECT_KEY
        for file in directory.glob("*.jsonl")
    ]
    return _sessions_from_files(files)


def rename_session(project_key: str, session_id: str, title: str) -> bool:
    """Set the session's display title (the `custom-title`/`agent-name` entries the
    CLI uses), so it shows renamed in the picker and the app history."""
    file = _session_file(project_key, session_id)
    if not file.is_file():
        return False
    safe = title.replace("\n", " ").strip()[:80]
    out: list[str] = []
    found = False
    for line in file.read_text(encoding="utf-8").splitlines():
        try:
            obj = json.loads(line)
        except json.JSONDecodeError:
            out.append(line)
            continue
        if obj.get("type") == "custom-title":
            obj["customTitle"] = safe
            found = True
            out.append(json.dumps(obj, ensure_ascii=False))
        elif obj.get("type") == "agent-name":
            obj["agentName"] = safe
            out.append(json.dumps(obj, ensure_ascii=False))
        else:
            out.append(line)
    if not found:
        out.append(json.dumps({"type": "custom-title", "customTitle": safe, "sessionId": session_id}, ensure_ascii=False))
    file.write_text("\n".join(out) + "\n", encoding="utf-8")
    return True


def set_session_color(project_key: str, session_id: str, color: str) -> bool:
    """Set the conversation accent the CLI shows, via its `agent-color` entry (the
    same one `claude --resume` reads). Empty color clears it."""
    file = _session_file(project_key, session_id)
    if not file.is_file():
        return False
    out = [
        line
        for line in file.read_text(encoding="utf-8").splitlines()
        if not _is_type(line, "agent-color")
    ]
    if color:
        out.append(json.dumps({"type": "agent-color", "agentColor": color, "sessionId": session_id}, ensure_ascii=False))
    file.write_text("\n".join(out) + "\n", encoding="utf-8")
    return True


def _is_type(line: str, type_name: str) -> bool:
    try:
        return json.loads(line).get("type") == type_name
    except json.JSONDecodeError:
        return False


def _transcript_for_title(path: Path, max_chars: int = 2000) -> str:
    parts: list[str] = []
    total = 0
    for entry in _iter_lines(path):
        if entry.get("type") not in ("user", "assistant"):
            continue
        text = _text_from_content(entry.get("message", {}).get("content"))
        if not text:
            continue
        chunk = f"{entry.get('type')}: {text[:600]}"
        parts.append(chunk)
        total += len(chunk)
        if total >= max_chars:
            break
    return "\n".join(parts)[:max_chars]


async def auto_generate_title(project_key: str, session_id: str) -> Optional[str]:
    """Get a short title from the model, then do the rename ourselves (the model only
    returns the text)."""
    file = _session_file(project_key, session_id)
    if not file.is_file():
        return None
    transcript = _transcript_for_title(file)
    if not transcript:
        return None
    from services.claude_runtime import generate_title

    raw = await generate_title(transcript)
    title = raw.replace("\n", " ").strip().strip("\"'").strip().rstrip(".")[:80]
    if not title:
        return None
    rename_session(project_key, session_id, title)
    return title


def delete_session(project_key: str, session_id: str) -> bool:
    file = _session_file(project_key, session_id)
    if not file.is_file():
        return False
    file.unlink()
    return True


def _parse_ask_answers(content: object) -> dict[str, str]:
    """Claude Code stores AskUserQuestion answers in the tool_result content as `"Q"="A"`."""
    from services.claude_runtime import _flatten_result_content
    text = _flatten_result_content(content)
    return dict(_ASK_ANSWERS_RE.findall(text))


def get_session_messages(project_key: str, session_id: str) -> list[dict]:
    file = _session_file(project_key, session_id)
    if not file.is_file():
        return []
    entries = list(_iter_lines(file))
    # AskUserQuestion answers live in the tool_result, not in the tool_use input.
    tool_result_by_id: dict[str, object] = {}
    for entry in entries:
        msg = entry.get("message", {})
        content = msg.get("content")
        if not isinstance(content, list):
            continue
        for block in content:
            if isinstance(block, dict) and block.get("type") == "tool_result":
                tuid = block.get("tool_use_id")
                if isinstance(tuid, str):
                    tool_result_by_id[tuid] = block.get("content")
    messages: list[dict] = []
    hidden_ids: set[str] = set()
    for entry in entries:
        etype = entry.get("type")
        if etype == "summary":
            text = entry.get("summary", "")
            if text:
                messages.append({"type": "summary", "text": text})
            continue
        if entry.get("isMeta") or entry.get("isSidechain"):
            continue
        message = entry.get("message", {})
        if message.get("stop_reason") == "stop_sequence":
            continue
        role = message.get("role", etype)
        content = message.get("content")
        if isinstance(content, str):
            if content.strip():
                messages.append({"type": "text", "role": role, "text": content})
            continue
        if not isinstance(content, list):
            continue
        for block in content:
            if not isinstance(block, dict):
                continue
            btype = block.get("type")
            if btype == "text":
                text = block.get("text", "")
                if text.strip():
                    messages.append({"type": "text", "role": role, "text": text})
            elif btype == "thinking":
                text = block.get("thinking") or block.get("text", "")
                if text.strip():
                    messages.append({"type": "thinking", "text": text})
            elif btype == "tool_use":
                from services.claude_runtime import _FILE_EDIT_TOOLS, _build_file_diff, _format_tool_input
                name = block.get("name", "")
                inp = block.get("input")
                bid = block.get("id")
                if name == "AskUserQuestion" and isinstance(inp, dict):
                    if isinstance(bid, str):
                        hidden_ids.add(bid)
                    questions = inp.get("questions") or []
                    answers = _parse_ask_answers(tool_result_by_id.get(bid or ""))
                    for q in questions:
                        if not isinstance(q, dict):
                            continue
                        qtext = q.get("question") or q.get("header") or ""
                        opts = [
                            {"id": f"q_{i}", "label": str(opt.get("label", "")), "description": opt.get("description")}
                            for i, opt in enumerate(q.get("options", []))
                            if isinstance(opt, dict)
                        ]
                        answer = answers.get(qtext, "")
                        chosen_id = next((o["id"] for o in opts if o["label"] == answer), None)
                        messages.append({
                            "type": "interaction",
                            "title": qtext,
                            "tool_name": q.get("header"),
                            "options": opts,
                            "free_text": "optional",
                            "resolved": chosen_id or "",
                            "resolved_text": None if chosen_id else answer,
                        })
                    continue
                if name == "TodoWrite" or name.startswith("Task"):
                    if isinstance(bid, str):
                        hidden_ids.add(bid)
                    continue
                if name in _FILE_EDIT_TOOLS and isinstance(inp, dict):
                    path = inp.get("file_path") or inp.get("notebook_path")
                    if isinstance(path, str) and path:
                        if isinstance(bid, str):
                            hidden_ids.add(bid)
                        messages.append({
                            "type": "file_change",
                            "path": path,
                            "diff": _build_file_diff(name, inp, path),
                            "id": bid,
                        })
                        continue
                messages.append({"type": "tool_use", "name": name, "text": _format_tool_input(inp)})
            elif btype == "tool_result":
                if block.get("tool_use_id") in hidden_ids:
                    continue
                from services.claude_runtime import _flatten_result_content
                text = _flatten_result_content(block.get("content"))
                if text.strip():
                    messages.append({"type": "tool_result", "text": text})
    return messages
