"""Endpoint predictor: the *base* model prompted to classify, or the fine-tuned one.

Uses the same OpenAI-compatible endpoint mechanism as the teacher, e.g. vLLM
serving ``google/gemma-3-270m-it`` on the Spark. Prompt styles:

* ``few_shot``: label instructions + few-shot examples (prompt baseline)
* ``zero_shot``: label instructions only (prompt baseline)
* ``finetuned``: exactly the training prompt, for a served fine-tuned checkpoint

Unparseable replies are kept verbatim so the harness counts them as invalid.
"""

from __future__ import annotations

import time
from dataclasses import dataclass

from scamradar.factory.prompts import LABEL_SYSTEM, label_prompt
from scamradar.factory.teacher import ChatClient
from scamradar.predictor import PredictionRow
from scamradar.prompting import chat_messages, user_content
from scamradar.schema import SchemaError, format_line, parse_line

DEFAULT_BASE_MODEL = "google/gemma-3-270m-it"
PROMPT_STYLES = ("few_shot", "zero_shot", "finetuned")


@dataclass
class PromptBaseline:
    client: ChatClient
    style: str = "few_shot"
    max_tokens: int = 32
    name: str = "prompt"

    def __post_init__(self) -> None:
        if self.style not in PROMPT_STYLES:
            raise ValueError(f"style must be one of {PROMPT_STYLES}")

    def messages(self, text: str) -> list[dict[str, str]]:
        if self.style == "few_shot":
            return label_prompt(text)
        if self.style == "finetuned":
            return chat_messages(text)
        # Gemma has no system role; fold the instructions into the user turn.
        return [{"role": "user", "content": f"{LABEL_SYSTEM}\n\n{user_content(text)}"}]

    def predict(self, texts: list[str]) -> list[PredictionRow]:
        rows = []
        for text in texts:
            t0 = time.perf_counter()
            raw = self.client.chat(self.messages(text), temperature=0.0, max_tokens=self.max_tokens)
            ms = (time.perf_counter() - t0) * 1000
            try:
                line = format_line(parse_line(raw, strict=False))
            except SchemaError:
                line = raw.strip().splitlines()[0] if raw.strip() else ""
            rows.append(PredictionRow(line, ms))
        return rows
