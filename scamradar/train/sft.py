"""Full fine-tune of Gemma 3 270M with TRL's ``SFTTrainer``.

``dry_run`` validates the config and dataset formatting with no torch or
transformers installed. ``train`` imports the heavy stack lazily.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

from scamradar.io import read_records, write_json
from scamradar.train.config import TrainConfig
from scamradar.train.data import DatasetCheck, check_records, to_sft_example


@dataclass
class DryRunReport:
    config: TrainConfig
    train: DatasetCheck
    eval: DatasetCheck | None

    @property
    def ok(self) -> bool:
        return self.train.ok and (self.eval is None or self.eval.ok)

    def summary(self) -> str:
        lines = [f"config: OK ({self.config.run_name}, model={self.config.model_id})"]
        for name, chk in (("train", self.train), ("eval", self.eval)):
            if chk is None:
                continue
            status = "OK" if chk.ok else f"{len(chk.errors)} error(s)"
            lines.append(
                f"{name}: {chk.n_records} records, labels={dict(chk.labels)}, "
                f"est. max tokens={chk.est_max_tokens}, over max_length={chk.n_over_length}, "
                f"{status}"
            )
            lines += [f"  - {e}" for e in chk.errors[:10]]
        if self.train.example_rendered:
            lines += ["example (rendered Gemma chat format):", self.train.example_rendered]
        return "\n".join(lines)


def dry_run(cfg: TrainConfig) -> DryRunReport:
    train = check_records(read_records(cfg.train_file), cfg.prompt_prefix, cfg.max_length)
    ev = None
    if cfg.eval_file and Path(cfg.eval_file).exists():
        ev = check_records(read_records(cfg.eval_file), cfg.prompt_prefix, cfg.max_length)
    return DryRunReport(cfg, train, ev)


def resolve_resume(cfg: TrainConfig) -> str | None:
    """Map ``resume_from_checkpoint`` to a concrete checkpoint path (or None)."""
    value = cfg.resume_from_checkpoint
    if not value:
        return None
    if value in ("latest", "true", "True"):
        out = Path(cfg.output_dir)
        ckpts = sorted(
            (p for p in out.glob("checkpoint-*") if p.name.split("-")[-1].isdigit()),
            key=lambda p: int(p.name.split("-")[-1]),
        )
        return str(ckpts[-1]) if ckpts else None
    if not Path(value).exists():
        raise FileNotFoundError(f"resume_from_checkpoint not found: {value}")
    return value


def build_sft_config_kwargs(cfg: TrainConfig, has_eval: bool) -> dict[str, Any]:
    """Arguments for ``trl.SFTConfig``; kept separate so it can be unit-tested."""
    return {
        "output_dir": cfg.output_dir,
        "run_name": cfg.run_name,
        "num_train_epochs": cfg.epochs,
        "learning_rate": cfg.learning_rate,
        "per_device_train_batch_size": cfg.batch_size,
        "per_device_eval_batch_size": cfg.batch_size,
        "gradient_accumulation_steps": cfg.gradient_accumulation_steps,
        "max_length": cfg.max_length,
        "bf16": cfg.bf16,
        "lr_scheduler_type": cfg.lr_scheduler,
        "warmup_ratio": cfg.warmup_ratio,
        "weight_decay": cfg.weight_decay,
        "gradient_checkpointing": cfg.gradient_checkpointing,
        "seed": cfg.seed,
        "save_strategy": "steps",
        "save_steps": cfg.save_steps,
        "save_total_limit": cfg.save_total_limit,
        "logging_steps": cfg.logging_steps,
        "eval_strategy": "steps" if has_eval else "no",
        "eval_steps": cfg.eval_steps if has_eval else None,
        "completion_only_loss": True,
        "report_to": "none",
    }


def train(cfg: TrainConfig) -> str:  # pragma: no cover - needs GPU + model weights
    """Run SFT. Returns the path of the saved final model."""
    try:
        import torch
        from datasets import Dataset
        from transformers import AutoModelForCausalLM, AutoTokenizer
        from trl import SFTConfig, SFTTrainer
    except ImportError as exc:
        raise SystemExit("training needs the optional deps: pip install -e '.[train]'") from exc

    report = dry_run(cfg)
    if not report.ok:
        raise SystemExit("dataset check failed:\n" + report.summary())

    def load(path: str) -> Any:
        return Dataset.from_list([to_sft_example(r, cfg.prompt_prefix) for r in read_records(path)])

    train_ds = load(cfg.train_file)
    eval_ds = load(cfg.eval_file) if report.eval is not None and cfg.eval_file else None

    tokenizer = AutoTokenizer.from_pretrained(cfg.model_id)
    model = AutoModelForCausalLM.from_pretrained(
        cfg.model_id,
        torch_dtype=torch.bfloat16 if cfg.bf16 else torch.float32,
        attn_implementation="eager",  # recommended for Gemma 3 training
    )
    trainer = SFTTrainer(
        model=model,
        args=SFTConfig(**build_sft_config_kwargs(cfg, eval_ds is not None)),
        train_dataset=train_ds,
        eval_dataset=eval_ds,
        processing_class=tokenizer,
    )
    trainer.train(resume_from_checkpoint=resolve_resume(cfg))
    final_dir = str(Path(cfg.output_dir) / "final")
    trainer.save_model(final_dir)
    tokenizer.save_pretrained(final_dir)
    write_json(Path(cfg.output_dir) / "train_config.json", cfg.to_dict())
    return final_dir
