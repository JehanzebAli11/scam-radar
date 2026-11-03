from __future__ import annotations

import json
from pathlib import Path

import httpx
import pytest

from scamradar.factory.datasheet import build_datasheet, datasheet_markdown, guess_language
from scamradar.factory.generate import GenerateConfig, generate_from_seeds, parse_variants
from scamradar.factory.label import LabelConfig, label_records, label_text
from scamradar.factory.prompts import (
    CATEGORY_GUIDANCE,
    HARD_NEGATIVE_CATEGORIES,
    generation_prompt,
    label_prompt,
)
from scamradar.factory.teacher import (
    DEFAULT_BASE_URL,
    DEFAULT_TEACHER_MODEL,
    ChatClient,
    ForbiddenEndpointError,
    OpenAICompatibleClient,
    check_endpoint_allowed,
    prompt_hash,
)
from scamradar.io import read_records
from scamradar.schema import SCAM_TYPES, validate_line

# --- teacher client ---------------------------------------------------------


def test_defaults() -> None:
    assert DEFAULT_BASE_URL == "http://localhost:8000/v1"
    assert DEFAULT_TEACHER_MODEL == "google/gemma-4-31b-it"


def test_http_client_speaks_openai_chat_completions() -> None:
    seen: dict = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["url"] = str(request.url)
        seen["body"] = json.loads(request.content)
        seen["auth"] = request.headers.get("authorization")
        return httpx.Response(200, json={"choices": [{"message": {"content": "safe|none|none"}}]})

    client = OpenAICompatibleClient(
        base_url="http://spark.local:8000/v1/",
        model="my/teacher",
        api_key="k",
        transport=httpx.MockTransport(handler),
    )
    assert isinstance(client, ChatClient)
    out = client.chat([{"role": "user", "content": "hi"}], temperature=0.2, max_tokens=8)
    assert out == "safe|none|none"
    assert seen["url"] == "http://spark.local:8000/v1/chat/completions"
    assert seen["body"]["model"] == "my/teacher"
    assert seen["body"]["temperature"] == 0.2 and seen["body"]["max_tokens"] == 8
    assert seen["auth"] == "Bearer k"


def test_http_client_bad_shape_and_http_error() -> None:
    bad = OpenAICompatibleClient(
        transport=httpx.MockTransport(lambda r: httpx.Response(200, json={}))
    )
    with pytest.raises(ValueError):
        bad.chat([{"role": "user", "content": "x"}])
    err = OpenAICompatibleClient(transport=httpx.MockTransport(lambda r: httpx.Response(500)))
    with pytest.raises(httpx.HTTPStatusError):
        err.chat([{"role": "user", "content": "x"}])


@pytest.mark.parametrize(
    "url",
    [
        "https://api.anthropic.com/v1",
        "https://api.openai.com/v1",
        "https://generativelanguage.googleapis.com/v1beta/openai",
        "https://myres.openai.azure.com/openai",
    ],
)
def test_proprietary_endpoints_refused(url: str) -> None:
    with pytest.raises(ForbiddenEndpointError):
        check_endpoint_allowed(url)
    with pytest.raises(ForbiddenEndpointError):
        OpenAICompatibleClient(base_url=url)


