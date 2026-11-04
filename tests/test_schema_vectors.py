"""Shared test vectors for the compact line format.

``tests/fixtures/schema_vectors.jsonl`` is read by this test (against
``scamradar/schema.py``) and by the Android app's Kotlin parser tests
(``app/core/src/test/.../SchemaVectorsTest.kt``), so the two parsers cannot
drift apart silently. Regenerate with::

    python -m tests.test_schema_vectors
"""

from __future__ import annotations

import json
from pathlib import Path

from scamradar.prompting import USER_PREFIX
from scamradar.schema import FLAG_ORDER, LABELS, SCAM_TYPES, SchemaError, parse_line, validate_line

ROOT = Path(__file__).resolve().parents[1]
VECTORS = ROOT / "tests" / "fixtures" / "schema_vectors.jsonl"
KOTLIN_SCHEMA = (
    ROOT
    / "app"
    / "core"
    / "src"
    / "main"
    / "kotlin"
    / "io"
    / "github"
    / "jehanzebali11"
    / "scamradar"
    / "core"
    / "Schema.kt"
)
KOTLIN_PROMPT = KOTLIN_SCHEMA.with_name("Prompt.kt")

LINES = [
    # valid canonical lines
    "safe|none|none",
    "promo|none|none",
    "promo|none|claims_official",
    "scam|code_theft|asks_for_code,urgency",
    "scam|phishing_link|has_link",
    "scam|fake_govt_aid|asks_for_money,urgency,claims_official",
    "scam|other|asks_for_code,asks_for_money,has_link,urgency,claims_official",
    *(f"scam|{t}|none" for t in SCAM_TYPES),
    # strictly invalid, some leniently recoverable
    "scam|none|none",
    "safe|code_theft|none",
    "spam|none|none",
    "scam|lottery|none",
    "safe|none|",
    "safe|none|wat",
    "safe|none|none,urgency",
    "scam|code_theft|urgency,asks_for_code",
    "scam|code_theft|urgency,urgency",
    "safe|none",
    "safe|none|none|x",
    " safe|none|none",
    "SAFE|none|none",
    "Scam | Code_Theft | Urgency , asks_for_code",
    "`scam|fake_prize|asks_for_money`",
    "scam|fake_prize|asks_for_money\nThis message offers a prize.",
    "safe|none|none\n",
    "promo|none|has_link,",
    "scam|family_emergency|asks_for_money,,urgency",
    "",
    "   ",
    "I think this is a scam.",
    "scam|phishing_link|has_link|extra",
]


def _vector(line: str) -> dict[str, object]:
    try:
        lenient: str | None = parse_line(line, strict=False).to_line()
    except SchemaError:
        lenient = None
    return {"line": line, "strict_valid": validate_line(line) == [], "lenient": lenient}


def build_vectors() -> list[dict[str, object]]:
    return [_vector(line) for line in LINES]


def test_vectors_fixture_matches_python_schema() -> None:
    on_disk = [json.loads(x) for x in VECTORS.read_text(encoding="utf-8").splitlines() if x.strip()]
    assert on_disk == build_vectors(), "regenerate with: python -m tests.test_schema_vectors"


def test_kotlin_enums_match_python_schema() -> None:
    """The Android parser declares the same values, flags in canonical order."""
    src = KOTLIN_SCHEMA.read_text(encoding="utf-8")
    positions = [src.index(f'"{f}"') for f in FLAG_ORDER]
    assert positions == sorted(positions), "Kotlin Flag order differs from FLAG_ORDER"
    for value in (*LABELS, *SCAM_TYPES):
        assert f'"{value}"' in src, value


def test_kotlin_prompt_matches_training_prompt() -> None:
    assert f'"{USER_PREFIX}"' in KOTLIN_PROMPT.read_text(encoding="utf-8")


if __name__ == "__main__":
    VECTORS.write_text(
        "".join(json.dumps(v, ensure_ascii=False) + "\n" for v in build_vectors()),
        encoding="utf-8",
    )
    print(f"wrote {VECTORS}")
