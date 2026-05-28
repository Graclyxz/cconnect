# CLAUDE.md — CConnect Backend

Bridge between the CConnect Android app and Claude Code, running locally on the
user's PC. FastAPI + Python 3.11+.

It drives Claude Code through the official **Claude Agent SDK**
(`claude-agent-sdk`), using the user's **subscription** (the logged-in CLI
OAuth), never an API key. The app reaches it either over the local tailnet
(plain HTTP, no auth) or over a Tailscale Funnel (HTTPS, Bearer-token gated).

---

## Architecture

```
[Mobile app] ──WS  /api/chat/ws────> chat router ──> services/claude_runtime.run_prompt ──> SDK query()
             ──REST /api/sessions/* ─> sessions router ──> services/sessions (reads ~/.claude/projects JSONL)
             ──GET  /api/shared/* ──> shared router (download-only from backend/shared/)
```

- **Subscription auth.** `core/sdk.ensure_subscription_auth()` drops
  `ANTHROPIC_API_KEY` so the SDK uses the CLI's OAuth. An exported key silently
  wins and bills API credits.
- **SDK auto-update.** `core/sdk.ensure_sdk_installed()` runs
  `pip install -U claude-agent-sdk` on startup (toggle with `AUTO_UPDATE_SDK=0`).
- **One prompt at a time per WS connection.** Multi-turn is achieved by resuming
  the session id from each `result` event.

## Project Structure

```
backend/
├── main.py                  # FastAPI app; lifespan ensures auth + SDK; router auto-discovery; catch-all 404
├── run.py                   # Uvicorn launcher; --expose tailscale brings up Funnel + token + QR
├── pyproject.toml
├── Dockerfile
├── .env.example
├── core/
│   ├── config.py            # PORT (8723), CLAUDE_PROJECTS_DIR, SHARED_DIR, AUTO_UPDATE_SDK, PUBLIC_ACCESS_TOKEN
│   ├── sdk.py               # Subscription auth + SDK install/upgrade + status
│   ├── responses.py         # api_response() — THE response envelope
│   └── rate_limit.py        # slowapi limiter (configured, not globally enforced)
├── middleware/
│   ├── public_auth.py       # Bearer-token gate; no-op when PUBLIC_ACCESS_TOKEN is None
│   ├── security.py          # Security headers + 25MB request size limit
│   └── error_handler.py     # Routes every error through api_response()
├── routers/
│   ├── health.py            # GET /api/health  (+ SDK status)  — only path open with --expose
│   ├── sessions.py          # GET /api/projects, /api/sessions, /api/sessions/{id}/messages, rename, color, delete
│   ├── shared.py            # GET /api/shared/{path}  — download files from backend/shared/
│   └── chat.py              # WS /api/chat/ws
├── schemas/
│   └── chat.py              # Inbound WebSocket message models
└── services/
    ├── claude_runtime.py    # SDK query() -> normalized event stream
    └── sessions.py          # Read transcripts from ~/.claude/projects (path-traversal safe)
```

## Auth model

| Command | `CCONNECT_AUTH_ACTIVE` env | `PUBLIC_ACCESS_TOKEN` | Result |
|---|---|---|---|
| `python run.py` | unset | `None` | Open backend (no auth) |
| `python run.py --expose tailscale` | `1` | from env / `.env` | Bearer required on every `/api/*` except `/api/health` |

`core/config.PUBLIC_ACCESS_TOKEN` is gated by `CCONNECT_AUTH_ACTIVE` so that a
token sitting in `.env` from a previous expose run doesn't accidentally lock
down a plain local run. The env var propagates to uvicorn's reload subprocess
on every platform, so the gate is consistent across reload and no-reload.

`PublicAuthMiddleware` enforces the gate; `_ws_bearer_ok()` in `routers/chat.py`
validates the same header on the WebSocket handshake before `accept()`.

## `--expose tailscale`

