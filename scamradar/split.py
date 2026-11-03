"""Stratified split of real labelled messages into a train pool and the locked test set.

Strata are ``label`` for safe/promo and ``scam:<scam_type>`` for scams, so
every scam type is represented on both sides where possible. The split is
deterministic for a given seed.
"""

from __future__ import annotations

import os
import random
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path

from scamradar.io import Record, create_locked_file, read_records, write_jsonl
from scamradar.schema import parse_line


@dataclass(frozen=True)
class SplitResult:
    train: list[Record]
    test: list[Record]


def stratum(record: Record) -> str:
    pred = parse_line(str(record["label"]))
    if pred.scam_type is not None:
        return f"scam:{pred.scam_type.value}"
    return pred.label.value


def stratified_split(
    records: list[Record], test_fraction: float = 0.5, seed: int = 13
) -> SplitResult:
    """Split per stratum. Each stratum sends ``round(n * test_fraction)`` to test.

    Rounding alternates across odd-sized strata so the overall ratio stays
    close to ``test_fraction``. Singleton strata go to test first.
    """
    if not 0.0 < test_fraction < 1.0:
        raise ValueError("test_fraction must be in (0, 1)")
    groups: dict[str, list[Record]] = defaultdict(list)
    for rec in records:
        if "label" not in rec:
            raise ValueError(f"record {rec.get('id')} has no label")
        groups[stratum(rec)].append(rec)

    rng = random.Random(seed)
    train: list[Record] = []
    test: list[Record] = []
    carry = 0.0
    for key in sorted(groups):
        items = list(groups[key])
        rng.shuffle(items)
        exact = len(items) * test_fraction + carry
        n_test = int(exact + 0.5)
        n_test = min(len(items), max(0, n_test))
        carry = exact - n_test
        test.extend(items[:n_test])
        train.extend(items[n_test:])
    return SplitResult(train=train, test=test)


def split_files(
    input_path: str | os.PathLike[str],
    train_out: str | os.PathLike[str],
    locked_out: str | os.PathLike[str],
    *,
    test_fraction: float = 0.5,
    seed: int = 13,
) -> SplitResult:
    """Read, split and write. Refuses to overwrite an existing locked file."""
    locked = Path(locked_out)
    if locked.exists():
        raise FileExistsError(f"refusing to overwrite existing locked file {locked}")
    records = read_records(input_path)
    result = stratified_split(records, test_fraction=test_fraction, seed=seed)
    # Write the locked file first (exclusive create) so a failure never leaves
    # a train pool that silently overlaps a later test set.
    create_locked_file(locked, result.test)
    write_jsonl(train_out, result.train)
    return result
