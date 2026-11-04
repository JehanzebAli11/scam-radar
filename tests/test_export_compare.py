from __future__ import annotations

import json
from pathlib import Path

from scamradar.cli import main
from scamradar.export_compare import load_row, to_markdown


def _exp(root: Path, name: str, stats: dict, metrics: dict | None, passed: bool | None) -> Path:
    d = root / name
    d.mkdir()
    (d / "stats.json").write_text(json.dumps(stats))
    if metrics is not None:
        (d / "eval.json").write_text(json.dumps({"metrics": metrics, "all_passed": passed}))
    return d


def test_table_has_cost_and_accuracy_per_export(tmp_path: Path) -> None:
    a = _exp(
        tmp_path,
        "int8-cache4096",
        {"model_file_mb": 290.4, "model_rss_mb": 410.2, "load_s": 1.2, "latency_ms_p50": 80},
        {"scam_recall": 0.93, "false_alarm_rate": 0.04, "false_alarm_rate_otp": 0.0},
        True,
    )
    b = _exp(tmp_path, "int4-cache512", {"model_file_mb": 160.0, "model_rss_mb": 190.0}, None, None)
    (b / "device.json").write_text(json.dumps({"pss_loaded_mb": 260.0}))
    table = to_markdown([load_row(a), load_row(b)])
    lines = table.splitlines()
    assert lines[0].startswith("| Export | File MB | Model RAM MB (host)")
    assert "| int8-cache4096 | 290 | 410 |" in lines[2]
    assert "93.0%" in lines[2] and lines[2].endswith("| pass |")
    assert "| int4-cache512 | 160 | 190 |" in lines[3]
    assert "| 260 |" in lines[3] and lines[3].endswith("| – |")


def test_cli_writes_markdown(tmp_path: Path) -> None:
    d = _exp(tmp_path, "x", {"model_file_mb": 1.0}, None, None)
    out = tmp_path / "TABLE.md"
    assert main(["compare-exports", str(d), "--markdown", str(out)]) == 0
    assert "| x | 1 |" in out.read_text()
