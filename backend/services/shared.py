"""File serving from the shared drop folder (PC <-> phone), with subfolders."""

import shutil
from pathlib import Path
from typing import Optional

from core.config import SHARED_DIR


def _base() -> Path:
    base = Path(SHARED_DIR)
    base.mkdir(parents=True, exist_ok=True)
    return base


def _resolve(relpath: str) -> Path:
    base = _base().resolve()
    path = (base / relpath).resolve()
    if path != base and base not in path.parents:
        raise ValueError("path escapes the shared directory")
    return path


def list_entries(relpath: str = "") -> list[dict]:
    target = _resolve(relpath)
    if not target.is_dir():
        raise ValueError("not a directory")
    entries = [
        {"name": e.name, "is_dir": e.is_dir(), "size": stat.st_size, "modified": stat.st_mtime}
        for e in target.iterdir()
        if not e.name.startswith(".")
        for stat in (e.stat(),)
    ]
    entries.sort(key=lambda f: (not f["is_dir"], -f["modified"]))
    return entries


def resolve_file(relpath: str) -> Optional[Path]:
    path = _resolve(relpath)
    return path if path.is_file() else None


def delete_entry(relpath: str) -> bool:
    path = _resolve(relpath)
    if path == _base().resolve():
        raise ValueError("cannot delete the shared root")
    if path.is_dir():
        shutil.rmtree(path)
        return True
    if path.is_file():
        path.unlink()
        return True
    return False
