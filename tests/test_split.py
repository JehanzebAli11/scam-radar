from __future__ import annotations

import json
from collections import Counter
from pathlib import Path

import pytest

from scamradar.io import read_records, write_jsonl
from scamradar.paths import LockedPathError
from scamradar.split import split_files, stratified_split, stratum


def _ids(records: list[dict]) -> set[str]:
    return {r["id"] for r in records}


def test_split_is_disjoint_complete_and_balanced(labelled: list[dict]) -> None:
    res = stratified_split(labelled, test_fraction=0.5, seed=1)
    assert _ids(res.train).isdisjoint(_ids(res.test))
    assert _ids(res.train) | _ids(res.test) == _ids(labelled)
    assert len(res.test) == len(labelled) // 2
    train_strata = Counter(stratum(r) for r in res.train)
    test_strata = Counter(stratum(r) for r in res.test)
    for key, total in Counter(stratum(r) for r in labelled).items():
        assert abs(train_strata[key] - test_strata[key]) <= 1, key
        assert train_strata[key] + test_strata[key] == total


def test_split_is_deterministic(labelled: list[dict]) -> None:
    a = stratified_split(labelled, seed=7)
    b = stratified_split(labelled, seed=7)
    c = stratified_split(labelled, seed=8)
    assert [r["id"] for r in a.test] == [r["id"] for r in b.test]
    assert _ids(a.test) != _ids(c.test)


def test_odd_strata_keep_overall_ratio() -> None:
    recs = [{"id": f"s{i}", "text": "x", "label": "safe|none|none"} for i in range(3)]
    recs += [{"id": f"p{i}", "text": "x", "label": "promo|none|none"} for i in range(3)]
    res = stratified_split(recs, test_fraction=0.5)
    assert len(res.test) == 3


@pytest.mark.parametrize("frac", [0.0, 1.0, -0.1])
def test_bad_fraction(labelled: list[dict], frac: float) -> None:
    with pytest.raises(ValueError):
        stratified_split(labelled, test_fraction=frac)


def test_missing_label_rejected() -> None:
    with pytest.raises(ValueError):
        stratified_split([{"id": "a", "text": "x"}])


def test_split_files_writes_and_refuses_overwrite(tmp_path: Path, labelled_path: Path) -> None:
    locked = tmp_path / "eval" / "locked" / "test.jsonl"
    train = tmp_path / "data" / "train_pool.jsonl"
    res = split_files(labelled_path, train, locked, test_fraction=0.25)
    assert locked.exists() and train.exists()
    assert len(locked.read_text().splitlines()) == len(res.test) == 8
    before = locked.read_bytes()
    with pytest.raises(FileExistsError):
        split_files(labelled_path, tmp_path / "other.jsonl", locked)
    assert locked.read_bytes() == before
    assert not (tmp_path / "other.jsonl").exists()


def test_csv_input(tmp_path: Path) -> None:
    csv_path = tmp_path / "real.csv"
    lines = ["id,text,label,tags"]
    for i in range(4):
        lines.append(f"r{i},hello {i},safe|none|none,otp")
        lines.append(f'q{i},"pay, now {i}",scam|fake_prize|asks_for_money,')
    csv_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    records = read_records(csv_path)
    assert records[0]["tags"] == ["otp"] and "tags" not in records[1]
    assert records[1]["text"] == "pay, now 0"
    res = split_files(csv_path, tmp_path / "train.jsonl", tmp_path / "eval/locked/t.jsonl")
    assert len(res.test) == 4


def test_locked_dir_is_guarded(tmp_path: Path) -> None:
    locked = tmp_path / "eval" / "locked" / "test.jsonl"
    locked.parent.mkdir(parents=True)
    locked.write_text(json.dumps({"id": "a", "text": "x", "label": "safe|none|none"}) + "\n")
    with pytest.raises(LockedPathError):
        read_records(locked)
    with pytest.raises(LockedPathError):
        write_jsonl(locked, [])
    with pytest.raises(LockedPathError):
        write_jsonl(tmp_path / "eval" / "locked" / "new.jsonl", [])
    assert read_records(locked, allow_locked=True)[0]["id"] == "a"


def test_locked_dir_env_override(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    custom = tmp_path / "vault"
    monkeypatch.setenv("SCAMRADAR_LOCKED_DIR", str(custom))
    with pytest.raises(LockedPathError):
        write_jsonl(custom / "x.jsonl", [])
