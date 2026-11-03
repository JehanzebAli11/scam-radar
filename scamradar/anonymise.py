"""Deterministic anonymisation of Pakistani phone messages.

Replaces personal identifiers with placeholders while keeping the signals a
scam classifier needs (URL domains, short codes such as 8171, OTP digits,
amounts):

========================  =======================================
Identifier                Replacement
========================  =======================================
email address             ``<EMAIL>``
URL path / query          ``https://domain.tld/<PATH>`` (domain kept)
PK IBAN                   ``<IBAN>``
CNIC (#####-#######-#)    ``<CNIC>``
mobile / landline number  ``<PHONE>``
names from a given list   ``<NAME>``
========================  =======================================

With ``numbered=True`` placeholders are numbered by first appearance
(``<PHONE_1>``, ``<PHONE_2>``, the same value always maps to the same number
within one message), which keeps "same sender vs different number" signals.
"""

from __future__ import annotations

import re
from collections.abc import Callable, Iterable
from dataclasses import dataclass, field

# --- patterns ---------------------------------------------------------------

EMAIL_RE = re.compile(r"(?<![\w.+-])[\w.+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}\b")

_TLDS = (
    "com|pk|net|org|info|biz|xyz|top|online|site|website|club|shop|store|live|link|"
    "click|ly|co|me|io|app|in|tk|ml|ga|cf|gq|cc|ws|vip|win|buzz|icu|tv|us|uk"
)
URL_RE = re.compile(
    r"(?P<scheme>https?://)?"
    r"(?P<host>(?:www\.)?(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+"
    rf"(?:{_TLDS}))"
    r"(?P<port>:\d{2,5})?"
    r"(?P<path>[/?#][^\s<>\"']*)?",
    re.IGNORECASE,
)
# Require a scheme, "www." or a path for scheme-less matches so we don't touch
# ordinary words like "e.g" or "Rs.500". Checked in the replacement callback.

IBAN_RE = re.compile(
    r"(?<![A-Za-z0-9])PK\d{2}[\s-]?[A-Z]{4}(?:[\s-]?\d{4}){4}(?![A-Za-z0-9])", re.IGNORECASE
)
CNIC_RE = re.compile(r"(?<![\d-])(?:\d{5}-\d{7}-\d|\d{13})(?![\d-])")

_SEP = r"[\s.-]?"
PHONE_RE = re.compile(
    r"(?<![\w+])(?:"
    # mobile: +92 3xx xxxxxxx / 0092 3xx... / 92 3xx...
    rf"(?:\+|00)?92{_SEP}\(?0?\)?{_SEP}3\d{{2}}{_SEP}\d{{3}}{_SEP}?\d{{4}}"
    # mobile: 03xx-xxxxxxx / 03xx xxx xxxx / 03xxxxxxxxx
    rf"|03\d{{2}}{_SEP}\d{{3}}{_SEP}?\d{{4}}"
    # landline with country code: +92 21 34567890 / +92-42-3576-1234
    rf"|(?:\+|00)92{_SEP}\(?[1-9]\d{{1,2}}\)?{_SEP}\d{{3,4}}{_SEP}?\d{{3,4}}"
    # landline with trunk prefix and explicit separator: 021-34567890 / (042) 35761234
    r"|\(?0[1-9]\d{1,2}\)?[\s-]\d{3,4}[\s-]?\d{3,4}"
    r")(?![\d])"
)

PLACEHOLDERS = ("EMAIL", "PATH", "IBAN", "CNIC", "PHONE", "NAME")


@dataclass
class _Counter:
    numbered: bool
    seen: dict[tuple[str, str], int] = field(default_factory=dict)
    totals: dict[str, int] = field(default_factory=dict)

    def tag(self, kind: str, value: str) -> str:
        if not self.numbered:
            return f"<{kind}>"
        key = (kind, re.sub(r"\D", "", value) if kind in ("PHONE", "CNIC") else value.lower())
        if key not in self.seen:
            self.totals[kind] = self.totals.get(kind, 0) + 1
            self.seen[key] = self.totals[kind]
        return f"<{kind}_{self.seen[key]}>"


@dataclass(frozen=True)
class Anonymiser:
    """Configurable, deterministic anonymiser.

    Args:
        names: optional list of personal names to replace with ``<NAME>``
            (matched case-insensitively on word boundaries; any script).
        numbered: number placeholders by first appearance within a message.
    """

    names: tuple[str, ...] = ()
    numbered: bool = False

    def __post_init__(self) -> None:
        cleaned = sorted({n.strip() for n in self.names if n.strip()}, key=len, reverse=True)
        object.__setattr__(self, "names", tuple(cleaned))
        pattern = None
        if cleaned:
            alt = "|".join(re.escape(n) for n in cleaned)
            pattern = re.compile(rf"(?<!\w)(?:{alt})(?!\w)", re.IGNORECASE)
        object.__setattr__(self, "_name_re", pattern)

    def __call__(self, text: str) -> str:
        return self.anonymise(text)

    def anonymise(self, text: str) -> str:
        counter = _Counter(self.numbered)
        text = EMAIL_RE.sub(_sub("EMAIL", counter), text)
        text = URL_RE.sub(_url_sub, text)
        text = IBAN_RE.sub(_sub("IBAN", counter), text)
        text = CNIC_RE.sub(_sub("CNIC", counter), text)
        text = PHONE_RE.sub(_sub("PHONE", counter), text)
        name_re: re.Pattern[str] | None = getattr(self, "_name_re")  # noqa: B009
        if name_re is not None:
            text = name_re.sub(_sub("NAME", counter), text)
        return text


def _sub(kind: str, counter: _Counter) -> Callable[[re.Match[str]], str]:
    def repl(m: re.Match[str]) -> str:
        return counter.tag(kind, m.group(0))

    return repl


def _url_sub(m: re.Match[str]) -> str:
    scheme, host, port, path = m.group("scheme"), m.group("host"), m.group("port"), m.group("path")
    looks_like_url = bool(scheme) or host.lower().startswith("www.") or bool(path)
    if not looks_like_url:
        return m.group(0)
    # Keep trailing sentence punctuation outside the placeholder.
    trailing = ""
    if path:
        stripped = path.rstrip(".,!?;:)")
        trailing = path[len(stripped) :]
        path = stripped
    out = f"{scheme or ''}{host}{port or ''}"
    if path and path not in ("/",):
        out += "/<PATH>"
    elif path == "/":
        out += "/"
    return out + trailing


def anonymise(text: str, names: Iterable[str] = (), *, numbered: bool = False) -> str:
    """Functional shortcut for :class:`Anonymiser`."""
    return Anonymiser(names=tuple(names), numbered=numbered)(text)


def load_names(path: str) -> tuple[str, ...]:
    """Load a names list (one per line, ``#`` comments allowed)."""
    with open(path, encoding="utf-8") as fh:
        return tuple(
            line.strip() for line in fh if line.strip() and not line.lstrip().startswith("#")
        )
