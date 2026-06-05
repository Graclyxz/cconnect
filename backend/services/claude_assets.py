"""Read-only views over the local Claude Code installation (plugins, skills, MCP)
plus the user prompt (USER.md) appended after the system context (CCONNECT.md)."""

import json
import re
from pathlib import Path

_CLAUDE_DIR = Path.home() / ".claude"
_USER_PROMPT = Path(__file__).resolve().parent.parent / "prompts" / "USER.md"

_FRONTMATTER_RE = re.compile(r"^---\s*\n(.*?)\n---", re.DOTALL)


def get_user_prompt() -> str:
    try:
        return _USER_PROMPT.read_text(encoding="utf-8")
    except OSError:
        return ""


def set_user_prompt(text: str) -> None:
    _USER_PROMPT.write_text(text, encoding="utf-8")


def _read_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def _enabled_plugins() -> dict:
    data = _read_json(_CLAUDE_DIR / "settings.json").get("enabledPlugins")
    return data if isinstance(data, dict) else {}


def list_marketplaces() -> list[dict]:
    known = _read_json(_CLAUDE_DIR / "plugins" / "known_marketplaces.json")
    items = []
    for name, info in known.items():
        if not isinstance(info, dict):
            continue
        source = info.get("source") or {}
        items.append({
            "name": name,
            "repo": source.get("repo") or source.get("url"),
            "last_updated": info.get("lastUpdated"),
        })
    items.sort(key=lambda m: m["name"])
    return items


def list_plugins() -> list[dict]:
    installed = _read_json(_CLAUDE_DIR / "plugins" / "installed_plugins.json").get("plugins", {})
    enabled = _enabled_plugins()
    items = []
    for key, installs in installed.items():
        if not isinstance(installs, list) or not installs:
            continue
        name, _, marketplace = key.partition("@")
        info = installs[0]
        items.append({
            "name": name,
            "marketplace": marketplace,
            "version": (info.get("version") or "").replace("unknown", "") or None,
            "scope": info.get("scope"),
            "enabled": bool(enabled.get(key, True)),
            "install_path": info.get("installPath"),
        })
    items.sort(key=lambda p: (not p["enabled"], p["name"]))
    return items


def _skill_meta(skill_file: Path) -> dict | None:
    try:
        text = skill_file.read_text(encoding="utf-8")
    except OSError:
        return None
    meta = {}
    match = _FRONTMATTER_RE.match(text)
    if match:
        for line in match.group(1).splitlines():
            key, _, value = line.partition(":")
            if key.strip() in ("name", "description"):
                meta[key.strip()] = value.strip().strip("\"'")
    return meta or None


def list_skills() -> list[dict]:
    items = []
    seen: set[tuple[str, str]] = set()
    for plugin in list_plugins():
        install = plugin.get("install_path")
        if not install:
            continue
        skills_dir = Path(install) / "skills"
        if not skills_dir.is_dir():
            continue
        for skill_file in sorted(skills_dir.glob("*/SKILL.md")):
            meta = _skill_meta(skill_file) or {}
            name = meta.get("name") or skill_file.parent.name
            key = (plugin["name"], name)
            if key in seen:
                continue
            seen.add(key)
            items.append({
                "name": name,
                "description": meta.get("description"),
                "plugin": plugin["name"],
                "enabled": plugin["enabled"],
            })
    personal = _CLAUDE_DIR / "skills"
    if personal.is_dir():
        for skill_file in sorted(personal.glob("*/SKILL.md")):
            meta = _skill_meta(skill_file) or {}
            items.append({
                "name": meta.get("name") or skill_file.parent.name,
                "description": meta.get("description"),
                "plugin": None,
                "enabled": True,
            })
    items.sort(key=lambda s: (not s["enabled"], s["name"]))
    return items


def list_mcp_servers() -> list[dict]:
    servers = _read_json(Path.home() / ".claude.json").get("mcpServers", {})
    items = []
    for name, cfg in servers.items():
        if not isinstance(cfg, dict):
            continue
        kind = cfg.get("type") or ("http" if cfg.get("url") else "stdio")
        detail = cfg.get("url") or " ".join([cfg.get("command", ""), *cfg.get("args", [])]).strip()
        items.append({"name": name, "type": kind, "detail": detail or None})
    items.sort(key=lambda s: s["name"])
    return items
