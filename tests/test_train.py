from __future__ import annotations

import json
import sys
from pathlib import Path

import pytest
import yaml

from scamradar.prompting import render_gemma
from scamradar.train.config import DEFAULT_CONFIG, ConfigError, load_config
from scamradar.train.data import check_records, to_sft_example
from scamradar.train.sft import build_sft_config_kwargs, dry_run, resolve_resume


def _cfg_file(tmp_path: Path, **overrides) -> Path:
    raw = yaml.safe_load(DEFAULT_CONFIG.read_text())
    raw.update(overrides)
    p = tmp_path / "cfg.yaml"
    p.write_text(yaml.safe_dump(raw))
    return p


def test_default_config_values() -> None:
    cfg = load_config(DEFAULT_CONFIG)
    assert cfg.model_id == "google/gemma-3-270m-it"
    assert (cfg.epochs, cfg.learning_rate, cfg.batch_size, cfg.max_length, cfg.bf16) == (
        3,
        5e-5,
        16,
        256,
        True,
    )
    assert cfg.prompt_prefix == "Classify this message:"
    assert cfg.save_steps > 0 and cfg.resume_from_checkpoint is None


@pytest.mark.parametrize(
    ("overrides", "fragment"),
    [
        ({"epochs": 0}, "epochs"),
        ({"learning_rate": 2}, "learning_rate"),
        ({"batch_size": 0}, "batch_size"),
        ({"train_file": "eval/locked/test.jsonl"}, "locked"),
        ({"eval_file": "eval/locked/test.jsonl"}, "locked"),
        ({"bogus_key": 1}, "unknown config keys"),
        ({"train_file": None}, "missing"),
    ],
)
def test_config_validation(tmp_path: Path, overrides: dict, fragment: str) -> None:
    with pytest.raises(ConfigError, match=fragment):
        load_config(_cfg_file(tmp_path, **overrides))


def test_sft_example_format() -> None:
    ex = to_sft_example({"text": "hello", "label": "safe|none|none"}, "Classify this message:")
    assert ex == {
        "prompt": [{"role": "user", "content": "Classify this message:\nhello"}],
        "completion": [{"role": "assistant", "content": "safe|none|none"}],
    }
    assert render_gemma("hello", "safe|none|none") == (
        "<bos><start_of_turn>user\nClassify this message:\nhello<end_of_turn>\n"
        "<start_of_turn>model\nsafe|none|none<end_of_turn>\n"
    )


def test_check_records_flags_problems() -> None:
    recs = [
        {"id": "a", "text": "ok", "label": "safe|none|none"},
        {"id": "b", "text": "bad", "label": "scam|none|none"},
        {"id": "c", "text": "none"},
        {"id": "d", "text": "x" * 2000, "label": "promo|none|none"},
    ]
    chk = check_records(recs, "Classify this message:", 256)
    assert not chk.ok
    assert any(e.startswith("b:") for e in chk.errors)
    assert any(e.startswith("c:") for e in chk.errors)
    assert chk.n_over_length == 1 and chk.labels == {"safe": 1, "promo": 1}
    assert not check_records([], "p", 256).ok


def test_dry_run_on_fixture_without_torch(tmp_path: Path, labelled_path: Path) -> None:
    cfg = load_config(
        _cfg_file(tmp_path),
        train_file=str(labelled_path),
        eval_file=str(tmp_path / "missing.jsonl"),
        output_dir=str(tmp_path / "runs"),
    )
    report = dry_run(cfg)
    assert report.ok and report.eval is None
    assert report.train.n_records == 32
    assert "<start_of_turn>model" in report.summary()
    assert not (tmp_path / "runs").exists()


def test_dry_run_does_not_import_torch(tmp_path: Path, labelled_path: Path) -> None:
    import subprocess

    code = (
        "import sys; from scamradar.train.config import load_config; "
        "from scamradar.train.sft import dry_run; "
        f"r = dry_run(load_config(r'{DEFAULT_CONFIG}', train_file=r'{labelled_path}', "
        f"eval_file=None, output_dir=r'{tmp_path}')); "
        "assert r.ok; assert 'torch' not in sys.modules and 'transformers' not in sys.modules"
    )
    subprocess.run([sys.executable, "-c", code], check=True)


def test_resolve_resume(tmp_path: Path) -> None:
    cfg = load_config(_cfg_file(tmp_path), output_dir=str(tmp_path))
    assert resolve_resume(cfg) is None
    cfg.resume_from_checkpoint = "latest"
    assert resolve_resume(cfg) is None
    for step in (200, 1000, 400):
        (tmp_path / f"checkpoint-{step}").mkdir()
    (tmp_path / "checkpoint-tmp").mkdir()
    assert resolve_resume(cfg) == str(tmp_path / "checkpoint-1000")
    cfg.resume_from_checkpoint = str(tmp_path / "checkpoint-200")
    assert resolve_resume(cfg) == str(tmp_path / "checkpoint-200")
    cfg.resume_from_checkpoint = str(tmp_path / "nope")
    with pytest.raises(FileNotFoundError):
        resolve_resume(cfg)


def test_sft_config_kwargs(tmp_path: Path) -> None:
    cfg = load_config(DEFAULT_CONFIG)
    kw = build_sft_config_kwargs(cfg, has_eval=False)
    assert kw["num_train_epochs"] == 3 and kw["learning_rate"] == 5e-5
    assert kw["per_device_train_batch_size"] == 16 and kw["max_length"] == 256
    assert kw["bf16"] is True and kw["save_strategy"] == "steps"
    assert kw["save_steps"] == cfg.save_steps and kw["eval_strategy"] == "no"
    assert kw["completion_only_loss"] is True
    assert build_sft_config_kwargs(cfg, has_eval=True)["eval_strategy"] == "steps"
    json.dumps(kw)  # serialisable for logging
