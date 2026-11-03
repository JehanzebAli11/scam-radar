"""Turn labelled records into SFT examples (prompt/completion chat format)."""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass, field
from typing import Any

from scamradar.io import Record
from scamradar.prompting import render_gemma, user_content
from scamradar.schema import validate_line

#: Conservative chars-per-token estimate for the dry run (no tokenizer here).
#: Urdu script tokenises less efficiently than Latin text, so err low.
CHARS_PER_TOKEN = 2.5


def to_sft_example(record: Record, prefix: str) -> dict[str, Any]:
    """TRL prompt-completion conversational format; loss is on the completion only."""
    return {
        "prompt": [{"role": "user", "content": user_content(record["text"], prefix)}],
        "completion": [{"role": "assistant", "content": str(record["label"])}],
    }


@dataclass
class DatasetCheck:
    n_records: int = 0
    labels: Counter[str] = field(default_factory=Counter)
    errors: list[str] = field(default_factory=list)
    est_max_tokens: int = 0
    n_over_length: int = 0
    example_rendered: str = ""

    @property
    def ok(self) -> bool:
        return self.n_records > 0 and not self.errors


def check_records(records: list[Record], prefix: str, max_length: int) -> DatasetCheck:
    chk = DatasetCheck(n_records=len(records))
    for rec in records:
        line = rec.get("label")
        if line is None:
            chk.errors.append(f"{rec['id']}: missing label")
            continue
        errs = validate_line(str(line))
        if errs:
            chk.errors.append(f"{rec['id']}: {'; '.join(errs)}")
            continue
        chk.labels[str(line).split("|", 1)[0]] += 1
        est = int(len(render_gemma(rec["text"], str(line), prefix)) / CHARS_PER_TOKEN) + 1
        chk.est_max_tokens = max(chk.est_max_tokens, est)
        chk.n_over_length += int(est > max_length)
    if records and "label" in records[0]:
        chk.example_rendered = render_gemma(records[0]["text"], str(records[0]["label"]), prefix)
    if not records:
        chk.errors.append("dataset is empty")
    return chk
