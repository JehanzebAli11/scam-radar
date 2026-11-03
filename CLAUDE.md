# CLAUDE.md

Read `AGENTS.md` for full conventions. The hard rules, repeated here:

1. Training data and labels come **only** from an open-weight teacher served
   locally via an OpenAI-compatible endpoint (default
   `http://localhost:8000/v1`, model `google/gemma-4-31b-it`), or from real
   consented messages. **Never call Claude/Anthropic, OpenAI or Gemini APIs
   anywhere in this repo.**
2. `eval/locked/` is the real human-labelled test set. Never write to it
   (except `scamradar split` creating it once); only the eval harness reads it.
   It is git-ignored apart from its README.
3. No real personal data in the repo. Fixtures and seeds are fictional.

Before finishing any change: `ruff check . && ruff format --check . && mypy scamradar && pytest`.

@AGENTS.md
