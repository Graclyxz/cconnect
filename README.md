# CConnect

Mobile interface for **Claude Code**. Drive Claude Code running on your PC —
sessions, files, projects, file edits, interactive permission prompts — from an
Android app, locally over Tailscale or publicly over a Tailscale Funnel.

```
[Android app] ──HTTP/WS──> [Backend :8723 on the PC] ──Agent SDK──> [Claude Code]
```

## Structure

```
cconnect/
├── backend/   # FastAPI bridge (Python) — see backend/CLAUDE.md
└── mobile/    # Android app (Jetpack Compose) — see mobile/CLAUDE.md
```

## Run modes

The backend runs in two modes. Both use the same `python run.py` entry.

### Local HTTP (no auth)

```bash
cd backend
python -m venv .venv && source .venv/Scripts/activate
pip install -e .
python run.py
```

- Backend listens on `:8723`, no auth.
- **Requirement:** both PC and phone have **Tailscale** installed and signed
  into the same account. The phone connects using the PC's tailnet IP
  (`100.x.x.x`) shown in the Tailscale app, e.g. `http://100.x.x.x:8723`.
  No funnel needed.

### Public HTTPS (token-gated, exposes the PC over the internet)

```bash
python run.py --expose tailscale
```

What this does:

1. Runs `tailscale up` and `tailscale funnel --bg 8723` to publish the backend
   at `https://<hostname>.<tailnet>.ts.net` (port 443).
2. If `PUBLIC_ACCESS_TOKEN` is unset, generates one and persists it in
   `backend/.env` (reused on subsequent runs).
3. Prints the public URL, the token, and a **scannable QR** encoding
   `{"url":"...","token":"..."}` for the mobile app.
4. Gates every `/api/*` route — except `/api/health` — behind
   `Authorization: Bearer <token>`. The WebSocket handshake checks the same.
5. On `Ctrl+C` (or process exit) runs `tailscale funnel --https=443 off` to
   close the funnel.

**Requirement (PC only):** Tailscale installed, signed in, and **Funnel
enabled for this node** in the tailnet ACL. The phone needs only an internet
connection — no Tailscale required.

## Mobile

Open Settings → Connections → scan QR (top-right of the dialog) to autofill the
connection from `--expose`'s output. The connection becomes active immediately
and the chat reconnects.

## License

MIT — see [LICENSE](LICENSE).
