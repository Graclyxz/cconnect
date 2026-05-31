"""Plan token usage (5-hour and weekly windows) as a markdown report."""

from __future__ import annotations

import json
import logging
from datetime import datetime, timezone
from pathlib import Path

import httpx

from core.config import CLAUDE_PROJECTS_DIR

logger = logging.getLogger(__name__)

_USAGE_URL = "https://api.anthropic.com/api/oauth/usage"
_CREDENTIALS = Path(CLAUDE_PROJECTS_DIR).parent / ".credentials.json"
_WINDOWS = (
    ("five_hour", "5-hour"),
    ("seven_day", "Weekly"),
    ("seven_day_opus", "Weekly (Opus)"),
    ("seven_day_sonnet", "Weekly (Sonnet)"),
)
_BAR_WIDTH = 20


def _oauth_token() -> str | None:
    try:
        data = json.loads(_CREDENTIALS.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    return (data.get("claudeAiOauth") or {}).get("accessToken")


def _bar(pct: float) -> str:
    filled = max(0, min(_BAR_WIDTH, round(pct / 100 * _BAR_WIDTH)))
    return "█" * filled + "░" * (_BAR_WIDTH - filled)


def _resets_hint(value) -> str:
    """Format a reset time (epoch or ISO-8601) as 'resets in Xh Ym'."""
    dt = None
    try:
        if isinstance(value, (int, float)):
            dt = datetime.fromtimestamp(value, tz=timezone.utc)
        elif isinstance(value, str) and value:
            dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except (ValueError, OSError, OverflowError):
        return ""
    if dt is None:
        return ""
    secs = (dt - datetime.now(timezone.utc)).total_seconds()
    if secs <= 0:
        return "resets now"
    d, rem = divmod(int(secs), 86400)
    h, m = rem // 3600, (rem % 3600) // 60
    if d:
        return f"resets in {d}d {h}h"
    if h:
        return f"resets in {h}h {m}m"
    return f"resets in {m}m"


def _window_md(label: str, win: dict) -> str | None:
    if not isinstance(win, dict):
        return None
    pct = win.get("utilization")
    if not isinstance(pct, (int, float)):
        return None
    lines = [f"**{label}** • {round(pct)}%", f"`{_bar(pct)}`"]
    hint = _resets_hint(win.get("resets_at"))
    if hint:
        lines.append(hint)
    return "  \n".join(lines)


async def usage_markdown() -> str:
    """Fetch plan usage and render it as markdown with per-window utilization bars."""
    token = _oauth_token()
    if not token:
        return "_No Claude token found (are you signed in to the CLI?)._"
    headers = {
        "Authorization": f"Bearer {token}",
        "anthropic-beta": "oauth-2025-04-20",
        "anthropic-version": "2023-06-01",
        "User-Agent": "cconnect",
    }
    try:
        async with httpx.AsyncClient(timeout=8.0) as client:
            resp = await client.get(_USAGE_URL, headers=headers)
        resp.raise_for_status()
        data = resp.json()
    except httpx.HTTPStatusError as exc:
        code = exc.response.status_code
        hint = " (sign in to the CLI again)" if code in (401, 403) else ""
        return f"_Couldn't fetch usage: {code}{hint}._"
    except (httpx.HTTPError, json.JSONDecodeError) as exc:
        logger.debug(f"usage fetch failed: {type(exc).__name__}: {exc}")
        return f"_Couldn't fetch usage: {type(exc).__name__}._"

    rows = [md for key, label in _WINDOWS if (md := _window_md(label, data.get(key))) is not None]
    if not rows:
        return "_The server returned no usage data._"
    return "**Token usage**\n\n" + "\n\n".join(rows)
