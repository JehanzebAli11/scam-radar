"""Generate message variants from seed examples via the teacher model."""

from __future__ import annotations

import re
from collections.abc import Iterable, Iterator
from dataclasses import dataclass

from scamradar.factory.prompts import CATEGORY_GUIDANCE, CATEGORY_TAGS, generation_prompt
from scamradar.factory.teacher import ChatClient, prompt_hash
from scamradar.io import Record

_LIST_PREFIX = re.compile(r"^\s*(?:\d+[.)]|[-*•])\s+")


@dataclass(frozen=True)
class GenerateConfig:
    n_per_seed: int = 5
    temperature: float = 0.9
    max_tokens: int = 1024
    min_chars: int = 8


def parse_variants(raw: str, *, min_chars: int = 8) -> list[str]:
    """Split a teacher reply into individual messages (one per line)."""
    out: list[str] = []
    for line in raw.splitlines():
        line = _LIST_PREFIX.sub("", line).strip().strip('"').strip()
        if len(line) >= min_chars and not line.startswith("```"):
            out.append(line)
    return out


def generate_from_seeds(
    seeds: Iterable[Record], client: ChatClient, config: GenerateConfig | None = None
) -> Iterator[Record]:
    """Yield generated records. Each carries provenance but no label.

    Generated text is labelled separately by :mod:`scamradar.factory.label`, so
    the category a seed was filed under never leaks into the label directly.
    """
    cfg = config or GenerateConfig()
    for seed in seeds:
        category = str(seed.get("category", ""))
        if category not in CATEGORY_GUIDANCE:
            raise ValueError(f"seed {seed.get('id')}: unknown category {category!r}")
        language = str(seed.get("lang", "roman_urdu"))
        messages = generation_prompt(category, seed["text"], language, cfg.n_per_seed)
        raw = client.chat(messages, temperature=cfg.temperature, max_tokens=cfg.max_tokens)
        variants = parse_variants(raw, min_chars=cfg.min_chars)[: cfg.n_per_seed]
        tags = sorted(set(seed.get("tags", [])) | set(CATEGORY_TAGS.get(category, ())))
        for i, text in enumerate(variants):
            yield {
                "id": f"{seed['id']}-g{i:02d}",
                "text": text,
                "category": category,
                "lang": language,
                "tags": tags,
                "source": "teacher_generated",
                "seed_id": seed["id"],
                "generator_model": client.model,
                "generator_prompt_hash": prompt_hash(messages),
            }