1. `os.environ["CCONNECT_AUTH_ACTIVE"] = "1"` is set in `run.py` before
   `uvicorn.run()` so the worker import of `core.config` sees it.
2. If `PUBLIC_ACCESS_TOKEN` is unset, a fresh `secrets.token_urlsafe(32)` is
   persisted in `.env` and set in `os.environ`.
3. `tailscale funnel --bg <port>` is started.
4. The public URL, port, token, and a QR (`{"url":"...","token":"..."}` JSON) are
   printed to the terminal. The mobile app scans this QR to autoconfigure.
5. `atexit` calls `tailscale funnel --https=443 off` to clean up.

**Tailnet requirements:** Tailscale signed in, Funnel allowed for this node in
the tailnet ACL.

## HTTP API

Every REST endpoint returns `core.responses.api_response()` —
`{success, status, message, data}`.

| Method | Path | Notes |
|---|---|---|
| GET | `/api/health` | Liveness + SDK status. **Open** even with --expose. |
| GET | `/api/capabilities` | Permission modes, effort levels, models, colors |
| GET | `/api/projects` | Claude Code projects under `~/.claude/projects` |
| GET | `/api/sessions?project=<key>` | Sessions in a project (or all when omitted) |
| GET | `/api/sessions/{id}/messages?project=<key>` | Session transcript (see below) |
| POST | `/api/sessions/{id}/rename` | Set a custom title |
| POST | `/api/sessions/{id}/color` | Set the session color |
| POST | `/api/sessions/{id}/auto-rename` | Ask the SDK to generate a title |
| DELETE | `/api/sessions/{id}?project=<key>` | Delete a session |
| GET | `/api/shared/{path:path}` | Download a file from `backend/shared/` |
| GET | `/api/shared` | List entries in `backend/shared/` |

## WebSocket protocol (`/api/chat/ws`)

Client → server (JSON):

- `{"type":"start","cwd":"...","permission_mode":"...","resume":"...","fork":false,"model":"...","effort":"...","partial":false,"base_url":"..."}`
- `{"type":"prompt","text":"..."}`
- `{"type":"set_permission_mode","mode":"..."}`
- `{"type":"interrupt"}`
- `{"type":"interaction_response","id":"...","option_id":"...","free_text":"..."}`

Server → client (JSON):

- `ready` (sessionId), `assistant_text`, `thinking`, `todos`, `task`
- `tool_use` (id, name, input as `"key: value"` per line)
- `tool_result` (tool_use_id, content, is_error)
- **`file_change`** (id, path, diff) — emitted instead of `tool_use` for
  Edit/Write/MultiEdit/NotebookEdit. Built via `difflib.unified_diff` from the
  tool input. The matching `tool_result` is suppressed.
- `interaction_request` (id, kind, options, free_text, title, tool_name, input,
  tool_use_id) — for `AskUserQuestion` and per-tool permission prompts. Paired
  by id with the client's `interaction_response`.
- `system`, `result` (carries `session_id`), `done`, `interrupted`, `error`.

`permission_mode` ∈
`default | acceptEdits | plan | dontAsk | bypassPermissions | auto`.

## Custom MCP server (`mcps/`)

`backend/mcps/` is an in-process MCP server exposed to Claude under the name
`cconnect`. Tools auto-register from sibling modules so adding one is a
single-file drop:

- Each `.py` in `backend/mcps/` exposes a module-level `tools = [<fn>, ...]`.
- `mcps/__init__.build_cconnect_server()` walks the package on startup and
  hands the collected list to `claude_agent_sdk.create_sdk_mcp_server`.
- `services/claude_runtime.run_prompt` wires it into the SDK via
  `ClaudeAgentOptions(mcp_servers={"cconnect": build_cconnect_server()})`.
- Tools surface to Claude as `mcp__cconnect__<tool_name>`.

### Adding a tool

