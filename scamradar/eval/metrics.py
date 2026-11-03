"""Pure metric computations for Scam Radar's release targets.

Targets (see README):

1. scam recall                     >= 0.90
2. false-alarm rate on genuine     <= 0.05   (safe+promo predicted as scam;
                                              also reported for ``otp``-tagged)
3. scam_type accuracy on true scams >= 0.80
4. not worse than the TF-IDF baseline on 1-3

An unparseable prediction is treated as "not scam" (it raises no alarm and
misses any scam) and is counted in ``invalid_rate``.
"""

from __future__ import annotations

import math
from collections import Counter
from collections.abc import Sequence
from dataclasses import asdict, dataclass, field
from typing import Any

from scamradar.schema import LABELS, Prediction, SchemaError, parse_line

TARGET_SCAM_RECALL = 0.90
TARGET_FALSE_ALARM = 0.05
TARGET_TYPE_ACCURACY = 0.80
OTP_TAG = "otp"


@dataclass(frozen=True)
class Example:
    """One gold example with its predicted line."""

    id: str
    gold: str
    pred: str
    tags: tuple[str, ...] = ()
    latency_ms: float | None = None


def _ratio(num: int, den: int) -> float | None:
    return num / den if den else None


@dataclass
class Metrics:
    n: int
    n_scam: int
    n_genuine: int
    n_genuine_otp: int
    scam_recall: float | None
    false_alarm_rate: float | None
    false_alarm_rate_otp: float | None
    scam_type_accuracy: float | None
    scam_type_accuracy_on_detected: float | None
    label_accuracy: float | None
    label_macro_f1: float | None
    flag_micro_f1: float | None
    invalid_rate: float | None
    mean_latency_ms: float | None
    p95_latency_ms: float | None
    confusion: dict[str, dict[str, int]] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def _parse(line: str) -> Prediction | None:
    try:
        return parse_line(line, strict=False)
    except SchemaError:
        return None


def _p95(values: list[float]) -> float:
    s = sorted(values)
    k = max(0, math.ceil(0.95 * len(s)) - 1)
    return s[k]


def compute_metrics(examples: Sequence[Example]) -> Metrics:
    n_scam = tp = fa = fa_otp = n_gen = n_gen_otp = invalid = 0
    type_ok = detected = detected_type_ok = 0
    label_ok = 0
    confusion: dict[str, Counter[str]] = {g: Counter() for g in LABELS}
    flag_tp = flag_fp = flag_fn = 0
    latencies: list[float] = []

    for ex in examples:
        gold = parse_line(ex.gold, strict=False)
        pred = _parse(ex.pred)
        if pred is None:
            invalid += 1
        if ex.latency_ms is not None:
            latencies.append(float(ex.latency_ms))
        pred_label = pred.label.value if pred else "invalid"
        confusion[gold.label.value][pred_label] += 1
        label_ok += int(pred is not None and pred.label is gold.label)
        pred_scam = pred is not None and pred.is_scam

        if gold.is_scam:
            n_scam += 1
            tp += int(pred_scam)
            same_type = pred_scam and pred is not None and pred.scam_type is gold.scam_type
            type_ok += int(same_type)
            if pred_scam:
                detected += 1
                detected_type_ok += int(same_type)
        else:
            n_gen += 1
            fa += int(pred_scam)
            if OTP_TAG in ex.tags:
                n_gen_otp += 1
                fa_otp += int(pred_scam)

        gold_flags = {f.value for f in gold.flags}
        pred_flags = {f.value for f in pred.flags} if pred else set()
        flag_tp += len(gold_flags & pred_flags)
        flag_fp += len(pred_flags - gold_flags)
        flag_fn += len(gold_flags - pred_flags)

    n = len(examples)
    return Metrics(
        n=n,
        n_scam=n_scam,
        n_genuine=n_gen,
        n_genuine_otp=n_gen_otp,
        scam_recall=_ratio(tp, n_scam),
        false_alarm_rate=_ratio(fa, n_gen),
        false_alarm_rate_otp=_ratio(fa_otp, n_gen_otp),
        scam_type_accuracy=_ratio(type_ok, n_scam),
        scam_type_accuracy_on_detected=_ratio(detected_type_ok, detected),
        label_accuracy=_ratio(label_ok, n),
        label_macro_f1=_macro_f1(confusion) if n else None,
        flag_micro_f1=_f1(flag_tp, flag_fp, flag_fn),
        invalid_rate=_ratio(invalid, n),
        mean_latency_ms=sum(latencies) / len(latencies) if latencies else None,
        p95_latency_ms=_p95(latencies) if latencies else None,
        confusion={g: dict(c) for g, c in confusion.items()},
    )


def _f1(tp: int, fp: int, fn: int) -> float | None:
    if tp + fp + fn == 0:
        return None
    return 2 * tp / (2 * tp + fp + fn)


def _macro_f1(confusion: dict[str, Counter[str]]) -> float | None:
    scores = []
    for label in LABELS:
        tp = confusion[label][label]
        fn = sum(confusion[label].values()) - tp
        fp = sum(confusion[g][label] for g in LABELS if g != label)
        if tp + fp + fn == 0:
            continue  # label absent from gold and predictions
        scores.append(2 * tp / (2 * tp + fp + fn))
    return sum(scores) / len(scores) if scores else None


@dataclass(frozen=True)
class TargetResult:
    name: str
    value: float | None
    target: str
    passed: bool | None  # None when not measurable

    @property
    def marker(self) -> str:
        return {True: "PASS", False: "FAIL", None: "N/A"}[self.passed]


def _ge(v: float | None, t: float) -> bool | None:
    return None if v is None else v >= t


def _le(v: float | None, t: float) -> bool | None:
    return None if v is None else v <= t


def check_targets(model: Metrics, baseline: Metrics | None = None) -> list[TargetResult]:
    results = [
        TargetResult(
            "scam_recall",
            model.scam_recall,
            f">= {TARGET_SCAM_RECALL:.2f}",
            _ge(model.scam_recall, TARGET_SCAM_RECALL),
        ),
        TargetResult(
            "false_alarm_rate",
            model.false_alarm_rate,
            f"<= {TARGET_FALSE_ALARM:.2f}",
            _le(model.false_alarm_rate, TARGET_FALSE_ALARM),
        ),
        TargetResult(
            "false_alarm_rate_otp",
            model.false_alarm_rate_otp,
            f"<= {TARGET_FALSE_ALARM:.2f}",
            _le(model.false_alarm_rate_otp, TARGET_FALSE_ALARM),
        ),
        TargetResult(
            "scam_type_accuracy",
            model.scam_type_accuracy,
            f">= {TARGET_TYPE_ACCURACY:.2f}",
            _ge(model.scam_type_accuracy, TARGET_TYPE_ACCURACY),
        ),
    ]
    results.append(
        TargetResult(
            "beats_tfidf_baseline",
            None,
            "no worse on recall, false alarms, type accuracy",
            beats_baseline(model, baseline),
        )
    )
    return results


def beats_baseline(model: Metrics, baseline: Metrics | None) -> bool | None:
    """True if the model is no worse than the baseline on all three primary metrics."""
    if baseline is None:
        return None
    pairs = [
        (model.scam_recall, baseline.scam_recall, 1),
        (model.false_alarm_rate, baseline.false_alarm_rate, -1),
        (model.scam_type_accuracy, baseline.scam_type_accuracy, 1),
    ]
    measurable = [(m, b, s) for m, b, s in pairs if m is not None and b is not None]
    if not measurable:
        return None
    return all((m - b) * s >= -1e-12 for m, b, s in measurable)
