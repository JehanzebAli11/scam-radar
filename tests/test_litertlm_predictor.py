"""LiteRtLmPredictor with a fake ``litert_lm`` module (no native library, no model file)."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import pytest

from scamradar.baselines.litertlm_predictor import (
    DEFAULT_MAX_NUM_TOKENS,
    LiteRtLmPredictor,
    RunStats,
)
from scamradar.eval.harness import predict_records


@dataclass
class _Reply:
    contents: str


class _FakeLm:
    """Records how the engine is built and used; answers from a script."""

    def __init__(self, answers: dict[str, str]) -> None:
        self.answers = answers
        self.engines: list[dict[str, Any]] = []
        self.prompts: list[str] = []
        self.samplers: list[Any] = []
        self.conversations = 0
        lm = self

        @dataclass
        class SamplerConfig:
            top_k: int
            top_p: float
            temperature: float

        class Backend:
            @staticmethod
            def CPU() -> str:  # noqa: N802 - mirrors the real API
                return "cpu"

            @staticmethod
            def GPU() -> str:  # noqa: N802
                return "gpu"

        class Conversation:
            def __enter__(self) -> Conversation:
                lm.conversations += 1
                return self

            def __exit__(self, *exc: object) -> None:
                return None

            def send_message(self, prompt: str, max_output_tokens: int) -> _Reply:
                lm.prompts.append(prompt)
                assert max_output_tokens > 0
                text = prompt.split("\n", 1)[1]
                return _Reply(lm.answers.get(text, "not a compact line"))

        class Engine:
            def __init__(self, path: str, backend: str, max_num_tokens: int | None) -> None:
                lm.engines.append(
                    {"path": path, "backend": backend, "max_num_tokens": max_num_tokens}
                )
                self.closed = False

            def create_conversation(self, sampler_config: Any) -> Conversation:
                lm.samplers.append(sampler_config)
                return Conversation()

            def close(self) -> None:
                self.closed = True

        self.SamplerConfig = SamplerConfig
        self.Backend = Backend
        self.Engine = Engine


@pytest.fixture
def model_file(tmp_path: Path) -> str:
    p = tmp_path / "model.litertlm"
    p.write_bytes(b"\0" * (3 * 1024 * 1024))
    return str(p)


def test_uses_training_prompt_greedy_and_fresh_conversations(model_file: str) -> None:
    lm = _FakeLm({"send me the code": " Scam|Code_Theft|asks_for_code\n<end_of_turn>"})
    pred = LiteRtLmPredictor(model_file, module=lm)
    rows = pred.predict(["send me the code", "hello"])

    assert [r.line for r in rows] == ["scam|code_theft|asks_for_code", "not a compact line"]
    assert lm.prompts == [
        "Classify this message:\nsend me the code",
        "Classify this message:\nhello",
    ]
    assert lm.conversations == 2  # no history shared between messages
    assert all(s.top_k == 1 for s in lm.samplers)
    assert lm.engines == [
        {"path": model_file, "backend": "cpu", "max_num_tokens": DEFAULT_MAX_NUM_TOKENS}
    ]


def test_records_run_stats(model_file: str) -> None:
    pred = LiteRtLmPredictor(model_file, max_num_tokens=None, module=_FakeLm({}))
    pred.predict(["a", "b", "c"])
    assert isinstance(pred.stats, RunStats)
    s = pred.stats.summary()
    assert s["model_file_mb"] == 3.0
    assert s["max_num_tokens"] is None
    assert s["messages"] == 3
    assert s["latency_ms_p50"] is not None and s["latency_ms_mean"] is not None
    assert s["peak_rss_mb"] > 0
    assert "latency_ms" not in s


def test_works_through_the_eval_harness(model_file: str) -> None:
    lm = _FakeLm({"x": "safe|none|none"})
    preds = predict_records(LiteRtLmPredictor(model_file, module=lm), [{"id": "1", "text": "x"}])
    assert preds["1"]["pred"] == "safe|none|none"
    assert preds["1"]["latency_ms"] is not None


def test_close_releases_engine(model_file: str) -> None:
    pred = LiteRtLmPredictor(model_file, module=_FakeLm({}))
    pred.load()
    engine = pred._engine
    pred.close()
    assert engine.closed and pred._engine is None
