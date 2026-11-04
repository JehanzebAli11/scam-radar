"""Run an exported ``.litertlm`` file directly, the same way the Android app does.

Used to compare exports (e.g. int8 vs int4, or a smaller ``max_num_tokens``)
on accuracy *and* cost before shipping one: predictions go through the normal
eval harness, and :class:`RunStats` records file size, memory, load time and
latency for the same run.

Needs the optional LiteRT-LM Python API (``pip install -e ".[litertlm]"``),
which is imported inside :meth:`LiteRtLmPredictor.load` so the rest of the
package and the tests work without it. The prompt is exactly the training
prompt, one fresh conversation per message, greedy decoding, matching
``app/android/.../llm/LiteRtClassifier.kt``.
"""

from __future__ import annotations

import os
import resource
import sys
import time
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any

from scamradar.predictor import PredictionRow
from scamradar.prompting import user_content
from scamradar.schema import SchemaError, format_line, parse_line

#: Same cap as the app (LiteRtClassifier.MAX_NUM_TOKENS): message + answer fit easily.
DEFAULT_MAX_NUM_TOKENS = 512
#: The longest compact line is well under this many tokens.
DEFAULT_MAX_OUTPUT_TOKENS = 48


def _rss_mb() -> float | None:
    """Current resident memory of this process in MB (Linux /proc), else None."""
    try:
        for line in Path("/proc/self/status").read_text().splitlines():
            if line.startswith("VmRSS:"):
                return int(line.split()[1]) / 1024
    except OSError:
        pass
    return None


def _peak_rss_mb() -> float:
    """Peak resident memory of this process in MB (ru_maxrss is KB on Linux, bytes on macOS)."""
    peak = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
    return peak / (1024 * 1024) if sys.platform == "darwin" else peak / 1024


def _percentile(values: list[float], q: float) -> float | None:
    if not values:
        return None
    s = sorted(values)
    return s[min(len(s) - 1, round(q * (len(s) - 1)))]


@dataclass
class RunStats:
    """Cost of one run. Memory is for this whole Python process, model included."""

    model_path: str
    model_file_mb: float
    max_num_tokens: int | None
    backend: str
    load_s: float = 0.0
    rss_before_load_mb: float | None = None
    rss_after_load_mb: float | None = None
    peak_rss_mb: float | None = None
    messages: int = 0
    latency_ms: list[float] = field(default_factory=list, repr=False)

    def summary(self) -> dict[str, Any]:
        d = asdict(self)
        lat = d.pop("latency_ms")
        d["latency_ms_mean"] = round(sum(lat) / len(lat), 1) if lat else None
        d["latency_ms_p50"] = _percentile(lat, 0.5)
        d["latency_ms_p95"] = _percentile(lat, 0.95)
        if self.rss_before_load_mb is not None and self.rss_after_load_mb is not None:
            d["model_rss_mb"] = round(self.rss_after_load_mb - self.rss_before_load_mb, 1)
        return d


@dataclass
class LiteRtLmPredictor:
    model_path: str
    max_num_tokens: int | None = DEFAULT_MAX_NUM_TOKENS
    max_output_tokens: int = DEFAULT_MAX_OUTPUT_TOKENS
    backend: str = "cpu"
    name: str = "litertlm"
    #: Injected in tests; otherwise ``import litert_lm`` on first use.
    module: Any = None
    stats: RunStats | None = None
    _engine: Any = None

    def load(self) -> None:
        if self._engine is not None:
            return
        if self.module is None:
            try:
                import litert_lm
            except ImportError as exc:  # pragma: no cover - depends on the environment
                raise SystemExit(
                    'LiteRT-LM Python API not installed: pip install -e ".[litertlm]"'
                ) from exc
            self.module = litert_lm
        lm = self.module
        self.stats = RunStats(
            model_path=self.model_path,
            model_file_mb=round(os.path.getsize(self.model_path) / (1024 * 1024), 1),
            max_num_tokens=self.max_num_tokens,
            backend=self.backend,
            rss_before_load_mb=_rss_mb(),
        )
        backend = lm.Backend.GPU() if self.backend == "gpu" else lm.Backend.CPU()
        t0 = time.perf_counter()
        self._engine = lm.Engine(
            self.model_path, backend=backend, max_num_tokens=self.max_num_tokens
        )
        self.stats.load_s = round(time.perf_counter() - t0, 3)
        self.stats.rss_after_load_mb = _rss_mb()

    def close(self) -> None:
        if self._engine is not None:
            self._engine.close()
            self._engine = None

    def classify(self, text: str) -> str:
        """Raw model output for one message (fresh conversation, greedy)."""
        self.load()
        lm = self.module
        sampler = lm.SamplerConfig(top_k=1, top_p=1.0, temperature=1.0)
        with self._engine.create_conversation(sampler_config=sampler) as conversation:
            reply = conversation.send_message(
                user_content(text), max_output_tokens=self.max_output_tokens
            )
        return str(reply.contents)

    def predict(self, texts: list[str]) -> list[PredictionRow]:
        self.load()
        assert self.stats is not None
        rows = []
        for text in texts:
            t0 = time.perf_counter()
            raw = self.classify(text)
            ms = (time.perf_counter() - t0) * 1000
            self.stats.latency_ms.append(round(ms, 2))
            try:
                line = format_line(parse_line(raw, strict=False))
            except SchemaError:
                line = raw.strip().splitlines()[0] if raw.strip() else ""
            rows.append(PredictionRow(line, ms))
        self.stats.messages += len(texts)
        self.stats.peak_rss_mb = round(_peak_rss_mb(), 1)
        return rows
