from __future__ import annotations

import pytest

from scamradar.schema import (
    FLAG_ORDER,
    Flag,
    Label,
    Prediction,
    ScamType,
    SchemaError,
    canonical_flags,
    format_line,
    make_line,
    parse_line,
    validate_line,
)

VALID = [
    "safe|none|none",
    "promo|none|claims_official",
    "scam|code_theft|asks_for_code,urgency",
    "scam|phishing_link|has_link",
    "scam|other|asks_for_code,asks_for_money,has_link,urgency,claims_official",
]


@pytest.mark.parametrize("line", VALID)
def test_valid_lines_roundtrip(line: str) -> None:
    assert validate_line(line) == []
    assert format_line(parse_line(line)) == line


@pytest.mark.parametrize(
    ("line", "fragment"),
    [
        ("scam|none|none", "requires a scam_type"),
        ("safe|code_theft|none", "must be 'none'"),
        ("spam|none|none", "unknown label"),
        ("scam|lottery|none", "unknown scam_type"),
        ("safe|none|", "empty"),
        ("safe|none|wat", "unknown flags"),
        ("safe|none|none,urgency", "cannot be combined"),
        ("scam|code_theft|urgency,asks_for_code", "canonical order"),
        ("scam|code_theft|urgency,urgency", "duplicate"),
        ("safe|none", "expected 3 fields"),
        ("safe|none|none|x", "expected 3 fields"),
        (" safe|none|none", "whitespace"),
        ("SAFE|none|none", "unknown label"),
    ],
)
def test_invalid_lines_report_errors(line: str, fragment: str) -> None:
    errors = validate_line(line)
    assert errors, line
    assert any(fragment in e for e in errors), errors
    with pytest.raises(SchemaError):
        parse_line(line, strict=True)


def test_lenient_parse_cleans_model_output() -> None:
    raw = "  SCAM | code_theft | urgency,asks_for_code,urgency \nBecause it asks for a code."
    pred = parse_line(raw, strict=False)
    assert pred.label is Label.SCAM
    assert pred.scam_type is ScamType.CODE_THEFT
    assert pred.flags == (Flag.ASKS_FOR_CODE, Flag.URGENCY)
    assert pred.to_line() == "scam|code_theft|asks_for_code,urgency"


def test_lenient_parse_drops_type_on_non_scam_and_strips_backticks() -> None:
    assert parse_line("`safe|fake_prize|none`", strict=False).to_line() == "safe|none|none"


@pytest.mark.parametrize("raw", ["", "scam|none|none", "maybe|none|none", "scam|code_theft|lol"])
def test_lenient_parse_still_rejects_bad_values(raw: str) -> None:
    with pytest.raises(SchemaError):
        parse_line(raw, strict=False)


def test_canonical_flags_order_and_dedupe() -> None:
    flags = canonical_flags(["urgency", "has_link", "asks_for_code", "urgency"])
    assert [f.value for f in flags] == ["asks_for_code", "has_link", "urgency"]
    assert list(FLAG_ORDER) == [
        "asks_for_code",
        "asks_for_money",
        "has_link",
        "urgency",
        "claims_official",
    ]
    with pytest.raises(ValueError):
        canonical_flags(["nope"])


def test_make_line_and_format_errors() -> None:
    assert make_line("scam", "fake_prize", ["urgency", "asks_for_money"]) == (
        "scam|fake_prize|asks_for_money,urgency"
    )
    assert make_line("promo", "none", None) == "promo|none|none"
    with pytest.raises(SchemaError):
        format_line(Prediction(label=Label.SCAM))


def test_format_ignores_scam_type_for_non_scam() -> None:
    pred = Prediction(Label.SAFE, ScamType.OTHER, ())
    assert format_line(pred) == "safe|none|none"
