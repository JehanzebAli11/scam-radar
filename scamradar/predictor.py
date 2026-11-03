"""Common interface for anything that turns texts into compact schema lines."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class PredictionRow:
    line: str
    latency_ms: float | None = None


class Predictor(Protocol):
    name: str

    def predict(self, texts: list[str]) -> list[PredictionRow]: ...