def test_from_env(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("SCAMRADAR_TEACHER_BASE_URL", "http://127.0.0.1:9000/v1")
    monkeypatch.setenv("SCAMRADAR_TEACHER_MODEL", "x/y")
    c = OpenAICompatibleClient.from_env()
    assert (c.base_url, c.model) == ("http://127.0.0.1:9000/v1", "x/y")
    assert OpenAICompatibleClient.from_env(model="override").model == "override"


def test_prompt_hash_stable() -> None:
    m = [{"role": "user", "content": "x"}]
    assert prompt_hash(m) == prompt_hash(list(m))
    assert prompt_hash(m) != prompt_hash([{"role": "user", "content": "y"}])
    assert len(prompt_hash(m)) == 16


# --- prompts ----------------------------------------------------------------


def test_every_scam_type_and_hard_negative_has_guidance() -> None:
    for t in SCAM_TYPES:
        assert t in CATEGORY_GUIDANCE
    assert set(HARD_NEGATIVE_CATEGORIES) == {
        "hard_negative_bank_otp",
        "hard_negative_bisp_8171",
        "hard_negative_courier_code",
        "hard_negative_telco_promo",
    }
    with pytest.raises(KeyError):
        generation_prompt("nope", "x", "english", 3)


def test_label_prompt_few_shots_are_valid() -> None:
    msgs = label_prompt("hello")
    assert msgs[0]["role"] == "system" and msgs[-1]["content"].endswith("hello")
    for m in msgs:
        if m["role"] == "assistant":
            assert validate_line(m["content"]) == []


# --- seeds ------------------------------------------------------------------


def test_seed_file_covers_all_categories_and_languages(seeds_path: Path) -> None:
    seeds = read_records(seeds_path)
    cats = {s["category"] for s in seeds}
    assert cats == set(CATEGORY_GUIDANCE)
    for cat in cats:
        assert sum(s["category"] == cat for s in seeds) >= 3, cat
    assert {s["lang"] for s in seeds} == {"roman_urdu", "urdu", "english", "mixed"}
    for s in seeds:
        assert s["fictional"] is True
        assert validate_line(s["expected_label"]) == [], s["id"]
        assert "label" not in s, "seed labels must come from the teacher, not the seed file"


# --- generate ---------------------------------------------------------------


def test_parse_variants_strips_numbering() -> None:
    raw = '1. "First message here"\n2) second message here\n- third one here\n\n```\nok'
    assert parse_variants(raw) == ["First message here", "second message here", "third one here"]


def test_generate_with_fake_teacher(seeds_path: Path, fake_teacher_cls) -> None:
    seeds = [
        s
        for s in read_records(seeds_path)
        if s["category"] in ("code_theft", "hard_negative_bank_otp")
    ][:4]

    def fn(messages):
        cat = messages[1]["content"].split("\n", 1)[0]
        return "\n".join(f"{i + 1}. variant {i} for {cat}" for i in range(5))

    teacher = fake_teacher_cls(fn=fn)
    out = list(generate_from_seeds(seeds, teacher, GenerateConfig(n_per_seed=3)))
    assert len(out) == 3 * len(seeds)
    assert len(teacher.calls) == len(seeds)
    assert all(c["temperature"] == 0.9 for c in teacher.calls)
    for rec in out:
        assert rec["source"] == "teacher_generated"
        assert rec["generator_model"] == "fake/teacher-1b"
        assert len(rec["generator_prompt_hash"]) == 16
        assert "label" not in rec
    otp = [r for r in out if r["category"] == "hard_negative_bank_otp"]
    assert otp and all("otp" in r["tags"] and "hard_negative" in r["tags"] for r in otp)
    assert len({r["id"] for r in out}) == len(out)


def test_generate_rejects_unknown_category(fake_teacher_cls) -> None:
    with pytest.raises(ValueError):
        list(generate_from_seeds([{"id": "x", "text": "t", "category": "zzz"}], fake_teacher_cls()))


# --- label ------------------------------------------------------------------


def test_label_text_canonicalises(fake_teacher_cls) -> None:
    t = fake_teacher_cls(["SCAM|code_theft|urgency,asks_for_code\n(reason...)"])
    out = label_text("x", t)
    assert out.line == "scam|code_theft|asks_for_code,urgency" and out.attempts == 1


def test_label_text_retries_then_succeeds(fake_teacher_cls) -> None:
    t = fake_teacher_cls(["I think it's a scam", httpx.ConnectError("down"), "safe|none|none"])
    out = label_text("x", t, LabelConfig(max_retries=3))
    assert out.line == "safe|none|none"
    assert out.attempts == 3 and len(out.errors) == 2
    assert [c["temperature"] for c in t.calls] == [0.0, 0.3, 0.3]


def test_label_text_gives_up(fake_teacher_cls) -> None:
    t = fake_teacher_cls(["nope", "scam|none|none"])
    out = label_text("x", t, LabelConfig(max_retries=2))
    assert out.line is None and len(out.errors) == 2


def test_label_records_provenance_and_rejects(fake_teacher_cls) -> None:
    recs = [{"id": "a", "text": "one"}, {"id": "b", "text": "two"}]
    t = fake_teacher_cls(["promo|none|none", "bad", "bad", "bad"])
    rejects: list = []
    out = list(label_records(recs, t, rejects=rejects))
    assert [r["id"] for r in out] == ["a"]
    assert out[0]["label"] == "promo|none|none"
    assert out[0]["teacher_model"] == "fake/teacher-1b"
    assert out[0]["label_source"] == "teacher" and len(out[0]["teacher_prompt_hash"]) == 16
    assert rejects[0]["id"] == "b" and len(rejects[0]["label_errors"]) == 3


# --- datasheet --------------------------------------------------------------


@pytest.mark.parametrize(
    ("text", "lang"),
    [
        ("Ammi main ghar 8 baje tak aa jaunga, khana rakh dena", "roman_urdu"),
        ("بیٹا کل شادی میں وقت پر پہنچ جانا", "urdu"),
        ("Your parcel will be delivered today between 2 and 5 pm", "english"),
        ("Reminder: your appointment kal subah hai at the clinic", "mixed"),
        ("12345", "unknown"),
    ],
)
def test_guess_language(text: str, lang: str) -> None:
    assert guess_language(text) == lang


def test_datasheet_counts(labelled: list[dict]) -> None:
    labelled[0]["teacher_model"] = "fake/teacher"
    labelled[1]["label"] = "garbage"
    sheet = build_datasheet(labelled + [{"id": "u", "text": "hi"}], name="fx")
    assert sheet["n_records"] == 33
    assert sheet["invalid_labels"] == 1
    assert sheet["labels"] == {"promo": 6, "safe": 10, "scam": 15, "unlabelled": 1}
    assert sheet["scam_types"]["code_theft"] == 3
    assert sheet["tags"] == {"otp": 4}
    assert sheet["provenance"]["label_models"] == {"fake/teacher": 1}
    assert sum(sheet["languages"].values()) == 33
    md = datasheet_markdown(sheet)
    assert "# Datasheet: fx" in md and "| scam | 15 |" in md
