"""Claude service status from status.claude.com (an Atlassian Statuspage instance)."""

from __future__ import annotations

import logging
import time

import httpx

logger = logging.getLogger(__name__)

_SUMMARY_URL = "https://status.claude.com/api/v2/summary.json"
_TTL = 60.0
_cache: dict | None = None
_cache_at = 0.0


async def _fetch() -> dict:
    async with httpx.AsyncClient(timeout=8.0) as client:
        resp = await client.get(_SUMMARY_URL, headers={"User-Agent": "cconnect"})
    resp.raise_for_status()
    return resp.json()


def _normalize(data: dict) -> dict:
    status = data.get("status") or {}
    components = [
        {"name": c.get("name", ""), "status": c.get("status", "operational")}
        for c in data.get("components", [])
        if isinstance(c, dict) and not c.get("group") and c.get("name")
    ]
    incidents = []
    for inc in data.get("incidents", []):
        if not isinstance(inc, dict):
            continue
        updates = inc.get("incident_updates") or []
        latest_update = updates[0] if updates and isinstance(updates[0], dict) else None
        incidents.append({
            "name": inc.get("name", ""),
            "impact": inc.get("impact", "none"),
            "status": inc.get("status", ""),
            "latest": latest_update.get("body") if latest_update else None,
            "updated_at": (latest_update.get("display_at") or latest_update.get("created_at"))
            if latest_update else inc.get("updated_at"),
            "shortlink": inc.get("shortlink"),
        })
    return {
        "indicator": status.get("indicator", "none"),
        "description": status.get("description", ""),
        "updated_at": (data.get("page") or {}).get("updated_at"),
        "components": components,
        "incidents": incidents,
    }


async def service_status() -> dict:
    global _cache, _cache_at
    now = time.monotonic()
    if _cache is not None and now - _cache_at < _TTL:
        return _cache
    try:
        result = _normalize(await _fetch())
    except (httpx.HTTPError, ValueError) as exc:
        logger.debug(f"status fetch failed: {type(exc).__name__}: {exc}")
        return {"error": f"Couldn't fetch status: {type(exc).__name__}"}
    _cache = result
    _cache_at = now
    return result
