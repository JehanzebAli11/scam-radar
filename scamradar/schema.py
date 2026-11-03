"""The compact output schema the model emits: ``label|scam_type|flags``.

Examples::

    scam|code_theft|asks_for_code,urgency
    safe|none|none
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum

NONE = "none"
SEP = "|"
FLAG_SEP = ","


class Label(StrEnum):
    SAFE = "safe"
    PROMO = "promo"
    SCAM = "scam"


class ScamType(StrEnum):
    FAKE_GOVT_AID = "fake_govt_aid"
    FAKE_PRIZE = "fake_prize"
    CODE_THEFT = "code_theft"
    BANK_OR_WALLET_IMPERSONATION = "bank_or_wallet_impersonation"
    FAMILY_EMERGENCY = "family_emergency"
    FAKE_JOB_OR_INVESTMENT = "fake_job_or_investment"
    PHISHING_LINK = "phishing_link"
    OTHER = "other"


class Flag(StrEnum):
    ASKS_FOR_CODE = "asks_for_code"
    ASKS_FOR_MONEY = "asks_for_money"
    HAS_LINK = "has_link"
    URGENCY = "urgency"
    CLAIMS_OFFICIAL = "claims_official"


LABELS: tuple[str, ...] = tuple(m.value for m in Label)
SCAM_TYPES: tuple[str, ...] = tuple(m.value for m in ScamType)
#: Canonical flag order is the declaration order above.
FLAG_ORDER: tuple[str, ...] = tuple(m.value for m in Flag)
_FLAG_RANK = {f: i for i, f in enumerate(FLAG_ORDER)}


class SchemaError(ValueError):
    """Raised when a compact line cannot be parsed into a valid prediction."""

    def __init__(self, errors: list[str]):
        super().__init__("; ".join(errors))
        self.errors = errors


@dataclass(frozen=True, slots=True)
class Prediction:
    label: Label
    scam_type: ScamType | None = None
    flags: tuple[Flag, ...] = ()

    @property
    def is_scam(self) -> bool:
        return self.label is Label.SCAM

    def to_line(self) -> str:
        return format_line(self)


def canonical_flags(flags: list[str] | tuple[str, ...] | set[str]) -> tuple[Flag, ...]:
    """Deduplicate and sort flags into canonical order. Unknown flags raise ``ValueError``."""
    uniq = {Flag(f) for f in flags}
    return tuple(sorted(uniq, key=lambda f: _FLAG_RANK[f.value]))


def validate_line(line: str) -> list[str]:
    """Strictly validate a compact line. Returns a list of errors (empty means valid).

    Strict means: exactly three ``|``-separated fields, lowercase, no whitespace,
    scam_type set iff label is scam, flags known, unique and in canonical order,
    and ``none`` used alone.
    """
    errors: list[str] = []
    if line != line.strip():
        errors.append("leading/trailing whitespace")
    parts = line.strip().split(SEP)
    if len(parts) != 3:
        return errors + [f"expected 3 fields separated by '|', got {len(parts)}"]
    label, scam_type, flags = parts

    if label not in LABELS:
        errors.append(f"unknown label {label!r}")
    if label == Label.SCAM:
        if scam_type == NONE:
            errors.append("label 'scam' requires a scam_type")
        elif scam_type not in SCAM_TYPES:
            errors.append(f"unknown scam_type {scam_type!r}")
    elif label in LABELS and scam_type != NONE:
        errors.append(f"scam_type must be 'none' when label is {label!r}")

    if flags == "":
        errors.append("flags field is empty (use 'none')")
    elif flags != NONE:
        items = flags.split(FLAG_SEP)
        unknown = [f for f in items if f not in _FLAG_RANK]
        if NONE in items:
            errors.append("'none' cannot be combined with other flags")
        unknown = [f for f in unknown if f != NONE]
        if unknown:
            errors.append(f"unknown flags {unknown}")
        if len(set(items)) != len(items):
            errors.append("duplicate flags")
        known = [f for f in items if f in _FLAG_RANK]
        if not unknown and len(set(items)) == len(items) and known != sorted(
            known, key=_FLAG_RANK.__getitem__
        ):
            errors.append(f"flags not in canonical order {list(FLAG_ORDER)}")
    return errors


def parse_line(line: str, *, strict: bool = True) -> Prediction:
    """Parse a compact line.

    With ``strict=True`` any deviation raises :class:`SchemaError`. With
    ``strict=False`` the parser is lenient about whitespace, case, a trailing
    explanation after the first line, and flag order/duplicates (useful for
    model outputs), but still rejects unknown values.
    """
    if strict:
        errors = validate_line(line)
        if errors:
            raise SchemaError(errors)
        text = line
    else:
        stripped = line.strip().strip("`").strip()
        text = stripped.splitlines()[0].strip().lower() if stripped else ""
        text = SEP.join(p.strip() for p in text.split(SEP))
    parts = text.split(SEP)
    if len(parts) != 3:
        raise SchemaError([f"expected 3 fields separated by '|', got {len(parts)}"])
    label_s, type_s, flags_s = parts
    try:
        label = Label(label_s)
        scam_type = None if type_s == NONE else ScamType(type_s)
        raw_flags = [] if flags_s in (NONE, "") else [f.strip() for f in flags_s.split(FLAG_SEP)]
        flags = canonical_flags([f for f in raw_flags if f])
    except ValueError as exc:
        raise SchemaError([str(exc)]) from exc
    if label is Label.SCAM and scam_type is None:
        raise SchemaError(["label 'scam' requires a scam_type"])
    if label is not Label.SCAM and scam_type is not None:
        if strict:
            raise SchemaError([f"scam_type must be 'none' when label is {label.value!r}"])
        scam_type = None
    return Prediction(label=label, scam_type=scam_type, flags=flags)


def format_line(pred: Prediction) -> str:
    """Format a prediction as a canonical compact line."""
    if pred.label is Label.SCAM and pred.scam_type is None:
        raise SchemaError(["label 'scam' requires a scam_type"])
    stype = pred.scam_type.value if (pred.label is Label.SCAM and pred.scam_type) else NONE
    flags = canonical_flags([f.value for f in pred.flags])
    flag_s = FLAG_SEP.join(f.value for f in flags) if flags else NONE
    return SEP.join((pred.label.value, stype, flag_s))


def make_line(label: str, scam_type: str | None = None, flags: list[str] | None = None) -> str:
    """Convenience: build a canonical line from plain strings."""
    pred = Prediction(
        label=Label(label),
        scam_type=ScamType(scam_type) if scam_type and scam_type != NONE else None,
        flags=canonical_flags(flags or []),
    )
    return format_line(pred)
