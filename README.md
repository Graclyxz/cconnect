# CConect

Remote interface for **Claude Code** from your phone. Control Claude Code running
on your home PC — with its sessions, files and projects — from a mobile app,
reachable from any network via Tailscale.

## Structure

```
cconect/
├── backend/   # FastAPI bridge (Python) between the app and Claude Code — see backend/CLAUDE.md
└── mobile/    # Android app (interface) — TBD
```

## Architecture

```
[Mobile app] <--Tailscale (HTTP/WS + SSH)--> [Backend :8000 on the PC] <--Agent SDK--> [Claude Code]
```

## Backend — quick start

```bash
cd backend
python -m venv .venv && source .venv/Scripts/activate   # Windows bash
pip install -e .
cp .env.example .env
python run.py                 # http://localhost:8000
```

See `CLAUDE.md` (root) and `backend/CLAUDE.md` for details.
