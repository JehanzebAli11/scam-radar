# AGENTS.md

Instructions for AI coding agents (and humans) working in this repo.
`CLAUDE.md` repeats the hard rules; this file is the full version.

## What this repo is

Data, training and evaluation code for Scam Radar, an offline Android app
where a fine-tuned `google/gemma-3-270m-it` labels a phone message
(SMS/WhatsApp; Roman Urdu, Urdu script, English, code-mixed) as
`label|scam_type|flags`. Heavy jobs run on the owner's NVIDIA DGX Spark
(ARM64, CUDA). Everything must also be testable on a CPU-only machine with no
Hugging Face access.

## Hard rules (never break these)

1. **Training data and labels come only from an open-weight teacher model
   served locally** via an OpenAI-compatible endpoint (default
   `http://localhost:8000/v1`, model `google/gemma-4-31b-it`, both
   configurable), **or from real consented messages.** Never call
   Claude/Anthropic, OpenAI or Gemini APIs anywhere in this repo: no SDK
   imports, no hosted endpoints, no "just for a quick label". The teacher
   client refuses those hosts and a test scans the package for their SDKs.
2. **`eval/locked/` holds the real human-labelled test set.** Code never writes
   to it (the one exception is `scamradar split` creating a new file there,
   exclusive-create, refusing to overwrite). Only the eval harness reads it.
   It is git-ignored; only its `README.md` is tracked. Never use it for
   few-shot examples, prompt tuning, thresholds or error analysis that feeds
   training.
3. **No real personal data in the repo.** All fixtures and seeds are fictional
   (fictional brands such as FictionBank/ZetaPay/NovaTel, placeholder numbers
   like `0300-0000000`). Real messages are anonymised (`scamradar anonymise`)
   and live only under `data/` or `eval/locked/`, both git-ignored.

## Conventions

- Python >= 3.11, typed, small modules. `ruff check .`, `ruff format .`,
  `mypy scamradar` and `pytest` must pass.
- Anything that talks to a model takes an injectable `ChatClient`
  (`scamradar/factory/teacher.py`); tests use `FakeTeacher` from
  `tests/conftest.py` or `httpx.MockTransport`. Tests never touch the network.
- `torch`/`transformers`/`trl` are optional (`.[train]`) and imported inside
  functions. `scamradar train --dry-run` must work without them.
- Use `scamradar.io.read_records` / `write_jsonl`; they enforce the locked-dir
  guard. Only `scamradar/eval/harness.py` passes `allow_locked=True`.
- The compact line format and canonical flag order live in
  `scamradar/schema.py`. The training prompt lives in `scamradar/prompting.py`.
  Change either only together with the eval harness, the export notes and
  the Android app's Kotlin mirror (`app/core/.../Schema.kt`, `Prompt.kt`).
  `tests/test_schema_vectors.py` and the app's `SchemaTest` both check
  `tests/fixtures/schema_vectors.jsonl`; regenerate it with
  `python -m tests.test_schema_vectors` after a schema change.
- The Android app lives in `app/` (Gradle, see `app/README.md`). Its advice
  text is pre-written in `app/core/.../text/AdviceText.kt`; never show
  model-written text to the user. The app has no INTERNET permission.
- Commit with clear messages; never commit data, checkpoints or model files.
