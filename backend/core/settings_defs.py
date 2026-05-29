"""The single source of truth for backend-owned settings: which exist, their type, default,
and description. Add a setting here — no DB migration, the KV table is unchanged.

Defaults and descriptions live in code (synced into the DB, never authored there) so they
track app versions; the effective value is always `stored override ?? default here`."""

from dataclasses import dataclass
from typing import Any, Optional

from core.config import DEFAULT_EFFORT, DEFAULT_MODEL, DEFAULT_PERMISSION_MODE


@dataclass(frozen=True)
class SettingDef:
    default: Any
    type: type
    description: str
    allowed: Optional[tuple] = None


SETTINGS: dict[str, SettingDef] = {
    "model": SettingDef(DEFAULT_MODEL, str, "Claude model alias used for chat generation"),
    "effort": SettingDef(DEFAULT_EFFORT, str, "Reasoning effort: low..max, or ultracode (xhigh + workflow orchestration)"),
    "permission_mode": SettingDef(DEFAULT_PERMISSION_MODE, str, "Default permission mode for new chat sessions"),
    "streaming": SettingDef(True, bool, "Stream tokens (partial messages) as they are generated"),
    "cli_source": SettingDef("system", str, "Which Claude CLI the backend drives: system, custom, or bundled", allowed=("system", "custom", "bundled")),
    "cli_custom_path": SettingDef(None, str, "Filesystem path to the CLI when cli_source is custom"),
}
