"""Exact and near-duplicate removal.

1. Exact: records whose *normalised* text matches are collapsed.
2. Near: character n-gram shingles, compared by Jaccard similarity. A small
   MinHash signature + LSH banding finds candidate pairs so this stays
   sub-quadratic on large sets; candidates are confirmed with exact Jaccard.

The first occurrence wins, so feed real data before synthetic data if real
examples should be kept preferentially.
"""

from __future__ import annotations

import hashlib
import re
import unicodedata
from collections import defaultdict
from collections.abc import Sequence
from dataclasses import dataclass

from scamradar.io import Record

_NUMBERED_PLACEHOLDER = re.compile(r"<([A-Z]+)_\d+>")
_DIGITS = re.compile(r"\d+")
_NON_WORD = re.compile(r"[^\w<>#]+", re.UNICODE)
_WS = re.compile(r"\s+")


def normalise(text: str) -> str:
    """Case-fold, NFKC, map digit runs to ``#`` and collapse punctuation/whitespace."""
    t = _NUMBERED_PLACEHOLDER.sub(r"<\1>", unicodedata.normalize("NFKC", text)).casefold()
    t = _DIGITS.sub("#", t)
    t = _NON_WORD.sub(" ", t)
    return _WS.sub(" ", t).strip()


def shingles(text: str, n: int = 4) -> frozenset[str]:
    t = normalise(text)
    if len(t) <= n:
        return frozenset({t}) if t else frozenset()
    return frozenset(t[i : i + n] for i in range(len(t) - n + 1))


def jaccard(a: frozenset[str], b: frozenset[str]) -> float:
    if not a and not b:
        return 1.0
    return len(a & b) / len(a | b)


def _hash(s: str, seed: int) -> int:
    h = hashlib.blake2b(s.encode("utf-8"), digest_size=8, salt=seed.to_bytes(8, "little"))
    return int.from_bytes(h.digest(), "little")


def minhash(sh: frozenset[str], num_perm: int = 64) -> tuple[int, ...]:
    if not sh:
        return tuple([0] * num_perm)
    return tuple(min(_hash(s, seed) for s in sh) for seed in range(num_perm))


@dataclass(frozen=True)
class DedupeConfig:
    threshold: float = 0.8
    ngram: int = 4
    num_perm: int = 64
    bands: int = 16


@dataclass
class DedupeResult:
    kept: list[Record]
    exact_dupes: list[Record]
    near_dupes: list[Record]


def dedupe(records: Sequence[Record], config: DedupeConfig | None = None) -> DedupeResult:
    cfg = config or DedupeConfig()
    if cfg.num_perm % cfg.bands:
        raise ValueError("num_perm must be divisible by bands")
    rows = cfg.num_perm // cfg.bands

    seen_norm: dict[str, str] = {}
    exact: list[Record] = []
    unique: list[Record] = []
    for rec in records:
        key = normalise(rec["text"])
        if key in seen_norm:
            exact.append({**rec, "duplicate_of": seen_norm[key]})
            continue
        seen_norm[key] = rec["id"]
        unique.append(rec)

    buckets: dict[tuple[int, int], list[int]] = defaultdict(list)
    kept_idx: list[int] = []
    kept_sh: dict[int, frozenset[str]] = {}
    near: list[Record] = []
    for i, rec in enumerate(unique):
        sh = shingles(rec["text"], cfg.ngram)
        sig = minhash(sh, cfg.num_perm)
        band_keys = [(b, hash(sig[b * rows : (b + 1) * rows])) for b in range(cfg.bands)]
        candidates = {j for key in band_keys for j in buckets.get(key, ())}
        match = next(
            (j for j in sorted(candidates) if jaccard(sh, kept_sh[j]) >= cfg.threshold), None
        )
        if match is not None:
            near.append({**rec, "duplicate_of": unique[match]["id"]})
            continue
        kept_idx.append(i)
        kept_sh[i] = sh
        for key in band_keys:
            buckets[key].append(i)
    return DedupeResult(kept=[unique[i] for i in kept_idx], exact_dupes=exact, near_dupes=near)
