"""Evaluation harness: gold set + predictions -> metrics, pass/fail, report.

This is the only module that reads the locked test set (``allow_locked=True``).
It never writes inside it: report paths are checked by :mod:`scamradar.io`.

Predictions file format (JSONL)::

    {"id": "t-0001", "pred": "scam|code_theft|asks_for_code", "latency_ms": 41.2}

``latency_ms`` is optional. Every gold id must have a prediction.
"""

from __future__ import annotations

import json
import os
from collections.abc import Sequence
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from scamradar.eval.metrics import (
    Example,
    Metrics,
    TargetResult,
    check_targets,
    compute_metrics,
)
from scamradar.io import Record, read_records, write_json, write_text
from scamradar.paths import ensure_not_locked
from scamradar.predictor import Predictor


@dataclass
class EvalReport:
    model_name: str
    metrics: Metrics
    targets: list[TargetResult]
    baseline_name: str | None = None
    baseline_metrics: Metrics | None = None

    @property
    def all_passed(self) -> bool:
        return all(t.passed is not False for t in self.targets) and any(
            t.passed for t in self.targets
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "model": self.model_name,
            "metrics": self.metrics.to_dict(),
            "targets": [
                {"name": t.name, "value": t.value, "target": t.target, "result": t.marker}
                for t in self.targets
            ],
            "all_passed": self.all_passed,
            "baseline": None
            if self.baseline_metrics is None
            else {"name": self.baseline_name, "metrics": self.baseline_metrics.to_dict()},
        }

    def to_markdown(self) -> str:
        return render_markdown(self)


def load_gold(path: str | os.PathLike[str]) -> list[Record]:
    """Read the gold set. Allowed to read the locked directory."""
    records = read_records(path, allow_locked=True)
    for rec in records:
        if "label" not in rec:
            raise ValueError(f"gold record {rec['id']} has no label")
    return records


def load_inputs(path: str | os.PathLike[str]) -> list[Record]:
    """Read texts to run a predictor on (labels optional). Allowed in the locked dir."""
    return read_records(path, allow_locked=True)


def load_predictions(path: str | os.PathLike[str]) -> dict[str, Record]:
    """Predictions are model outputs, not gold; they never live in the locked dir."""
    out: dict[str, Record] = {}
    for rec in _read_prediction_records(path):
        out[str(rec["id"])] = rec
    return out


def _read_prediction_records(path: str | os.PathLike[str]) -> list[Record]:
    ensure_not_locked(path, "read predictions from")
    rows = []
    with Path(path).open(encoding="utf-8") as fh:
        for line in fh:
            if line.strip():
                rows.append(json.loads(line))
    for r in rows:
        if "id" not in r or "pred" not in r:
            raise ValueError(f"{path}: every prediction needs 'id' and 'pred'")
    return rows


def join(gold: Sequence[Record], preds: dict[str, Record]) -> list[Example]:
    missing = [g["id"] for g in gold if g["id"] not in preds]
    if missing:
        raise ValueError(f"{len(missing)} gold ids have no prediction, e.g. {missing[:5]}")
    return [
        Example(
            id=g["id"],
            gold=str(g["label"]),
            pred=str(preds[g["id"]]["pred"]),
            tags=tuple(g.get("tags", []) or []),
            latency_ms=preds[g["id"]].get("latency_ms"),
        )
        for g in gold
    ]


def predict_records(predictor: Predictor, gold: Sequence[Record]) -> dict[str, Record]:
    rows = predictor.predict([g["text"] for g in gold])
    return {
        g["id"]: {"id": g["id"], "pred": r.line, "latency_ms": r.latency_ms}
        for g, r in zip(gold, rows, strict=True)
    }


def evaluate(
    examples: Sequence[Example],
    *,
    model_name: str = "model",
    baseline_examples: Sequence[Example] | None = None,
    baseline_name: str = "tfidf",
) -> EvalReport:
    metrics = compute_metrics(examples)
    base = compute_metrics(baseline_examples) if baseline_examples is not None else None
    return EvalReport(
        model_name=model_name,
        metrics=metrics,
        targets=check_targets(metrics, base),
        baseline_name=baseline_name if base is not None else None,
        baseline_metrics=base,
    )


def _fmt(v: float | None, pct: bool = True) -> str:
    if v is None:
        return "n/a"
    return f"{v * 100:.1f}%" if pct else f"{v:.1f}"


_ROWS: tuple[tuple[str, str, bool], ...] = (
    ("Scam recall", "scam_recall", True),
    ("False-alarm rate (safe+promo)", "false_alarm_rate", True),
    ("False-alarm rate (OTP-tagged)", "false_alarm_rate_otp", True),
    ("Scam-type accuracy (true scams)", "scam_type_accuracy", True),
    ("Scam-type accuracy (detected scams)", "scam_type_accuracy_on_detected", True),
    ("Label accuracy", "label_accuracy", True),
    ("Label macro-F1", "label_macro_f1", True),
    ("Flag micro-F1", "flag_micro_f1", True),
    ("Invalid output rate", "invalid_rate", True),
    ("Mean latency (ms)", "mean_latency_ms", False),
    ("p95 latency (ms)", "p95_latency_ms", False),
)


def render_markdown(report: EvalReport) -> str:
    m, b = report.metrics, report.baseline_metrics
    verdict = "PASS" if report.all_passed else "FAIL"
    lines = [
        f"## Evaluation: {report.model_name}",
        "",
        f"**Overall: {verdict}** · n={m.n} (scams {m.n_scam}, genuine {m.n_genuine}, "
        f"of which OTP {m.n_genuine_otp})",
        "",
        "### Targets",
        "",
        "| Target | Value | Goal | Result |",
        "|---|---|---|---|",
    ]
    for t in report.targets:
        value = _fmt(t.value) if t.name != "beats_tfidf_baseline" else "see below"
        lines.append(f"| {t.name} | {value} | {t.target} | {t.marker} |")
    header = f"| Metric | {report.model_name} |" + (f" {report.baseline_name} |" if b else "")
    lines += ["", "### Metrics", "", header, "|---|---|" + ("---|" if b else "")]
    for title, key, pct in _ROWS:
        row = f"| {title} | {_fmt(getattr(m, key), pct)} |"
        if b:
            row += f" {_fmt(getattr(b, key), pct)} |"
        lines.append(row)
    lines += ["", "### Label confusion (rows = gold)", ""]
    cols = ["safe", "promo", "scam", "invalid"]
    lines += ["| gold \\ pred | " + " | ".join(cols) + " |", "|---" * (len(cols) + 1) + "|"]
    for gold, row in m.confusion.items():
        lines.append(f"| {gold} | " + " | ".join(str(row.get(c, 0)) for c in cols) + " |")
    return "\n".join(lines) + "\n"


def write_report(report: EvalReport, json_path: str | None, md_path: str | None) -> None:
    if json_path:
        write_json(json_path, report.to_dict())
    if md_path:
        write_text(md_path, report.to_markdown())
