"""Selectable Claude Code CLI: which binary the SDK drives (system / custom / bundled).

The SDK defaults to its bundled claude.exe, which may lag the CLI the user already has
on PATH. This lets the phone point CConnect at the system CLI (newer, carries features
like ultracode), a custom path, or the bundled one, and update the system CLI in place."""

import json
import platform
import shutil
import subprocess
from pathlib import Path
from typing import Optional

_STATE_FILE = Path(__file__).resolve().parent.parent / ".cli_settings.json"
_VALID_SOURCES = ("system", "custom", "bundled")
_DEFAULT_SOURCE = "system"

# Same fallback locations the SDK's own _find_cli scans after PATH, using the
# platform-correct binary name (claude.exe on Windows, claude elsewhere).
_CLI_NAME = "claude.exe" if platform.system() == "Windows" else "claude"
_KNOWN_LOCATIONS = (
    Path.home() / ".local/bin" / _CLI_NAME,
    Path.home() / ".npm-global/bin" / _CLI_NAME,
    Path("/usr/local/bin") / _CLI_NAME,
    Path.home() / "node_modules/.bin" / _CLI_NAME,
)


def _load() -> dict:
    try:
        return json.loads(_STATE_FILE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}


def _save(state: dict) -> None:
    _STATE_FILE.write_text(json.dumps(state, indent=2), encoding="utf-8")


def get_source() -> str:
    source = _load().get("source")
    return source if source in _VALID_SOURCES else _DEFAULT_SOURCE


def get_custom_path() -> Optional[str]:
    return _load().get("custom_path")


def system_cli() -> Optional[str]:
    """The CLI the user runs in their own terminal (PATH first, then known spots)."""
    if found := shutil.which("claude"):
        return found
    for path in _KNOWN_LOCATIONS:
        if path.exists() and path.is_file():
            return str(path)
    return None


def bundled_version() -> Optional[str]:
    try:
        from claude_agent_sdk._cli_version import __cli_version__
        return __cli_version__
    except Exception:
        return None


def resolve_cli_path() -> Optional[str]:
    """Path to pass as ClaudeAgentOptions.cli_path. None = let the SDK use its bundled CLI."""
    source = get_source()
    if source == "custom":
        path = get_custom_path()
        return path if path and Path(path).exists() else None
    if source == "system":
        return system_cli()
    return None


def cli_version(path: Optional[str]) -> Optional[str]:
    """Run `<cli> -v` and return the version string. None when it can't be queried."""
    if not path:
        return None
    try:
        result = subprocess.run([path, "-v"], capture_output=True, text=True, timeout=15)
    except (OSError, subprocess.SubprocessError):
        return None
    return result.stdout.strip() or None


def update_cli() -> dict:
    """Self-update the resolved CLI in place (`claude update`). Bundled can't update."""
    if get_source() == "bundled":
        return {"ok": False, "message": "The bundled CLI can't be updated; switch to system."}
    path = resolve_cli_path()
    if not path:
        return {"ok": False, "message": "No CLI resolved for the current source."}
    try:
        result = subprocess.run([path, "update"], capture_output=True, text=True, timeout=180)
    except (OSError, subprocess.SubprocessError) as exc:
        return {"ok": False, "message": str(exc)}
    output = (result.stdout + result.stderr).strip()
    return {"ok": result.returncode == 0, "message": output, "version": cli_version(path)}


def set_source(source: str, custom_path: Optional[str] = None) -> None:
    if source not in _VALID_SOURCES:
        raise ValueError(f"invalid source: {source}")
    if source == "custom" and not (custom_path and Path(custom_path).exists()):
        raise ValueError("custom source requires an existing path")
    _save({"source": source, "custom_path": custom_path})


def status() -> dict:
    source = get_source()
    resolved = resolve_cli_path()
    system = system_cli()
    return {
        "source": source,
        "resolved_path": resolved,
        "active_version": cli_version(resolved) if resolved else bundled_version(),
        "bundled_version": bundled_version(),
        "system_path": system,
        "system_version": cli_version(system),
        "custom_path": get_custom_path(),
    }
