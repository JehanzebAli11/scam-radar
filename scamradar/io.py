"""Reading and writing message records (JSONL and CSV).

A record is a dict with at least ``text``; labelled records also carry
``label`` (a compact schema line). Optional keys: ``id``, ``tags``, ``lang``,
``category`` and provenance fields.
"""

from __future__ import annotations

import csv
import json
import os
from collections.abc import Iterable, Iterator
from pathlib import Path
from typing import Any

from scamradar.paths import ensure_not_locked

Record = dict[str, Any]


def _iter_jsonl(path: Path) -> Iterator[Record]:
    with path.open(encoding="utf-8") as fh:
        for lineno, raw in enumerate(fh, 1):
            raw = raw.strip()
            if not raw:
                continue
            try:
                obj = json.loads(raw)
            except json.JSONDecodeError as exc:
                raise ValueError(f"{path}:{lineno}: invalid JSON: {exc}") from exc
            if not isinstance(obj, dict):
                raise ValueError(f"{path}:{lineno}: expected a JSON object")
            yield obj


def _iter_csv(path: Path) -> Iterator[Record]:
    with path.open(encoding="utf-8", newline="") as fh:
        for row in csv.DictReader(fh):
            rec: Record = {k: v for k, v in row.items() if k is not None and v not in (None, "")}
            if "tags" in rec:
                rec["tags"] = [t.strip() for t in str(rec["tags"]).split(";") if t.strip()]
            yield rec


def read_records(path: str | os.PathLike[str], *, allow_locked: bool = False) -> list[Record]:
    """Read records from ``.jsonl``/``.json`` or ``.csv``.

    Reading from the locked test set is refused unless ``allow_locked`` is set,
    which only the eval harness does.
    """
    p = Path(path)
    if not allow_locked:
        ensure_not_locked(p, "read")
    records = list(_iter_csv(p) if p.suffix.lower() == ".csv" else _iter_jsonl(p))
    for i, rec in enumerate(records):
        if "text" not in rec or not isinstance(rec["text"], str):
            raise ValueError(f"{p}: record {i} has no 'text' string")
        rec.setdefault("id", f"{p.stem}-{i:05d}")
        rec["id"] = str(rec["id"])
    return records


def write_jsonl(
    path: str | os.PathLike[str], records: Iterable[Record], *, exclusive: bool = False
) -> int:
    """Write records as JSONL. Never writes into the locked directory.

    ``exclusive=True`` opens with mode ``"x"`` so an existing file is never
    overwritten.
    """
    p = Path(path)
    ensure_not_locked(p, "write")
    return _write(p, records, exclusive=exclusive)


def _write(p: Path, records: Iterable[Record], *, exclusive: bool) -> int:
    p.parent.mkdir(parents=True, exist_ok=True)
    n = 0
    with p.open("x" if exclusive else "w", encoding="utf-8") as fh:
        for rec in records:
            fh.write(json.dumps(rec, ensure_ascii=False) + "\n")
            n += 1
    return n


def create_locked_file(path: str | os.PathLike[str], records: Iterable[Record]) -> int:
    """Create a new locked test file. The only sanctioned write into ``eval/locked``.

    Uses exclusive creation, so an existing locked file can never be modified
    or replaced.
    """
    p = Path(path)
    if p.exists():
        raise FileExistsError(f"refusing to overwrite existing locked file {p}")
    return _write(p, records, exclusive=True)


def write_json(path: str | os.PathLike[str], obj: Any) -> None:
    p = Path(path)
    ensure_not_locked(p, "write")
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_text(path: str | os.PathLike[str], text: str) -> None:
    p = Path(path)
    ensure_not_locked(p, "write")
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8")
