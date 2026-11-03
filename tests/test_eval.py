from __future__ import annotations

import itertools
import json
from pathlib import Path

import pytest

from scamradar.eval.harness import (
    evaluate,
    join,
    load_gold,
    load_predictions,
    predict_records,
    render_markdown,
    write_report,
)
from scamradar.eval.metrics import Example, beats_baseline, check_targets, compute_metrics
from scamradar.paths import LockedPathError
from scamradar.predictor import PredictionRow

SCAM_CT = "scam|code_theft|asks_for_code"
SCAM_FP = "scam|fake_prize|asks_for_money"
SAFE = "safe|none|none"
PROMO = "promo|none|none"


_ids = itertools.count(1)


def ex(gold: str, pred: str, tags=(), latency=None) -> Example:
    return Example(id=f"e{next(_ids)}", gold=gold, pred=pred, tags=tuple(tags), latency_ms=latency)


def test_metric_math_by_hand() -> None:
    examples = [
        # 4 scams: 3 detected, 2 with correct type
        ex(SCAM_CT, SCAM_CT, latency=10),
        ex(SCAM_CT, SCAM_FP, latency=20),
        ex(SCAM_FP, SCAM_FP, latency=30),
        ex(SCAM_FP, SAFE, latency=40),
        # 6 genuine: 1 false alarm on an OTP, 1 promo->safe confusion, 1 invalid
        ex(SAFE, SAFE, tags=["otp"]),
        ex(SAFE, SCAM_CT, tags=["otp"]),
        ex(SAFE, SAFE),
        ex(PROMO, PROMO),
        ex(PROMO, SAFE),
        ex(PROMO, "gibberish"),
    ]
    m = compute_metrics(examples)
    assert (m.n, m.n_scam, m.n_genuine, m.n_genuine_otp) == (10, 4, 6, 2)
    assert m.scam_recall == pytest.approx(3 / 4)
    assert m.false_alarm_rate == pytest.approx(1 / 6)
    assert m.false_alarm_rate_otp == pytest.approx(1 / 2)
    assert m.scam_type_accuracy == pytest.approx(2 / 4)
    assert m.scam_type_accuracy_on_detected == pytest.approx(2 / 3)
    assert m.label_accuracy == pytest.approx(6 / 10)
    assert m.invalid_rate == pytest.approx(1 / 10)
    assert m.mean_latency_ms == pytest.approx(25.0)
    assert m.p95_latency_ms == 40
    assert m.confusion["promo"] == {"promo": 1, "safe": 1, "invalid": 1}
    assert m.confusion["scam"] == {"scam": 3, "safe": 1}
    # macro F1 over safe/promo/scam from the confusion matrix
    f1_safe = 2 * 2 / (2 * 2 + 2 + 1)
    f1_promo = 2 * 1 / (2 * 1 + 0 + 2)
    f1_scam = 2 * 3 / (2 * 3 + 1 + 1)
    assert m.label_macro_f1 == pytest.approx((f1_safe + f1_promo + f1_scam) / 3)
    # flags: gold {code}x2,{money}x2 ; preds {code},{money},{money},{} + one false {code}
    # tp=2 (e1 code, e3 money), fp=2 (e2 money, e6 code), fn=2 (e2 code, e4 money)
    assert m.flag_micro_f1 == pytest.approx(2 * 2 / (2 * 2 + 2 + 2))


def test_targets_pass_and_fail() -> None:
    perfect = [ex(SCAM_CT, SCAM_CT)] * 9 + [ex(SAFE, SAFE, tags=["otp"])] * 20
    results = {t.name: t for t in check_targets(compute_metrics(perfect))}
    assert results["scam_recall"].marker == "PASS"
    assert results["false_alarm_rate"].marker == "PASS"
    assert results["false_alarm_rate_otp"].marker == "PASS"
    assert results["scam_type_accuracy"].marker == "PASS"
    assert results["beats_tfidf_baseline"].marker == "N/A"

    bad = [ex(SCAM_CT, SAFE)] * 2 + [ex(SCAM_CT, SCAM_FP)] * 8 + [ex(SAFE, SCAM_CT)]
    r = {t.name: t for t in check_targets(compute_metrics(bad))}
    assert r["scam_recall"].marker == "FAIL"  # 0.8 < 0.9
    assert r["false_alarm_rate"].marker == "FAIL"  # 1.0 > 0.05
    assert r["false_alarm_rate_otp"].marker == "N/A"  # no OTP-tagged genuine
    assert r["scam_type_accuracy"].marker == "FAIL"


