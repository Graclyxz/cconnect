"""Download-only file serving from the shared drop folder (PC -> phone)."""

from pathlib import Path
from typing import Optional

from core.config import SHARED_DIR


def _base() -> Path:
    base = Path(SHARED_DIR)
    base.mkdir(parents=True, exist_ok=True)
    return base


def list_files() -> list[dict]:
    base = _base()
    items = [
        {"name": entry.name, "size": stat.st_size, "modified": stat.st_mtime}
        for entry in base.iterdir()
        if entry.is_file() and not entry.name.startswith(".")
        for stat in (entry.stat(),)
    ]
    items.sort(key=lambda f: f["modified"], reverse=True)
    return items


def resolve_file(name: str) -> Optional[Path]:
    base = _base()
    path = (base / name).resolve()
    if path.parent != base.resolve():
        raise ValueError("name escapes the shared directory")
    if not path.is_file():
        return None
    return path
