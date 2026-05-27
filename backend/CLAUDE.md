# CLAUDE.md — CConect Backend

Bridge between the CConect mobile app and Claude Code, running locally on the
user's PC. Backend only — FastAPI + Python 3.11.

It drives Claude Code through the official **Claude Agent SDK** (`claude-agent-sdk`),
using the user's **subscription** (the logged-in Claude Code CLI OAuth), not an API
key. The app reaches it over **Tailscale**; there is no extra app-level auth.

The core (response envelope, config pattern, security middleware, Dockerized deploy)
was lifted from the `telegramleadsbot` skeleton.

---

## Architecture

```
[Mobile app] ──WS /api/chat/ws──> chat router ──> services/claude_runtime.run_prompt ──> SDK query()
             ──REST /api/*───────> sessions router ──> services/sessions (reads ~/.claude/projects JSONL)
```

- **Subscription auth.** On startup `core/sdk.ensure_subscription_auth()` drops
  `ANTHROPIC_API_KEY` so the SDK uses the CLI OAuth (subscription quota). An exported
  key silently wins and bills API credits.
- **SDK auto-update.** On startup `core/sdk.ensure_sdk_installed()` runs
  `pip install -U claude-agent-sdk` (toggle with `AUTO_UPDATE_SDK`).
- **One prompt at a time per WS connection.** Multi-turn is achieved by resuming the
  session id returned in each `result` event; permission mode is configurable per
  session and changeable mid-connection.

## Project Structure

```
backend/
├── main.py                  # FastAPI app; lifespan ensures auth + SDK; router auto-discovery; catch-all 404
├── run.py                   # Uvicorn launcher (port from core.config, honors $WEB_CONCURRENCY)
├── pyproject.toml           # Dependencies
├── Dockerfile               # Image build
├── .env.example             # Template — copy to .env and fill for local dev
├── core/
│   ├── config.py            # PORT (8723), CLAUDE_PROJECTS_DIR, permission modes, AUTO_UPDATE_SDK
│   ├── sdk.py               # Subscription auth + SDK install/upgrade + status
│   ├── responses.py         # api_response() — THE response envelope
│   └── rate_limit.py        # slowapi limiter (configured, not globally enforced)
├── middleware/
│   ├── error_handler.py     # Routes every error through api_response()
│   └── security.py          # Security headers + 25MB request size limit
├── routers/
│   ├── health.py            # GET /api/health  (+ SDK status)
│   ├── sessions.py          # GET /api/projects, /api/sessions, /api/sessions/{id}/messages
│   └── chat.py              # WS /api/chat/ws
├── schemas/
│   └── chat.py              # Inbound WebSocket message models
└── services/
    ├── claude_runtime.py    # query() -> normalized event stream
    └── sessions.py          # Read projects & transcripts from ~/.claude/projects (path-traversal safe)
```

## HTTP API

Every REST endpoint returns `core.responses.api_response()` — `{success, status, message, data}`.

| Method | Path | Notes |
|---|---|---|
| GET | `/api/health` | Liveness + installed SDK version |
| GET | `/api/projects` | Claude Code projects under `~/.claude/projects` |
| GET | `/api/sessions?project=<key>` | Sessions in a project |
| GET | `/api/sessions/{session_id}/messages?project=<key>` | Session transcript |

## WebSocket protocol (`/api/chat/ws`)

Client → server (JSON):

- `{"type":"start","cwd":"C:\\DEV\\proj","permission_mode":"default","resume":null,"fork":false,"model":null}`
- `{"type":"prompt","text":"..."}`
- `{"type":"set_permission_mode","mode":"bypassPermissions"}`
- `{"type":"interrupt"}`

Server → client (JSON): `ready`, `assistant_text`, `thinking`, `tool_use`,
`tool_result`, `system`, `result` (carries `session_id`), `done`, `interrupted`, `error`.

`permission_mode` ∈ `default | acceptEdits | plan | dontAsk | bypassPermissions`
(`bypassPermissions` == `claude --dangerously-skip-permissions`).

## Configuration

All config lives in **environment variables**. `core/config.py` reads them via
`os.environ`, auto-loading a root `.env` (python-dotenv) for local dev.

| Var | Default | Notes |
|---|---|---|
| `PORT` | `8723` | Uvicorn bind port |
| `CLAUDE_PROJECTS_DIR` | `~/.claude/projects` | Where Claude Code stores sessions |
| `DEFAULT_PERMISSION_MODE` | `default` | Fallback when a `start` omits it |
| `AUTO_UPDATE_SDK` | `1` | `pip install -U claude-agent-sdk` on startup |

## Local Development

```bash
python -m venv .venv && source .venv/Scripts/activate   # Windows bash
pip install -e .
cp .env.example .env
python run.py                 # http://localhost:8723 (reload on)
```

Requires the Claude Code CLI to be logged in (subscription) on this machine.
`python run.py --production` runs without reload across multiple workers.

## Conventions

1. **Routers stay thin** — validate input, call a service, return `api_response()`.
2. **Business logic lives in `services/`.** New integrations get their own module.
3. **Imports at the top of the file.** The only exception is the deferred
   `claude_agent_sdk` import inside `services/claude_runtime.py` (the package is
   installed/upgraded at startup, so it may be absent at module-load time).
4. **No secrets in the repo.** Secrets come from env vars / a gitignored `.env`.
