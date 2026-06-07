"""Uvicorn server launcher. Use --production for no-reload multi-worker mode."""

import argparse
import atexit
import io
import json
import os
import re
import secrets
import subprocess
import sys
from pathlib import Path
from urllib.parse import urlparse

import qrcode
import uvicorn

from core.config import PORT
from services import system_monitor

_ENV_PATH = Path(__file__).resolve().parent / ".env"
_TOKEN_VAR = "PUBLIC_ACCESS_TOKEN"


def _abort(msg: str) -> None:
    sys.stderr.write(msg.rstrip() + "\n")
    sys.exit(1)


def _persist_token_in_env(token: str) -> str:
    if not _ENV_PATH.exists():
        _ENV_PATH.write_text(f"{_TOKEN_VAR}={token}\n", encoding="utf-8")
        return "created .env"

    lines = _ENV_PATH.read_text(encoding="utf-8").splitlines()
    pattern = re.compile(rf"^\s*{re.escape(_TOKEN_VAR)}\s*=\s*(.*)$")
    for i, line in enumerate(lines):
        match = pattern.match(line)
        if match:
            lines[i] = f"{_TOKEN_VAR}={token}"
            _ENV_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")
            return "filled empty entry in .env"

    if lines and lines[-1] != "":
        lines.append("")
    lines.append(f"{_TOKEN_VAR}={token}")
    _ENV_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return "appended entry to .env"


def _ensure_public_token() -> bool:
    """Make sure a Bearer token exists for public exposure. Returns True if just generated."""
    # Env var (not module mutation) so uvicorn's reload subprocess inherits the state.
    os.environ["CCONNECT_AUTH_ACTIVE"] = "1"
    if os.environ.get(_TOKEN_VAR):
        return False

    token = secrets.token_urlsafe(32)
    _persist_token_in_env(token)
    os.environ[_TOKEN_VAR] = token
    return True


def _start_tailscale_funnel(port: int) -> str:
    """Start Tailscale Funnel in the background and return the public URL."""
    try:
        subprocess.run(["tailscale", "up"], capture_output=True, text=True, timeout=60)
        result = subprocess.run(
            ["tailscale", "funnel", "--bg", str(port)],
            capture_output=True, text=True, check=True, timeout=20,
        )
    except FileNotFoundError:
        _abort("tailscale CLI not found in PATH. Install Tailscale and try again.")
    except subprocess.CalledProcessError as exc:
        _abort(f"tailscale funnel failed:\n{exc.stderr or exc.stdout}")

    output = (result.stdout or "") + (result.stderr or "")
    match = re.search(r"https://[^\s]+\.ts\.net/?", output)
    if not match:
        _abort(f"could not parse public URL from tailscale output:\n{output}")
    return match.group(0).rstrip("/")


def _stop_tailscale_funnel() -> None:
    """Best-effort shutdown of the background funnel on exit."""
    try:
        subprocess.run(
            ["tailscale", "funnel", "--https=443", "off"],
            capture_output=True, timeout=10,
        )
    except Exception:
        pass


def _print_qr(payload: str) -> None:
    qr = qrcode.QRCode(border=1)
    qr.add_data(payload)
    qr.make(fit=True)
    buf = io.StringIO()
    qr.print_ascii(out=buf, invert=True)
    sys.stdout.buffer.write(buf.getvalue().encode("utf-8"))
    sys.stdout.buffer.flush()


def _expose(provider: str, port: int) -> None:
    generated = _ensure_public_token()
    if provider == "tailscale":
        public_url = _start_tailscale_funnel(port)
        atexit.register(_stop_tailscale_funnel)
    else:
        _abort(f"unknown --expose provider: {provider}")
        return  # unreachable, satisfies static checkers
    token = os.environ[_TOKEN_VAR]
    token_tag = " [Auto]" if generated else ""
    parsed = urlparse(public_url)
    pub_port = parsed.port or (443 if parsed.scheme == "https" else 80)
    print(
        f"\n  Public URL : {public_url}"
        f"\n  Provider   : {provider}"
        f"\n  Port       : {pub_port}"
        f"\n  Token      : {token}{token_tag}\n"
    )
    _print_qr(json.dumps({"url": public_url, "token": token}, separators=(",", ":")))


def main():
    parser = argparse.ArgumentParser(description="CConnect backend launcher.")
    parser.add_argument("--production", action="store_true",
                        help="No reload, multi-worker (Linux/macOS only).")
    parser.add_argument("--expose", choices=["tailscale"], default=None,
                        help="Expose the backend to the public internet via the given provider.")
    args = parser.parse_args()

    is_windows = sys.platform == "win32"

    system_monitor.reset_log_file()
    if args.expose:
        _expose(args.expose, PORT)

    # Disabled on Windows: uvicorn's reload worker breaks the Claude CLI's asyncio subprocess.
    reload = not args.production and not is_windows
    workers = int(os.environ.get("WEB_CONCURRENCY", "2")) if (args.production and not is_windows) else 1

    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=PORT,
        reload=reload,
        workers=workers,
    )


if __name__ == "__main__":
    main()
