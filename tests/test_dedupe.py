from __future__ import annotations

import pytest

from scamradar.factory.dedupe import DedupeConfig, dedupe, jaccard, normalise, shingles


def _recs(*texts: str) -> list[dict]:
    return [{"id": f"r{i}", "text": t} for i, t in enumerate(texts)]


def test_normalise() -> None:
    assert normalise("  Aap ka CODE 123456 hai!!  ") == "aap ka code # hai"
    assert normalise("Call <PHONE_2> now") == normalise("call <PHONE_1>  NOW.")
    assert normalise("ｆｕｌｌｗｉｄｔｈ") == "fullwidth"


def test_jaccard_and_shingles() -> None:
    a, b = shingles("abcdef", 3), shingles("abcdeg", 3)
    assert jaccard(a, a) == 1.0
    assert 0 < jaccard(a, b) < 1
    assert jaccard(frozenset(), frozenset()) == 1.0
    assert shingles("ab", 4) == frozenset({"ab"})


def test_exact_duplicates_after_normalisation() -> None:
    res = dedupe(_recs("Aap ka code 123 hai", "aap ka CODE 999 hai!", "something else entirely"))
    assert [r["id"] for r in res.kept] == ["r0", "r2"]
    assert res.exact_dupes[0]["duplicate_of"] == "r0"
    assert not res.near_dupes


def test_near_duplicates() -> None:
    base = "Mubarak ho aap ne lucky draw mein 50000 jeet liye hain abhi call karein"
    res = dedupe(
        _recs(base, base + " jaldi", "Ammi main ghar 8 baje tak aa jaunga khana rakh dena")
    )
    assert [r["id"] for r in res.kept] == ["r0", "r2"]
    assert res.near_dupes[0]["duplicate_of"] == "r0"


def test_threshold_controls_near_dupes() -> None:
    texts = (
        "the quick brown fox jumps over the lazy dog",
        "the quick brown fox leaps over a lazy cat",
    )
    assert len(dedupe(_recs(*texts), DedupeConfig(threshold=0.95)).kept) == 2
    assert len(dedupe(_recs(*texts), DedupeConfig(threshold=0.2, bands=32)).kept) == 1


def test_first_occurrence_wins_and_urdu_script() -> None:
    res = dedupe(
        _recs("آپ کا انعام نکلا ہے فوراً رابطہ کریں", "آپ کا انعام نکلا ہے، فوراً رابطہ کریں!")
    )
    assert [r["id"] for r in res.kept] == ["r0"]


def test_bad_band_config() -> None:
    with pytest.raises(ValueError):
        dedupe(_recs("a"), DedupeConfig(num_perm=10, bands=3))
