"""Application configuration loaded from environment variables (.env for local dev)."""

import os
from pathlib import Path

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

PORT = int(os.environ.get("PORT", "8723"))

CLAUDE_PROJECTS_DIR = os.environ.get(
    "CLAUDE_PROJECTS_DIR",
    str(Path.home() / ".claude" / "projects"),
)

PERMISSION_MODES = ("default", "acceptEdits", "plan", "dontAsk", "bypassPermissions")
DEFAULT_PERMISSION_MODE = os.environ.get("DEFAULT_PERMISSION_MODE", "default")

# Pull the latest claude-agent-sdk on startup. Disable for faster dev reloads.
AUTO_UPDATE_SDK = os.environ.get("AUTO_UPDATE_SDK", "1") not in ("0", "false", "False")

__all__ = [
    "PORT",
    "CLAUDE_PROJECTS_DIR",
    "PERMISSION_MODES",
    "DEFAULT_PERMISSION_MODE",
    "AUTO_UPDATE_SDK",
]
