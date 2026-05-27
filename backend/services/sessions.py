"""Read Claude Code projects and session transcripts from ~/.claude/projects."""

import json
import re
import time
from pathlib import Path
from typing import Any, Optional

from core.config import CLAUDE_PROJECTS_DIR

_KEY_RE = re.compile(r"^[A-Za-z0-9._-]+$")
_SESSION_RE = re.compile(r"^[A-Za-z0-9._-]+$")


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


def list_projects() -> list[dict]:
    base = _base()
    if not base.is_dir():
        return []
    projects = []
    for directory in base.iterdir():
        if not directory.is_dir():
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


def list_sessions(project_key: str) -> list[dict]:
    directory = _project_dir(project_key)
    if not directory.is_dir():
        return []
    sessions = []
    for file in directory.glob("*.jsonl"):
        stat = file.stat()
        sessions.append({
            "session_id": file.stem,
            "last_active": stat.st_mtime,
            "size": stat.st_size,
            "preview": _preview(file),
        })
    sessions.sort(key=lambda s: s["last_active"], reverse=True)
    return sessions


def delete_session(project_key: str, session_id: str) -> bool:
    file = _session_file(project_key, session_id)
    if not file.is_file():
        return False
    file.unlink()
    return True


def get_session_messages(project_key: str, session_id: str) -> list[dict]:
    file = _session_file(project_key, session_id)
    if not file.is_file():
        return []
    messages = []
    for entry in _iter_lines(file):
        etype = entry.get("type")
        if etype == "summary":
            messages.append({"type": "summary", "text": entry.get("summary", "")})
            continue
        message = entry.get("message", {})
        messages.append({
            "type": etype,
            "role": message.get("role", etype),
            "text": _text_from_content(message.get("content")),
            "timestamp": entry.get("timestamp"),
            "uuid": entry.get("uuid"),
        })
    return messages
