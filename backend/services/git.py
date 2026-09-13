"""Every git invocation the backend makes.

Reads run with `--no-optional-locks`: a plain `git status` otherwise rewrites `.git/index`,
which the project watcher reports as a change and answers with another read, forever.
"""

import os
import subprocess
from pathlib import Path

_NO_PROMPT = {"GIT_TERMINAL_PROMPT": "0", "GIT_ASKPASS": "", "SSH_ASKPASS": ""}
_READ_ONLY = ("--no-optional-locks",)


def run(root: Path, *args: str, timeout: int, writes: bool = False) -> subprocess.CompletedProcess:
    prefix = () if writes else _READ_ONLY
    return subprocess.run(
        ["git", *prefix, "-C", str(root), *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=timeout,
        stdin=subprocess.DEVNULL,
        env={**os.environ, **_NO_PROMPT},
    )
