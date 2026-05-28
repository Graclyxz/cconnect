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

## Built-in features

On top of plain Claude Code, the backend ships a few helpers wired into the
agent so they're available out of the box from the phone:

- **Shared folder.** Drop a file into `backend/shared/` — or just ask Claude to
  write one there — and you get a tap-to-download link in the chat. The phone
  lets you save it to Downloads, save as somewhere else, or share to another
  app, all over the same authenticated connection.
- **Cross-project progress check.** Ask things like _"how's the README I left
  running in <other-project> going?"_ and Claude summarizes that other
  session's latest activity into Done / Pending / Files touched / Next step,
  without you having to open it.
- **Editable system prompt.** `backend/prompts/agent.md` is auto-appended to
  every chat — that's where the file-sharing and progress-check conventions
  live. Edit it to teach Claude your own conventions or to add guidance for
  new tools.

## SSH client

The mobile app also bundles a lightweight SSH client. Open Settings → SSH
hosts to save a target — its address, the SSH port (`22` unless the server
listens somewhere else), and the credentials you log in with — then tap it
to open an embedded terminal. On Linux that's your shell user + password;
on Windows hosts running OpenSSH it's your account password. Password auth
must be enabled on the target.

### Local

Phone and target on the same network — use the target's LAN IP or
hostname. No extra setup.

### Remote (via Tailscale)

Install Tailscale on both the phone and the target machine, sign into the
same account, and use the target's tailnet IP (`100.x.x.x`) shown in the
Tailscale app. The port stays the same (`22`); no port forwarding, no VPN
setup.

## License

MIT — see [LICENSE](LICENSE).
