"""Uvicorn server launcher. Use --production for no-reload multi-worker mode."""

import os
import sys

import uvicorn

from core.config import PORT


def main():
    is_production = "--production" in sys.argv
    is_windows = sys.platform == "win32"

    # On Windows the Claude CLI is spawned via asyncio subprocess, which needs
    # ProactorEventLoop. uvicorn only selects it for a single, non-reload process,
    # so reload and extra workers are disabled there.
    reload = not is_production and not is_windows
    workers = int(os.environ.get("WEB_CONCURRENCY", "2")) if (is_production and not is_windows) else 1

    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=PORT,
        reload=reload,
        workers=workers,
    )


if __name__ == "__main__":
    main()
