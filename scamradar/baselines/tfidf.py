"""Character n-gram TF-IDF + logistic regression baseline.

Three heads, all on the same features:

* ``label``: safe / promo / scam (trained on all records)
* ``scam_type``: trained on scam records only, applied when label is scam
* ``flags``: one binary classifier per flag that has both classes in training
"""

from __future__ import annotations

import os
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import joblib
import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression

from scamradar.io import Record
from scamradar.paths import ensure_not_locked
from scamradar.predictor import PredictionRow
from scamradar.schema import FLAG_ORDER, make_line, parse_line

FORMAT_VERSION = 1


@dataclass(frozen=True)
class TfidfConfig:
    ngram_min: int = 2
    ngram_max: int = 5
    min_df: int = 1
    max_features: int | None = 200_000
    C: float = 4.0
    class_weight: str | None = "balanced"
    seed: int = 13


class _Constant:
    """Stand-in classifier when a head has a single class in training."""

    def __init__(self, value: Any):
        self.value = value

    def predict(self, x: Any) -> np.ndarray:
        return np.array([self.value] * x.shape[0], dtype=object)


@dataclass
class TfidfBaseline:
    config: TfidfConfig = field(default_factory=TfidfConfig)
    name: str = "tfidf"
    vectorizer: TfidfVectorizer | None = None
    label_clf: Any = None
    type_clf: Any = None
    flag_clfs: dict[str, Any] = field(default_factory=dict)

    def _lr(self) -> LogisticRegression:
        return LogisticRegression(
            C=self.config.C,
            class_weight=self.config.class_weight,
            max_iter=2000,
            random_state=self.config.seed,
        )

    def _fit_head(self, x: Any, y: list[Any]) -> Any:
        if len(set(y)) < 2:
            return _Constant(y[0])
        return self._lr().fit(x, y)

    def fit(self, records: list[Record]) -> TfidfBaseline:
        if not records:
            raise ValueError("no training records")
        texts = [r["text"] for r in records]
        preds = [parse_line(str(r["label"])) for r in records]
        cfg = self.config
        self.vectorizer = TfidfVectorizer(
            analyzer="char_wb",
            ngram_range=(cfg.ngram_min, cfg.ngram_max),
            min_df=cfg.min_df,
            max_features=cfg.max_features,
            sublinear_tf=True,
            lowercase=True,
        )
        x = self.vectorizer.fit_transform(texts)
        self.label_clf = self._fit_head(x, [p.label.value for p in preds])
        scam_idx = [i for i, p in enumerate(preds) if p.scam_type is not None]
        types = [preds[i].scam_type.value for i in scam_idx]  # type: ignore[union-attr]
        self.type_clf = self._fit_head(x[scam_idx], types) if scam_idx else _Constant("other")
        self.flag_clfs = {}
        for flag in FLAG_ORDER:
            y = [int(any(f.value == flag for f in p.flags)) for p in preds]
            self.flag_clfs[flag] = self._fit_head(x, y)
        return self

    def predict_lines(self, texts: list[str]) -> list[str]:
        if self.vectorizer is None:
            raise RuntimeError("model is not fitted")
        x = self.vectorizer.transform(texts)
        labels = self.label_clf.predict(x)
        types = self.type_clf.predict(x)
        flag_preds = {f: clf.predict(x) for f, clf in self.flag_clfs.items()}
        lines = []
        for i, label in enumerate(labels):
            flags = [f for f in FLAG_ORDER if int(flag_preds[f][i]) == 1]
            stype = str(types[i]) if label == "scam" else None
            lines.append(make_line(str(label), stype, flags))
        return lines

    def predict(self, texts: list[str]) -> list[PredictionRow]:
        out = []
        for text in texts:
            t0 = time.perf_counter()
            line = self.predict_lines([text])[0]
            out.append(PredictionRow(line, (time.perf_counter() - t0) * 1000))
        return out

    def save(self, path: str | os.PathLike[str]) -> None:
        ensure_not_locked(path, "write")
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        joblib.dump({"format_version": FORMAT_VERSION, "model": self}, path)

    @classmethod
    def load(cls, path: str | os.PathLike[str]) -> TfidfBaseline:
        blob = joblib.load(path)
        if not isinstance(blob, dict) or blob.get("format_version") != FORMAT_VERSION:
            raise ValueError(f"{path} is not a scam-radar TF-IDF baseline (v{FORMAT_VERSION})")
        model = blob["model"]
        if not isinstance(model, cls):
            raise TypeError(f"{path} does not contain a {cls.__name__}")
        return model
