"""Application configuration loaded from environment variables (.env for local dev)."""

import os

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

PORT = int(os.environ.get("PORT", "8000"))

# Bridge / auth settings are added here as the Claude Code integration lands.

__all__ = [
    "PORT",
]
