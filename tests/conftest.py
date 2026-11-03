from __future__ import annotations

import json
import shutil
from pathlib import Path

import pytest

FIXTURES = Path(__file__).parent / "fixtures"
REPO = Path(__file__).resolve().parents[1]


@pytest.fixture
def labelled_path(tmp_path: Path) -> Path:
    """A copy of the fictional labelled fixture in a temp dir."""
    dst = tmp_path / "labelled.jsonl"
    shutil.copy(FIXTURES / "labelled.jsonl", dst)
    return dst


@pytest.fixture
def labelled(labelled_path: Path) -> list[dict]:
    return [json.loads(line) for line in labelled_path.read_text(encoding="utf-8").splitlines()]


@pytest.fixture
def seeds_path() -> Path:
    return REPO / "data" / "seeds" / "seeds.example.jsonl"


class FakeTeacher:
    """Scripted ChatClient. ``replies`` is consumed in order, or ``fn`` is called."""

    def __init__(self, replies=None, fn=None, model: str = "fake/teacher-1b"):
        self.replies = list(replies or [])
        self.fn = fn
        self.model = model
        self.calls: list[dict] = []

    def chat(self, messages, *, temperature=0.0, max_tokens=256):
        self.calls.append({"messages": messages, "temperature": temperature})
        if self.fn is not None:
            return self.fn(messages)
        if not self.replies:
            raise AssertionError("FakeTeacher ran out of replies")
        reply = self.replies.pop(0)
        if isinstance(reply, Exception):
            raise reply
        return reply


@pytest.fixture
def fake_teacher_cls():
    return FakeTeacher
