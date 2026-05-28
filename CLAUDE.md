# CConnect

## Project Overview

Mobile interface for Claude Code. The Android app drives Claude Code running on
the user's PC — sessions, files, projects, file edits and interactive permission
prompts — over HTTP/WS, reachable either locally via Tailscale or publicly via
Tailscale Funnel.

The mobile app also bundles a standalone SSH client (saved hosts, embedded
terminal, OS auto-detection) — see `mobile/CLAUDE.md`.

**Monorepo** — backend and mobile are a single project and MUST stay in sync.
Changes to API contracts, event shapes, or schemas have to be reflected in both.

- **Backend**: FastAPI (Python 3.11+ + Pydantic 2 + Uvicorn) — bridge between the
  app and Claude Code via the Agent SDK. Runs on the user's PC.
- **Mobile**: Android (Jetpack Compose, Kotlin 2.2, BOM 2025.09). Connects via
  WebSocket and REST.

See `backend/CLAUDE.md` and `mobile/CLAUDE.md` for module-specific rules.

## Architecture

```
[Android app] ──HTTP/WS──> [Backend :8723] ──claude-agent-sdk──> [Claude Code CLI]
                                │
                                └──> ~/.claude/projects (sessions on disk)
```

- Backend port `8723`, runs on the user's PC.
- Two transport modes:
  - **Local**: both devices on the same tailnet; phone hits `http://<tailnet-host>:8723`.
  - **Public**: `python run.py --expose tailscale` brings up Tailscale Funnel on
    443; phone hits `https://<funnel>.ts.net` with a Bearer token.
- Claude auth: the SDK uses the **Claude Code CLI's OAuth subscription** (no API
  key). `core/sdk.ensure_subscription_auth()` drops `ANTHROPIC_API_KEY` so the
  CLI's session wins.

## Auth model

- Plain `python run.py` → no auth. Open backend on the tailnet.
- `python run.py --expose tailscale` → sets `CCONNECT_AUTH_ACTIVE=1` and a
  `PUBLIC_ACCESS_TOKEN` (auto-generated if absent, persisted in `.env`).
  `core/config` honors the token only when the flag is set, so a leftover
  token in `.env` never accidentally locks down a plain local run.
  `PublicAuthMiddleware` enforces `Authorization: Bearer <token>` for every
  `/api/*` except `/api/health`. The WS handshake checks the same header.

## Development Commands

### Backend
```bash
cd backend && python run.py                    # Local HTTP (no auth)
cd backend && python run.py --expose tailscale # Public HTTPS via Funnel
cd backend && python run.py --production       # Multi-worker (Linux/macOS)
```

### Mobile
```bash
cd mobile && ./gradlew :app:installDebug       # Debug to connected device
cd mobile && ./gradlew :app:assembleRelease    # Signed release APK
```

## Key Rules

1. **Monorepo consistency** — backend and mobile must agree on event types,
   field names, and the QR payload shape (`{url, token}` JSON).
2. **No secrets in the repo** — `.env`, `key.properties`, `keystore.jks` are
   all gitignored.
3. **English only** — all code, comments, docs in English.
4. **Read before acting** — verify before editing; check existing
   conventions/helpers before creating new ones.
