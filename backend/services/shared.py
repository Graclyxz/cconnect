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


def _count_children(path: Path) -> int:
    try:
        return sum(1 for e in path.iterdir() if not e.name.startswith("."))
    except OSError:
        return 0


def list_entries(relpath: str = "") -> list[dict]:
    target = _resolve(relpath)
    if not target.is_dir():
        raise ValueError("not a directory")
    entries = [
        {
            "name": e.name,
            "is_dir": e.is_dir(),
            "size": stat.st_size,
            "modified": stat.st_mtime,
            "items": _count_children(e) if e.is_dir() else None,
        }
        for e in target.iterdir()
        if not e.name.startswith(".")
        for stat in (e.stat(),)
    ]
    entries.sort(key=lambda f: (not f["is_dir"], -f["modified"]))
    return entries


def resolve_file(relpath: str) -> Optional[Path]:
    path = _resolve(relpath)
    return path if path.is_file() else None


def absolute_paths(relpaths: list[str]) -> list[str]:
    return [str(_resolve(rel)) for rel in relpaths]


async def save_upload(relpath: str, chunks) -> None:
    path = _resolve(relpath)
    if path == _base().resolve() or path.is_dir():
        raise ValueError("invalid destination")
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.parent / f".{path.name}.part"
    try:
        with tmp.open("wb") as fh:
            async for chunk in chunks:
                fh.write(chunk)
        tmp.replace(path)
    finally:
        tmp.unlink(missing_ok=True)


def create_folder(relpath: str) -> None:
    path = _resolve(relpath)
    if path == _base().resolve():
        raise ValueError("invalid folder name")
    if path.exists():
        raise ValueError("already exists")
    path.mkdir(parents=True)


def rename_entry(relpath: str, new_name: str) -> bool:
    if not new_name or "/" in new_name or "\\" in new_name or new_name.startswith("."):
        raise ValueError("invalid name")
    path = _resolve(relpath)
    if path == _base().resolve():
        raise ValueError("cannot rename the shared root")
    if not path.exists():
        return False
    target = path.with_name(new_name)
    if target.exists():
        raise ValueError("already exists")
    path.rename(target)
    return True


def _dedup_target(dest_dir: Path, name: str) -> Path:
    target = dest_dir / name
    if not target.exists():
        return target
    parsed = Path(name)
    stem, suffix = parsed.stem, parsed.suffix
    n = 1
    while True:
        target = dest_dir / f"{stem} ({n}){suffix}"
        if not target.exists():
            return target
        n += 1


def _resolve_transfer(relpaths: list[str], dest: str) -> tuple[list[Path], Path]:
    dest_dir = _resolve(dest)
    if not dest_dir.is_dir():
        raise ValueError("destination is not a folder")
    sources = []
    for rel in relpaths:
        src = _resolve(rel)
        if src == _base().resolve():
            raise ValueError("cannot transfer the shared root")
        if not src.exists():
            continue
        if src.is_dir() and (dest_dir == src or src in dest_dir.parents):
            raise ValueError("cannot transfer a folder into itself")
        sources.append(src)
    return sources, dest_dir


def move_entries(relpaths: list[str], dest: str) -> int:
    sources, dest_dir = _resolve_transfer(relpaths, dest)
    moved = 0
    for src in sources:
        if src.parent == dest_dir:
            continue
        shutil.move(str(src), str(_dedup_target(dest_dir, src.name)))
        moved += 1
    return moved


def copy_entries(relpaths: list[str], dest: str) -> int:
    sources, dest_dir = _resolve_transfer(relpaths, dest)
    copied = 0
    for src in sources:
        target = _dedup_target(dest_dir, src.name)
        if src.is_dir():
            shutil.copytree(src, target)
        else:
            shutil.copy2(src, target)
        copied += 1
    return copied


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
