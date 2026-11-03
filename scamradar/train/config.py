"""Training configuration loaded from YAML and validated without heavy deps."""

from __future__ import annotations

import os
from dataclasses import asdict, dataclass, fields
from pathlib import Path
from typing import Any

import yaml

from scamradar.paths import is_locked_path
from scamradar.prompting import USER_PREFIX

DEFAULT_CONFIG = Path(__file__).parent / "configs" / "v1.yaml"


class ConfigError(ValueError):
    def __init__(self, errors: list[str]):
        super().__init__("; ".join(errors))
        self.errors = errors


@dataclass
class TrainConfig:
    train_file: str
    output_dir: str
    run_name: str = "scamradar"
    model_id: str = "google/gemma-3-270m-it"
    eval_file: str | None = None
    prompt_prefix: str = USER_PREFIX
    epochs: float = 3
    learning_rate: float = 5e-5
    batch_size: int = 16
    gradient_accumulation_steps: int = 1
    max_length: int = 256
    bf16: bool = True
    lr_scheduler: str = "cosine"
    warmup_ratio: float = 0.03
    weight_decay: float = 0.0
    gradient_checkpointing: bool = False
    seed: int = 13
    save_steps: int = 200
    save_total_limit: int | None = 5
    logging_steps: int = 10
    eval_steps: int = 200
    resume_from_checkpoint: str | None = None

    def validate(self) -> list[str]:
        errors: list[str] = []
        for name in ("train_file", "eval_file", "output_dir"):
            value = getattr(self, name)
            if value and is_locked_path(value):
                errors.append(f"{name} points into the locked test set: {value}")
        if self.epochs <= 0:
            errors.append("epochs must be > 0")
        if not 0 < self.learning_rate < 1:
            errors.append("learning_rate must be in (0, 1)")
        for name in ("batch_size", "gradient_accumulation_steps", "max_length", "save_steps",
                     "logging_steps", "eval_steps"):
            if int(getattr(self, name)) <= 0:
                errors.append(f"{name} must be a positive integer")
        if not 0 <= self.warmup_ratio < 1:
            errors.append("warmup_ratio must be in [0, 1)")
        if not self.prompt_prefix.strip():
            errors.append("prompt_prefix must not be empty")
        return errors

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def load_config(path: str | os.PathLike[str] = DEFAULT_CONFIG, **overrides: Any) -> TrainConfig:
    raw = yaml.safe_load(Path(path).read_text(encoding="utf-8")) or {}
    if not isinstance(raw, dict):
        raise ConfigError([f"{path}: expected a YAML mapping"])
    raw.update({k: v for k, v in overrides.items() if v is not None})
    known = {f.name for f in fields(TrainConfig)}
    unknown = sorted(set(raw) - known)
    if unknown:
        raise ConfigError([f"unknown config keys: {unknown}"])
    missing = [k for k in ("train_file", "output_dir") if not raw.get(k)]
    if missing:
        raise ConfigError([f"missing required keys: {missing}"])
    try:
        cfg = TrainConfig(**raw)
    except TypeError as exc:
        raise ConfigError([str(exc)]) from exc
    errors = cfg.validate()
    if errors:
        raise ConfigError(errors)
    return cfg