1. Drop `backend/mcps/<your_module>.py`.
2. Decorate the handler with `@tool("name", "description", {"arg": type})`
   from `claude_agent_sdk`. Return
   `{"content": [{"type": "text", "text": "..."}]}` from the handler.
3. Expose `tools = [your_handler]` at module level.
4. Restart the backend — it's picked up automatically.
5. Recommended: add a section to `prompts/agent.md` telling Claude when to
   call it. Without that guidance the SDK often won't reach for it.

### Prompt template (`prompts/agent.md`)

The file is appended verbatim to every session's system prompt by
`_agent_append()`. Two placeholders are substituted at request time:

- `{{SHARED_DIR}}` → absolute path of `backend/shared/`.
- `{{BASE_URL}}` → the request's effective base URL. The phone sends its
  current `base_url` in the WS `start` payload, so the substituted shared
  links always work for whichever device issued the prompt.

### Bundled tools

| Tool | Purpose |
|---|---|
| `check_progress` | Summarizes the latest session of another project into Done / Pending / Files touched / Next step. Runs an isolated SDK subquery in `AI_WORKDIR` with `haiku`. |

## Session transcript transformation

`services/sessions.get_session_messages` reads the JSONL transcript and
normalizes blocks so resume == live:

- `text`, `thinking`, `summary` — passthrough.
- `tool_use` — emitted with `text = _format_tool_input(input)` (same `"key: value"`
  format as live). Special handling:
  - `Edit/Write/MultiEdit/NotebookEdit` → `file_change` with unified diff.
    The matching `tool_result` is dropped.
  - `TodoWrite` and any `Task*` tool → dropped entirely (live shows them as
    transient state, no equivalent in history).
  - `AskUserQuestion` → expanded into one `interaction` message per question,
    with the chosen answer reconstructed by parsing the matching tool_result
    content (Claude Code stores it as `"Q"="A", "Q2"="A2"`).
- `tool_result` — emitted with `_flatten_result_content(...)` (same as live).

## File-edit diff construction (`_build_file_diff`)

| Tool | Input | Diff |
|---|---|---|
| `Edit` | `old_string`, `new_string` | unified_diff(old, new) |
| `MultiEdit` | `edits[]` | concatenated unified_diffs |
| `Write` | `content` | unified_diff("", content) — every line as `+` |
| `NotebookEdit` | `new_source` | unified_diff("", new_source) |

## Configuration

All config lives in environment variables. `core/config.py` auto-loads
`backend/.env` for local dev.

| Var | Default | Notes |
|---|---|---|
| `PORT` | `8723` | Uvicorn bind port |
| `CLAUDE_PROJECTS_DIR` | `~/.claude/projects` | Where Claude Code stores sessions |
| `DEFAULT_PERMISSION_MODE` | `default` | Fallback when `start` omits it |
| `DEFAULT_EFFORT` | `max` | Fallback when `start` omits it |
| `DEFAULT_MODEL` | `opus` | Fallback when `start` omits it |
| `AUTO_UPDATE_SDK` | `1` | `pip install -U claude-agent-sdk` on startup |
| `PUBLIC_ACCESS_TOKEN` | — | Bearer token, honored only when `CCONNECT_AUTH_ACTIVE=1` |

## Reload

`reload=True` is enabled on Linux/macOS only. On Windows uvicorn's reload
worker breaks the asyncio subprocess that Claude CLI spawns
(`CLIConnectionError`), so reload stays off there.

## Conventions

1. **Routers stay thin** — validate input, call a service, return `api_response()`.
2. **Business logic lives in `services/`.** New integrations get their own module.
3. **Imports at the top of the file.** The only exception is the deferred
   `claude_agent_sdk` import inside `services/claude_runtime.py` (the package is
   installed/upgraded at startup, so it may be absent at module-load time).
4. **No secrets in the repo.** Secrets come from env vars / a gitignored `.env`.
