# CConnect

## Project Overview

Mobile, desktop and web interface for Claude Code. The apps drive Claude Code
running on the user's PC — sessions, files, projects, file edits, interactive
permission prompts, chat attachments, rewind — over HTTP/WS, reachable either
locally via Tailscale or publicly via Tailscale Funnel. They also remote-manage
the Claude Code installation itself (CLI version/updates, plugins, marketplaces,
MCP servers, skills, memories, user prompt), ship a full file manager over the
backend's shared folder, and a live PC monitor (CPU/GPU/memory/disks + server
logs).

The mobile and desktop apps also bundle a standalone SSH client (saved hosts,
embedded terminal, OS auto-detection) — see `mobile/CLAUDE.md` / `client/CLAUDE.md`.

**Monorepo** — backend, mobile and client are a single project and MUST stay in
sync. Changes to API contracts, event shapes, or schemas have to be reflected
everywhere. The mobile (Android) and client (desktop/web) apps mirror each other
1:1 — a feature that applies to both lands in both.

- **Backend**: FastAPI (Python 3.11+ + Pydantic 2 + Uvicorn) — bridge between the
  apps and Claude Code via the Agent SDK. Runs on the user's PC. Serves all
  clients identically.
- **Mobile**: Android (Jetpack Compose, Kotlin 2.2, BOM 2025.09). Connects via
  WebSocket and REST.
- **Client**: Compose Multiplatform (Kotlin) — one codebase building a native
  **desktop** app (Windows/Linux/macOS) and a **web** app (WebAssembly, hosted on
  Cloudflare Pages). Same WebSocket/REST contract.

See `backend/CLAUDE.md`, `mobile/CLAUDE.md` and `client/CLAUDE.md` for
module-specific rules.

## Architecture

```
[Android / desktop / web client] ──HTTP/WS──> [Backend :8723] ──claude-agent-sdk──> [Claude Code CLI]
                                │
                                ├──> ~/.claude/projects (sessions on disk)
                                ├──> ~/.claude (plugins, marketplaces, MCP, skills, memories — read + `claude` CLI subprocess for mutations)
                                └──> backend/shared/ (file manager + chat attachment uploads)
```

- Backend port `8723`, runs on the user's PC.
- Two transport modes:
  - **Local**: both devices on the same tailnet; phone hits `http://<tailnet-host>:8723`.
  - **Public**: `python run.py --expose tailscale` brings up Tailscale Funnel on
    443; phone hits `https://<funnel>.ts.net` with a Bearer token.
- Claude auth: the SDK uses the **Claude Code CLI's OAuth subscription** (no API
  key). `core/sdk.ensure_subscription_auth()` drops `ANTHROPIC_API_KEY` so the
  CLI's session wins.

## Version contract

Three-way compatibility, declared in `backend/pyproject.toml`:

- `[project] version` — the server version.
- `[tool.cconnect] supported-app` — minimum mobile app version the server accepts.
- `[tool.cconnect] supported-cli` — minimum Claude CLI version the backend's
  features are validated against.

`GET /api/health` and `GET /api/capabilities` expose `version`,
`supported_app`, `cli_version`, `supported_cli`. The app compares them against
its own `versionName` / `BuildConfig.SUPPORTED_SERVER` and surfaces
AppOutdated / ServerOutdated / CliOutdated notices. **Bump these together**
when a change requires a newer counterpart.

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
cd mobile && ./gradlew :app:installRelease     # Signed release to connected device
cd mobile && ./gradlew :app:assembleRelease    # Signed release APK
```

### Client (desktop + web)
```bash
cd client && ./gradlew :app:run                                 # Run desktop
cd client && ./gradlew :app:wasmJsBrowserDevelopmentRun         # Run web
cd client && ./gradlew :app:compileKotlinDesktop :app:compileKotlinWasmJs  # Compile-check both
cd client && ./gradlew :app:packageDistributionForCurrentOS     # Desktop installers
cd client && ./gradlew :app:wasmJsBrowserDistribution           # Web static bundle (→ Cloudflare Pages in CI)
```
Releases are cut by `.github/workflows/release.yml` on a `x.y.z` tag: desktop
installers, the Android APK, and the web deploy to Cloudflare Pages all ship
together.

## Key Rules

1. **Monorepo consistency** — backend, mobile and client must agree on event
   types, field names, and the QR payload shape (`{url, token}` JSON). Mobile and
   client mirror each other (same screens/models); apply applicable features to both.
2. **Version contract** — when a feature needs a newer app/server/CLI, update
   `version` / `supported-app` / `supported-cli` in `backend/pyproject.toml`
   and the app's `versionName` / `SUPPORTED_SERVER` accordingly.
3. **No secrets in the repo** — `.env`, `key.properties`, `keystore.jks` are
   all gitignored. `backend/prompts/USER.md` is user-owned and gitignored too.
4. **English only** — all code, comments, docs in English.
5. **Read before acting** — verify before editing; check existing
   conventions/helpers before creating new ones.
