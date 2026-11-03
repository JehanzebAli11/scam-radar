"""Dataset datasheet: counts per label / scam type / language and provenance."""

from __future__ import annotations

import re
from collections import Counter
from collections.abc import Sequence
from datetime import UTC, datetime
from typing import Any

from scamradar.io import Record
from scamradar.schema import SchemaError, parse_line

_URDU_SCRIPT = re.compile(r"[؀-ۿݐ-ݿﭐ-﷿ﹰ-﻿]")
_LATIN = re.compile(r"[A-Za-z]")
_WORD = re.compile(r"[A-Za-z']+")

#: Frequent Roman Urdu function words; enough for a coarse language guess.
ROMAN_URDU_MARKERS = frozenset(
    """
    aap ap apka apke apki aapka aapke aapki hai hain hy he ho hoga hogi ka ke ki ko
    se mein main mai mujhe mera meri mere tum tumhara kya kia nahi nai nahin bhi
    bhai behan ammi abbu jaldi abhi kar karo karein kardo krdo raha rahi rahe tha thi
    the wala wali wale lekin aur ya agar to phir kal aaj bohat bahut yeh ye woh wo
    shukriya mubarak raqam paise bhejein bhejo bhej dein dein liye lie hum hamara
    """.split()
)


def guess_language(text: str) -> str:
    """Coarse guess: ``urdu`` (script), ``roman_urdu``, ``english`` or ``mixed``."""
    urdu_chars = len(_URDU_SCRIPT.findall(text))
    latin_chars = len(_LATIN.findall(text))
    if urdu_chars and urdu_chars >= latin_chars:
        return "urdu"
    words = [w.lower() for w in _WORD.findall(text)]
    if not words:
        return "urdu" if urdu_chars else "unknown"
    ru = sum(w in ROMAN_URDU_MARKERS for w in words) / len(words)
    if urdu_chars:
        return "mixed"
    if ru >= 0.25:
        return "roman_urdu"
    if ru >= 0.08:
        return "mixed"
    return "english"


def build_datasheet(records: Sequence[Record], *, name: str = "dataset") -> dict[str, Any]:
    labels: Counter[str] = Counter()
    types: Counter[str] = Counter()
    flags: Counter[str] = Counter()
    langs: Counter[str] = Counter()
    sources: Counter[str] = Counter()
    teachers: Counter[str] = Counter()
    prompt_hashes: Counter[str] = Counter()
    tags: Counter[str] = Counter()
    invalid = 0
    for rec in records:
        langs[str(rec.get("lang") or guess_language(rec["text"]))] += 1
        sources[str(rec.get("source", "unknown"))] += 1
        for key in ("teacher_model", "generator_model"):
            if rec.get(key):
                teachers[str(rec[key])] += 1
        for key in ("teacher_prompt_hash", "generator_prompt_hash"):
            if rec.get(key):
                prompt_hashes[str(rec[key])] += 1
        for t in rec.get("tags", []) or []:
            tags[str(t)] += 1
        if "label" not in rec:
            labels["unlabelled"] += 1
            continue
        try:
            pred = parse_line(str(rec["label"]))
        except SchemaError:
            invalid += 1
            continue
        labels[pred.label.value] += 1
        if pred.scam_type is not None:
            types[pred.scam_type.value] += 1
        for f in pred.flags:
            flags[f.value] += 1
    return {
        "name": name,
        "created_utc": datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "n_records": len(records),
        "invalid_labels": invalid,
        "labels": dict(sorted(labels.items())),
        "scam_types": dict(sorted(types.items())),
        "flags": dict(sorted(flags.items())),
        "languages": dict(sorted(langs.items())),
        "tags": dict(sorted(tags.items())),
        "provenance": {
            "sources": dict(sorted(sources.items())),
            "models": dict(sorted(teachers.items())),
            "prompt_hashes": dict(sorted(prompt_hashes.items())),
        },
    }


def datasheet_markdown(sheet: dict[str, Any]) -> str:
    lines = [f"# Datasheet: {sheet['name']}", "", f"Records: **{sheet['n_records']}**", ""]
    for section in ("labels", "scam_types", "flags", "languages", "tags"):
        items = sheet.get(section) or {}
        if not items:
            continue
        lines += [f"## {section.replace('_', ' ').title()}", "", "| value | count |", "|---|---|"]
        lines += [f"| {k} | {v} |" for k, v in items.items()]
        lines.append("")
    prov = sheet["provenance"]
    lines += ["## Provenance", ""]
    lines += [f"- source `{k}`: {v}" for k, v in prov["sources"].items()]
    lines += [f"- model `{k}`: {v}" for k, v in prov["models"].items()]
    return "\n".join(lines) + "\n"
