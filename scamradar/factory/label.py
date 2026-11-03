"""Label texts with the teacher model into the compact schema."""

from __future__ import annotations

import logging
from collections.abc import Iterable, Iterator
from dataclasses import dataclass

from scamradar.factory.prompts import label_prompt
from scamradar.factory.teacher import ChatClient, prompt_hash
from scamradar.io import Record
from scamradar.schema import SchemaError, format_line, parse_line

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class LabelConfig:
    max_retries: int = 3
    temperature: float = 0.0
    retry_temperature: float = 0.3
    max_tokens: int = 32


@dataclass(frozen=True)
class LabelOutcome:
    line: str | None
    attempts: int
    errors: tuple[str, ...]


def label_text(text: str, client: ChatClient, config: LabelConfig | None = None) -> LabelOutcome:
    """Ask the teacher for a label, parse leniently, re-emit canonically.

    Retries (with a slightly higher temperature) when the reply is not a valid
    schema line. Returns ``line=None`` if every attempt failed.
    """
    cfg = config or LabelConfig()
    messages = label_prompt(text)
    errors: list[str] = []
    for attempt in range(1, cfg.max_retries + 1):
        temp = cfg.temperature if attempt == 1 else cfg.retry_temperature
        try:
            raw = client.chat(messages, temperature=temp, max_tokens=cfg.max_tokens)
        except Exception as exc:  # network errors etc. are retried too
            errors.append(f"attempt {attempt}: {type(exc).__name__}: {exc}")
            continue
        try:
            return LabelOutcome(format_line(parse_line(raw, strict=False)), attempt, tuple(errors))
        except SchemaError as exc:
            errors.append(f"attempt {attempt}: {exc} (reply={raw[:80]!r})")
    return LabelOutcome(None, cfg.max_retries, tuple(errors))


def label_records(
    records: Iterable[Record],
    client: ChatClient,
    config: LabelConfig | None = None,
    *,
    rejects: list[Record] | None = None,
) -> Iterator[Record]:
    """Yield labelled copies of ``records``; failures are appended to ``rejects``."""
    phash = prompt_hash(label_prompt("{text}"))
    for rec in records:
        outcome = label_text(rec["text"], client, config)
        if outcome.line is None:
            log.warning("could not label %s: %s", rec.get("id"), outcome.errors)
            if rejects is not None:
                rejects.append({**rec, "label_errors": list(outcome.errors)})
            continue
        yield {
            **rec,
            "label": outcome.line,
            "label_source": "teacher",
            "teacher_model": client.model,
            "teacher_prompt_hash": phash,
            "label_attempts": outcome.attempts,
        }
