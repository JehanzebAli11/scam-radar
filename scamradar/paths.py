"""Guards for the locked, human-labelled test set under ``eval/locked/``.

Only the evaluation harness may read from the locked directory, and nothing may
write to it except :func:`scamradar.split` creating a brand-new file once.
"""

from __future__ import annotations

import os
from pathlib import Path

LOCKED_ENV = "SCAMRADAR_LOCKED_DIR"
_LOCKED_PARTS = ("eval", "locked")


class LockedPathError(PermissionError):
    """Raised when code tries to touch the locked test set in a forbidden way."""


def _extra_locked_dir() -> Path | None:
    value = os.environ.get(LOCKED_ENV)
    return Path(value).expanduser().resolve() if value else None


def is_locked_path(path: str | os.PathLike[str]) -> bool:
    """True if ``path`` lies inside an ``eval/locked`` directory (or ``$SCAMRADAR_LOCKED_DIR``)."""
    resolved = Path(path).expanduser().resolve()
    parts = resolved.parts
    for i in range(len(parts) - 1):
        if parts[i] == _LOCKED_PARTS[0] and parts[i + 1] == _LOCKED_PARTS[1]:
            return True
    extra = _extra_locked_dir()
    return extra is not None and (resolved == extra or extra in resolved.parents)


def ensure_not_locked(path: str | os.PathLike[str], action: str) -> None:
    """Raise :class:`LockedPathError` if ``path`` is inside the locked test set."""
    if is_locked_path(path):
        raise LockedPathError(
            f"refusing to {action} {path}: it is inside the locked test set. "
            "Only the eval harness may read it and nothing may modify it."
        )
