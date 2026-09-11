"""Unified diffs in the shape the apps render: one `{kind, text}` per line."""

import difflib


def unified(old: str, new: str, path: str) -> list[str]:
    return list(
        difflib.unified_diff(
            (old or "").splitlines(),
            (new or "").splitlines(),
            fromfile=path,
            tofile=path,
            lineterm="",
        )
    )


def classify(lines: list[str]) -> list[dict[str, str]]:
    """`+`/`-` is stripped from text because kind already encodes it."""
    out: list[dict[str, str]] = []
    last_header: str | None = None
    i = 0
    while i < len(lines):
        line = lines[i]
        following = lines[i + 1] if i + 1 < len(lines) else ""
        if line.startswith("---") and following.startswith("+++"):
            old_path = line[3:].strip()
            new_path = following[3:].strip()
            for text in ((new_path,) if old_path == new_path else (old_path, new_path)):
                if text != last_header:
                    out.append({"kind": "header", "text": text})
                    last_header = text
            i += 2
            continue
        if line.startswith("@@"):
            out.append({"kind": "hunk", "text": line})
        elif line.startswith("+"):
            out.append({"kind": "add", "text": line[1:]})
        elif line.startswith("-"):
            out.append({"kind": "del", "text": line[1:]})
        else:
            out.append({"kind": "ctx", "text": line[1:] if line.startswith(" ") else line})
        i += 1
    return out
