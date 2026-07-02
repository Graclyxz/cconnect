"""Bearer-token gate for the public surface. No-op when PUBLIC_ACCESS_TOKEN is unset."""

import hmac

from fastapi import FastAPI, Request, WebSocket
from starlette.middleware.base import BaseHTTPMiddleware

from core.config import PUBLIC_ACCESS_TOKEN
from core.responses import api_response

# The /admin shell is static and secret-free; the admin API under /api/admin/* has
# its own token (routers/admin.require_admin), so both bypass the PUBLIC_ACCESS_TOKEN gate.
_OPEN_PATHS = frozenset({
    "/api/health",
    "/admin",
    "/admin/admin.css",
    "/admin/admin.js",
})
_OPEN_PREFIXES = ("/api/admin/",)


def ws_bearer_ok(ws: WebSocket) -> bool:
    if PUBLIC_ACCESS_TOKEN is None:
        return True
    scheme, _, value = ws.headers.get("authorization", "").partition(" ")
    token = value.strip() if scheme.lower() == "bearer" else ws.query_params.get("token", "")
    return hmac.compare_digest(token, PUBLIC_ACCESS_TOKEN)


def _extract_bearer(request: Request) -> str | None:
    header = request.headers.get("authorization")
    if not header:
        return None
    scheme, _, value = header.partition(" ")
    if scheme.lower() != "bearer":
        return None
    return value.strip() or None


class PublicAuthMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        if PUBLIC_ACCESS_TOKEN is None:
            return await call_next(request)
        if request.url.path in _OPEN_PATHS or request.url.path.startswith(_OPEN_PREFIXES):
            return await call_next(request)

        provided = _extract_bearer(request)
        if provided is None or not hmac.compare_digest(provided, PUBLIC_ACCESS_TOKEN):
            return api_response(status=401)

        return await call_next(request)


def register_public_auth_middleware(app: FastAPI):
    app.add_middleware(PublicAuthMiddleware)
