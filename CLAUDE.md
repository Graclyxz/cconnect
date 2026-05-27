# CConect

## Project Overview

Remote interface for Claude Code from the phone. Lets you control Claude Code
running on your home PC — with its sessions, files and projects — from a mobile
app, reachable from any network via Tailscale.

**Monorepo** — backend and mobile are a single project and MUST stay in sync.
Changes to API contracts, schemas or response formats must be reflected in both.

- **Backend**: FastAPI (Python 3.11 + Pydantic 2 + Uvicorn) — local bridge between
  the app and Claude Code (Agent SDK), runs on the user's PC.
- **Mobile**: Android app (interface: markdown rendering, sessions, SSH terminal).
  Stack TBD — empty folder for now.

See `backend/CLAUDE.md` for detailed backend rules. (mobile: TBD)

## Architecture

```
[Mobile app] <--Tailscale (HTTP/WS + SSH)--> [Backend :8000 on the PC] <--Agent SDK--> [Claude Code + local projects]
```

- Backend: port 8000, runs on the user's PC.
- Transport: Tailscale (private network, no router port-forwarding).
- Claude auth: subscription via the Claude Code CLI login (SDK native OAuth).
- Terminal and files: separate SSH/SFTP channel, also over Tailscale.

## Development Commands

### Backend
```bash
cd backend && python run.py              # Dev server on :8000
cd backend && python run.py --production # Production (multi-worker)
```

### Mobile
```bash
# TBD — Android app stack not defined yet.
```

## Key Rules
1. **Read before acting** - Verify values, column/class names before using them
2. **Never guess** - Check actual code, config or data
3. **Reuse before creating** - Check whether a component/function already exists
4. **English only** - All code, comments and docs are written in English
5. **Monorepo consistency** - Backend and mobile must agree on API contracts
6. **No secrets in the repo** - Secrets via env vars / a gitignored `.env`
