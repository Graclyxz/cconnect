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

## In the chat

Claude Code's own slash commands show up in the composer, plus a `/usage` view
of your plan's token limits. A **quick chat** button opens a side panel for a
throwaway question — handy to ask something while a long task keeps running,
without derailing it.

**Attachments** travel with your message: tap the clip, pick any files or
photos on the phone, and they land on the PC before the prompt runs. Images
reach Claude as real vision input — it sees them, not a path — and other files
arrive as mentions it can open directly.

**Rewind** takes a conversation back to an earlier point. Pick the moment,
preview exactly what would change on disk (`+added −removed • files`), and
choose whether to roll back the conversation alone or code and conversation
together.

Model, effort, permission mode, how much of each turn you see, and which Claude
CLI the backend drives are all set from the app and shared across every client
that connects.

## Files

The shared folder grew into a full file manager. Browse `backend/shared/` from
the phone: upload files (with per-file progress you can cancel), create
folders, rename, sort by name/date/type/size, and long-press to multi-select —
then move, copy, share, delete, save to Downloads, or copy a file's PC path.
Tap a file to preview it in place — images with zoom, HTML rendered in a real
web view, SVG, Markdown, and source code — and delete it right from the
preview if it's no longer needed.

It works in both directions: drop a file into `backend/shared/` on the PC — or
just ask Claude to write one there — and you get a tap-to-download link in the
chat, served over the same authenticated connection.

## Manage Claude Code itself

The **Claude** screen is a remote manager for the Claude Code install on the
PC:

- See the active CLI version, switch where it comes from, update it, and read
  the official changelog without leaving the app.
- Browse any **marketplace** catalog and install **plugins** from the phone;
  enable, disable, update, or uninstall the ones you have.
- Add or remove **MCP servers** and marketplaces.
- Read the **skills** your plugins provide, and view or delete Claude's
  **memories**, globally or per project.
- Keep your own standing instructions in a **user prompt** editable from the
  app — it rides along every conversation, on top of the system conventions in
  `backend/prompts/CCONNECT.md`.
- See your **plan usage** at a glance — your subscription tier (Pro, Max 5x,
  Max 20x) and a bar per limit window: current session, all models, and the
  per-model weekly caps, each with its reset time.

## Watch the PC

The **Monitor** screen shows what the machine is doing while Claude works:
CPU, GPU and memory as live graphs (VRAM and temperature included when
there's an NVIDIA card), storage per disk, the server's own logs streaming
in over a dedicated WebSocket, and a device card — OS with its brand icon,
hostname, uptime, CPU/GPU models. Swipe between resources and logs, switch
servers right from the top bar, and **restart the backend remotely** — one
confirmation, the server relaunches itself and the app reconnects on its
own.

## Built-in features

On top of plain Claude Code, the backend ships a few helpers wired into the
agent so they're available out of the box from the phone:

- **Cross-project progress check.** Ask things like _"how's the README I left
  running in <other-project> going?"_ and Claude summarizes that other
  session's latest activity into Done / Pending / Files touched / Next step,
  without you having to open it.
- **Editable system prompt.** `backend/prompts/CCONNECT.md` is auto-appended to
  every chat — that's where the file-sharing and progress-check conventions
  live. Your personal additions go in `USER.md`, edited straight from the
  Claude screen.

## Staying current

The app and the backend declare which versions of each other — and of the
Claude CLI — they support. When something falls behind, a notice in the chat
takes you straight to the right place: the app's own update (with its
changelog), the server requirement, or the CLI update button. Release notes
for both CConnect and Claude Code are readable in the app.

## SSH client

The mobile app also bundles a lightweight SSH client. Open Settings → SSH
hosts to save a target — its address, the SSH port (`22` unless the server
listens somewhere else), and the credentials you log in with — then tap it
to open an embedded terminal. On Linux that's your shell user + password;
on Windows hosts running OpenSSH it's your account password. Password auth
must be enabled on the target. A keepalive and a Wi-Fi lock keep the session
connected when the screen turns off.

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
