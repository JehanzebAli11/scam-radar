"""End-to-end CLI tests with a fake teacher; no network, no GPU."""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from scamradar import cli
from scamradar.train.config import DEFAULT_CONFIG


def run(*args: str) -> int:
    return cli.main([str(a) for a in args])


def _lines(p: Path) -> list[dict]:
    return [json.loads(x) for x in p.read_text(encoding="utf-8").splitlines() if x.strip()]


def test_validate(capsys, labelled_path: Path, tmp_path: Path) -> None:
    assert run("validate", "--line", "safe|none|none") == 0
    assert run("validate", "--line", "scam|none|none") == 1
    assert run("validate", "--input", labelled_path) == 0
    bad = tmp_path / "bad.jsonl"
    bad.write_text('{"id":"x","text":"t","label":"spam|none|none"}\n{"id":"y","text":"t"}\n')
    assert run("validate", "--input", bad) == 1
    assert "0/2 valid" in capsys.readouterr().out


def test_anonymise(tmp_path: Path) -> None:
    src = tmp_path / "in.jsonl"
    src.write_text(json.dumps({"id": "a", "text": "Ahmed call 0300-1234567"}) + "\n")
    names = tmp_path / "names.txt"
    names.write_text("Ahmed\n")
    out = tmp_path / "out.jsonl"
    assert run("anonymise", src, "-o", out, "--names", names) == 0
    assert _lines(out)[0]["text"] == "<NAME> call <PHONE>"


def test_split_refuses_overwrite(tmp_path: Path, labelled_path: Path, capsys) -> None:
    locked = tmp_path / "eval" / "locked" / "test.jsonl"
    assert (
        run("split", labelled_path, "--train-out", tmp_path / "pool.jsonl", "--locked-out", locked)
        == 0
    )
    assert len(_lines(locked)) == 16
    assert (
        run("split", labelled_path, "--train-out", tmp_path / "p2.jsonl", "--locked-out", locked)
        == 1
    )
    assert "refusing to overwrite" in capsys.readouterr().err


def test_pipeline_generate_label_dedupe_datasheet(
    tmp_path: Path, seeds_path: Path, monkeypatch: pytest.MonkeyPatch, fake_teacher_cls
) -> None:
    def fn(messages):
        if messages[0]["content"].startswith("You label"):
            text = messages[-1]["content"]
            return "scam|code_theft|asks_for_code" if "code" in text else "safe|none|none"
        cat = messages[1]["content"].split("\n", 1)[0]
        return f"Fake variant one for {cat} with a code\nFake variant one for {cat} with a code!\n"

    teacher = fake_teacher_cls(fn=fn)
    monkeypatch.setattr(cli, "_teacher", lambda args: teacher)

    gen = tmp_path / "gen.jsonl"
    assert run("generate", seeds_path, "-o", gen, "-n", 2, "--categories", "code_theft,promo") == 0
    assert len(_lines(gen)) == 12

    lab = tmp_path / "lab.jsonl"
    assert run("label", gen, "-o", lab, "--rejects", tmp_path / "rej.jsonl") == 0
    labelled = _lines(lab)
    assert all(r["label"] == "scam|code_theft|asks_for_code" for r in labelled)
    assert all(r["teacher_model"] == "fake/teacher-1b" for r in labelled)

    ded = tmp_path / "ded.jsonl"
    assert run("dedupe", lab, "-o", ded, "--dupes", tmp_path / "dupes.jsonl") == 0
    assert len(_lines(ded)) == 2  # one per category after exact dedupe

    sheet = tmp_path / "sheet.json"
    assert run("datasheet", ded, "-o", sheet, "--markdown", tmp_path / "sheet.md") == 0
    assert json.loads(sheet.read_text())["provenance"]["label_models"] == {"fake/teacher-1b": 2}


def test_factory_commands_refuse_locked_input(tmp_path: Path, labelled_path: Path, capsys) -> None:
    locked = tmp_path / "eval" / "locked" / "test.jsonl"
    locked.parent.mkdir(parents=True)
    locked.write_text(labelled_path.read_text())
    for argv in (
        ("dedupe", locked, "-o", tmp_path / "x.jsonl"),
        ("datasheet", locked),
        ("baseline-tfidf", "--train", locked, "--model", tmp_path / "m.joblib"),
        ("anonymise", locked, "-o", tmp_path / "y.jsonl"),
        ("train", "--dry-run", "--train-file", locked),
    ):
        assert run(*argv) == 1, argv
    assert "locked" in capsys.readouterr().err


def test_tfidf_then_eval(tmp_path: Path, labelled_path: Path, capsys) -> None:
    locked = tmp_path / "eval" / "locked" / "test.jsonl"
    pool = tmp_path / "pool.jsonl"
    assert run("split", labelled_path, "--train-out", pool, "--locked-out", locked) == 0
    model = tmp_path / "tfidf.joblib"
    preds = tmp_path / "preds.jsonl"
    assert (
        run("baseline-tfidf", "--train", pool, "--model", model, "--predict", locked, "-o", preds)
        == 0
    )
    assert len(_lines(preds)) == 16
    before = locked.read_bytes()
    report_json = tmp_path / "report.json"
    rc = run(
        "eval",
        "--gold",
        locked,
        "--predictions",
        preds,
        "--baseline",
        preds,
        "--name",
        "tfidf-self",
        "--json",
        report_json,
        "--markdown",
        tmp_path / "r.md",
    )
    assert rc == 0
    out = capsys.readouterr().out
    assert "## Evaluation: tfidf-self" in out
    data = json.loads(report_json.read_text())
    assert {t["name"] for t in data["targets"]} >= {"scam_recall", "false_alarm_rate_otp"}
    assert locked.read_bytes() == before
    assert run("baseline-tfidf", "--model", model) == 2


def test_train_dry_run(tmp_path: Path, labelled_path: Path, capsys) -> None:
    assert (
        run(
            "train",
            "--config",
            DEFAULT_CONFIG,
            "--dry-run",
            "--train-file",
            labelled_path,
            "--output-dir",
            tmp_path / "runs",
        )
        == 0
    )
    out = capsys.readouterr().out
    assert "config: OK" in out and "train: 32 records" in out
    bad = tmp_path / "bad.jsonl"
    bad.write_text('{"id":"x","text":"t","label":"scam|none|none"}\n')
    assert run("train", "--dry-run", "--train-file", bad) == 1


def test_no_proprietary_api_references_in_code() -> None:
    """Ground rule: nothing in the package may call Claude/Anthropic, OpenAI or Gemini APIs."""
    pkg = Path(cli.__file__).parent
    banned = (
        "import anthropic",
        "import openai",
        "from openai",
        "from anthropic",
        "google.generativeai",
        "google.genai",
    )
    for path in pkg.rglob("*.py"):
        src = path.read_text(encoding="utf-8")
        for needle in banned:
            assert needle not in src, f"{path}: {needle}"
