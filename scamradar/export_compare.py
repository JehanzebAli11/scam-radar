"""Side-by-side table of exported models: accuracy *and* cost.

Each experiment directory holds the outputs of one export:

* ``stats.json``  from ``scamradar predict-litertlm --stats`` (file size, RAM, latency)
* ``eval.json``   from ``scamradar eval --json`` (scam recall, false alarms, ...)
* ``device.json`` optional, filled in by hand from the phone::

      {"pss_loaded_mb": 412.0, "pss_idle_mb": 96.0, "first_check_s": 1.4}

  (``adb shell dumpsys meminfo <package>`` TOTAL PSS right after a check, and
  again after the app's 5-minute idle unload.)

The directory name is used as the row label, e.g. ``runs/exp/int8-cache4096``.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from scamradar.paths import ensure_not_locked

COLUMNS = (
    ("File MB", "stats", "model_file_mb", "{:.0f}"),
    ("Model RAM MB (host)", "stats", "model_rss_mb", "{:.0f}"),
    ("Peak RAM MB (host)", "stats", "peak_rss_mb", "{:.0f}"),
    ("Phone PSS loaded MB", "device", "pss_loaded_mb", "{:.0f}"),
    ("Phone PSS idle MB", "device", "pss_idle_mb", "{:.0f}"),
    ("Load s", "stats", "load_s", "{:.2f}"),
    ("p50 ms", "stats", "latency_ms_p50", "{:.0f}"),
    ("Scam recall", "metrics", "scam_recall", "{:.1%}"),
    ("False alarms", "metrics", "false_alarm_rate", "{:.1%}"),
    ("OTP false alarms", "metrics", "false_alarm_rate_otp", "{:.1%}"),
    ("Type accuracy", "metrics", "scam_type_accuracy", "{:.1%}"),
)


def _load(path: Path) -> dict[str, Any] | None:
    ensure_not_locked(path, "read experiment results from")
    if not path.is_file():
        return None
    data: dict[str, Any] = json.loads(path.read_text(encoding="utf-8"))
    return data


def load_row(directory: str | Path) -> dict[str, Any]:
    d = Path(directory)
    stats = _load(d / "stats.json") or {}
    evaluation = _load(d / "eval.json") or {}
    device = _load(d / "device.json") or {}
    return {
        "name": d.name,
        "stats": stats,
        "metrics": evaluation.get("metrics", {}),
        "device": device,
        "all_passed": evaluation.get("all_passed"),
    }


def _cell(row: dict[str, Any], source: str, key: str, fmt: str) -> str:
    value = row[source].get(key)
    return "–" if value is None else fmt.format(value)


def to_markdown(rows: list[dict[str, Any]]) -> str:
    head = ["Export", *(c[0] for c in COLUMNS), "Targets"]
    lines = ["| " + " | ".join(head) + " |", "|" + "---|" * len(head)]
    for row in rows:
        passed = {True: "pass", False: "FAIL", None: "–"}[row["all_passed"]]
        cells = [row["name"], *(_cell(row, s, k, f) for _, s, k, f in COLUMNS), passed]
        lines.append("| " + " | ".join(cells) + " |")
    return "\n".join(lines) + "\n"
