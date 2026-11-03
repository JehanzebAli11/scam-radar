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

#: Frequent Roman Urdu words; enough for a coarse language guess.
ROMAN_URDU_MARKERS = frozenset(
    """
    aap ap apka apke apki aapka aapke aapki hai hain hy he ho hoga hogi ka ke ki ko
    se mein mai mujhe mera meri mere tum tumhara kya kia nahi nai nahin bhi
    bhai behan ammi abbu jaldi abhi kar karo karein kardo krdo raha rahi rahe tha thi
    the wala wali wale lekin aur ya agar to phir kal aaj bohat bahut yeh ye woh wo
    shukriya mubarak raqam paise bhejein bhejo bhej dein dein liye lie hum hamara
    ghar tak aa ja jaunga jayega khana dena lena rakh baje subah shaam raat sirf
    gaya gayi hua hui wapis zaroori tareekh main
    """.split()
)
#: Frequent English words (minus any that are also Roman Urdu, e.g. "to", "the").
ENGLISH_MARKERS = (
    frozenset(
        """
    the a an your you is are was will be to for and of at on in this that today
    please now with from has have been our we by between it not do can my i if
    or as get click call dear customer account online job support verification
    congratulations offer bundle internet details submit refund help order delivery
    track website free upgrade sale deal reminder appointment cancel register
    """.split()
    )
    - ROMAN_URDU_MARKERS
)


def guess_language(text: str) -> str:
    """Coarse guess: ``urdu`` (script), ``roman_urdu``, ``english``, ``mixed`` or ``unknown``."""
    urdu_chars = len(_URDU_SCRIPT.findall(text))
    latin_chars = len(_LATIN.findall(text))
    if urdu_chars and urdu_chars >= latin_chars:
        return "urdu"
    if urdu_chars:
        return "mixed"
    words = [w.lower() for w in _WORD.findall(text)]
    if not words:
        return "unknown"
    ru = sum(w in ROMAN_URDU_MARKERS for w in words)
    en = sum(w in ENGLISH_MARKERS for w in words)
    if ru + en == 0:
        return "english"
    share = ru / (ru + en)
    if share >= 0.75:
        return "roman_urdu"
    if share <= 0.2:
        return "english"
    return "mixed"


def build_datasheet(records: Sequence[Record], *, name: str = "dataset") -> dict[str, Any]:
    labels: Counter[str] = Counter()
    types: Counter[str] = Counter()
    flags: Counter[str] = Counter()
    langs: Counter[str] = Counter()
    sources: Counter[str] = Counter()
    label_models: Counter[str] = Counter()
    gen_models: Counter[str] = Counter()
    label_prompts: Counter[str] = Counter()
    gen_prompts: Counter[str] = Counter()
    tags: Counter[str] = Counter()
    invalid = 0
    for rec in records:
        langs[str(rec.get("lang") or guess_language(rec["text"]))] += 1
        sources[str(rec.get("source", "unknown"))] += 1
        for key, counter in (
            ("teacher_model", label_models),
            ("generator_model", gen_models),
            ("teacher_prompt_hash", label_prompts),
            ("generator_prompt_hash", gen_prompts),
        ):
            if rec.get(key):
                counter[str(rec[key])] += 1
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
            "label_models": dict(sorted(label_models.items())),
            "generator_models": dict(sorted(gen_models.items())),
            "label_prompt_hashes": dict(sorted(label_prompts.items())),
            "generator_prompt_hashes": dict(sorted(gen_prompts.items())),
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
    lines += [f"- labelled by `{k}`: {v}" for k, v in prov["label_models"].items()]
    lines += [f"- generated by `{k}`: {v}" for k, v in prov["generator_models"].items()]
    return "\n".join(lines) + "\n"