def test_threshold_edges_inclusive() -> None:
    nine_of_ten = [ex(SCAM_CT, SCAM_CT)] * 9 + [ex(SCAM_CT, SAFE)]
    one_in_twenty = [ex(SAFE, SAFE)] * 19 + [ex(SAFE, SCAM_CT)]
    r = {t.name: t for t in check_targets(compute_metrics(nine_of_ten + one_in_twenty))}
    assert r["scam_recall"].passed is True
    assert r["false_alarm_rate"].passed is True


def test_beats_baseline() -> None:
    good = compute_metrics([ex(SCAM_CT, SCAM_CT), ex(SAFE, SAFE)])
    worse = compute_metrics([ex(SCAM_CT, SAFE), ex(SAFE, SCAM_CT)])
    assert beats_baseline(good, worse) is True
    assert beats_baseline(worse, good) is False
    assert beats_baseline(good, good) is True
    assert beats_baseline(good, None) is None


def test_empty_is_not_a_pass() -> None:
    report = evaluate([])
    assert report.metrics.scam_recall is None
    assert not report.all_passed


def test_report_markdown_and_json(tmp_path: Path) -> None:
    model = [ex(SCAM_CT, SCAM_CT, latency=5), ex(SAFE, SAFE, tags=["otp"], latency=7)]
    base = [ex(SCAM_CT, SAFE), ex(SAFE, SAFE, tags=["otp"])]
    report = evaluate(model, model_name="gemma", baseline_examples=base)
    assert report.all_passed
    md = render_markdown(report)
    assert "## Evaluation: gemma" in md and "**Overall: PASS**" in md
    assert "| scam_recall | 100.0% | >= 0.90 | PASS |" in md
    assert "| beats_tfidf_baseline | see below |" in md and "| tfidf |" in md
    assert "| Mean latency (ms) | 6.0 | n/a |" in md
    write_report(report, str(tmp_path / "r.json"), str(tmp_path / "r.md"))
    data = json.loads((tmp_path / "r.json").read_text())
    assert data["all_passed"] is True
    assert data["baseline"]["metrics"]["scam_recall"] == 0.0
    with pytest.raises(LockedPathError):
        write_report(report, str(tmp_path / "eval" / "locked" / "r.json"), None)


def test_harness_reads_locked_gold_and_joins(tmp_path: Path) -> None:
    gold = tmp_path / "eval" / "locked" / "test.jsonl"
    gold.parent.mkdir(parents=True)
    gold.write_text(
        "\n".join(
            json.dumps(r)
            for r in [
                {"id": "a", "text": "send code", "label": SCAM_CT},
                {"id": "b", "text": "otp do not share", "label": SAFE, "tags": ["otp"]},
            ]
        )
        + "\n"
    )
    preds = tmp_path / "preds.jsonl"
    preds.write_text(
        json.dumps({"id": "a", "pred": SCAM_CT, "latency_ms": 3})
        + "\n"
        + json.dumps({"id": "b", "pred": SAFE})
        + "\n"
    )
    examples = join(load_gold(gold), load_predictions(preds))
    assert examples[1].tags == ("otp",) and examples[0].latency_ms == 3
    before = gold.read_bytes()
    evaluate(examples)
    assert gold.read_bytes() == before

    preds.write_text(json.dumps({"id": "a", "pred": SCAM_CT}) + "\n")
    with pytest.raises(ValueError, match="no prediction"):
        join(load_gold(gold), load_predictions(preds))
    with pytest.raises(LockedPathError):
        load_predictions(gold)


def test_predict_records() -> None:
    class Echo:
        name = "echo"

        def predict(self, texts):
            return [PredictionRow(SAFE, 1.0) for _ in texts]

    out = predict_records(Echo(), [{"id": "x", "text": "t"}])
    assert out == {"x": {"id": "x", "pred": SAFE, "latency_ms": 1.0}}
