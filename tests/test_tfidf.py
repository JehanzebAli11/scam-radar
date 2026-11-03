from __future__ import annotations

from pathlib import Path

import pytest

from scamradar.baselines.prompt_baseline import PromptBaseline
from scamradar.baselines.tfidf import TfidfBaseline
from scamradar.paths import LockedPathError
from scamradar.schema import parse_line, validate_line


@pytest.fixture
def model(labelled: list[dict]) -> TfidfBaseline:
    return TfidfBaseline().fit(labelled)


def test_fits_training_data_and_emits_valid_lines(model: TfidfBaseline, labelled) -> None:
    lines = model.predict_lines([r["text"] for r in labelled])
    assert all(validate_line(line) == [] for line in lines)
    pairs = zip(lines, labelled, strict=True)
    acc = sum(parse_line(p).label == parse_line(r["label"]).label for p, r in pairs) / len(labelled)
    assert acc >= 0.9


def test_generalises_on_obvious_cases(model: TfidfBaseline) -> None:
    preds = model.predict_lines(
        [
            "ghalti se code chala gaya, wapis bhej dein",
            "mega sale 40% off on shoes this weekend",
        ]
    )
    assert parse_line(preds[0]).scam_type is not None
    assert parse_line(preds[1]).label.value != "scam"


def test_save_load_roundtrip(model: TfidfBaseline, tmp_path: Path) -> None:
    path = tmp_path / "m" / "tfidf.joblib"
    model.save(path)
    loaded = TfidfBaseline.load(path)
    texts = ["Lucky draw jeeta hai fee bhejein", "Ammi khana rakh dena"]
    assert loaded.predict_lines(texts) == model.predict_lines(texts)
    rows = loaded.predict(texts)
    assert all(r.latency_ms is not None and r.latency_ms >= 0 for r in rows)


def test_refuses_to_save_into_locked(model: TfidfBaseline, tmp_path: Path) -> None:
    with pytest.raises(LockedPathError):
        model.save(tmp_path / "eval" / "locked" / "m.joblib")


def test_load_rejects_foreign_file(tmp_path: Path) -> None:
    import joblib

    joblib.dump({"something": 1}, tmp_path / "x.joblib")
    with pytest.raises(ValueError):
        TfidfBaseline.load(tmp_path / "x.joblib")


def test_single_class_heads_and_unfitted() -> None:
    recs = [{"text": f"hello there {i}", "label": "safe|none|none"} for i in range(3)]
    m = TfidfBaseline().fit(recs)
    assert m.predict_lines(["anything"]) == ["safe|none|none"]
    with pytest.raises(RuntimeError):
        TfidfBaseline().predict_lines(["x"])
    with pytest.raises(ValueError):
        TfidfBaseline().fit([])


def test_prompt_baseline_styles(fake_teacher_cls) -> None:
    t = fake_teacher_cls(["Scam | fake_prize | asks_for_money", "I don't know", ""])
    pb = PromptBaseline(t, style="few_shot")
    rows = pb.predict(["a", "b", "c"])
    assert [r.line for r in rows] == ["scam|fake_prize|asks_for_money", "I don't know", ""]
    assert t.calls[0]["messages"][0]["role"] == "system"

    ft = fake_teacher_cls(["safe|none|none"])
    PromptBaseline(ft, style="finetuned").predict(["hello"])
    assert ft.calls[0]["messages"] == [{"role": "user", "content": "Classify this message:\nhello"}]
    zt = fake_teacher_cls(["safe|none|none"])
    PromptBaseline(zt, style="zero_shot").predict(["hello"])
    assert len(zt.calls[0]["messages"]) == 1
    with pytest.raises(ValueError):
        PromptBaseline(t, style="nope")
