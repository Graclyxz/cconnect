# CLAUDE.md — CConect Backend

Bridge between the CConect mobile app and Claude Code, running locally on the
user's PC. Backend only — FastAPI + Python 3.11.

The core (response envelope, config pattern, security middleware, Dockerized deploy)
was lifted from the `telegramleadsbot` skeleton.

> **Status:** base skeleton. The Claude Code integration (Agent SDK, sessions,
> streaming, MCP) and the app authentication are not implemented yet.

---

## Project Structure

```
backend/
├── main.py                  # FastAPI app: middleware, error handlers, router auto-discovery, catch-all 404
├── run.py                   # Uvicorn launcher (honors $PORT, $WEB_CONCURRENCY)
├── pyproject.toml           # Dependencies
├── Dockerfile               # Image build
├── .env.example             # Template — copy to .env and fill for local dev
├── core/
│   ├── config.py            # Reads all config from env vars (.env auto-loaded for local dev)
│   ├── responses.py         # api_response() — THE response envelope
│   └── rate_limit.py        # slowapi limiter (configured, not globally enforced)
├── middleware/
│   ├── error_handler.py     # Routes every error through api_response()
│   └── security.py          # Security headers + 25MB request size limit
├── routers/
│   └── health.py            # GET /api/health
└── schemas/                 # Pydantic models (empty for now)
```

## API Response Envelope

Every JSON endpoint returns `core.responses.api_response()`. Never return a raw dict.

```json
{ "success": true, "status": 200, "message": "OK", "data": { } }
```

`data` is omitted when `None`. Default messages are **English** (derived from
`HTTPStatus`). Errors flow through the handlers in `middleware/error_handler.py`.

## Configuration

All config lives in **environment variables** — no `ctes.py`, no templating layer.
`core/config.py` reads them via `os.environ`, auto-loading a root `.env` (python-dotenv)
for local dev.

### Environment Variables

| Var | Example | Notes |
|---|---|---|
| `PORT` | `8000` | Uvicorn bind port |

> Bridge and app-auth variables are added here as the Claude Code integration lands.

## Local Development

```bash
python -m venv .venv && source .venv/Scripts/activate   # Windows bash
pip install -e .
cp .env.example .env          # fill in the values
python run.py                 # http://localhost:8000 (reload on)
```

`python run.py --production` runs without reload across multiple workers.

## Conventions

1. **Routers stay thin** — validate input, call a service, return `api_response()`.
2. **Business logic lives in `services/`** (created when the Claude Code integration
   lands). New integrations get their own module there.
3. **Imports at the top of the file**, never inline; never fully-qualified class names inline.
4. **No secrets in the repo.** Secrets come from env vars / a gitignored `.env`;
   `core/config.py` only holds dev fallbacks.
