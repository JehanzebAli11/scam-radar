# eval/locked/ — the locked test set

This directory holds the **real, human-labelled test set** (e.g. `test.jsonl`).
Its contents are git-ignored and never committed; only this README is tracked.

Rules:

- Created exactly once by `scamradar split` (exclusive-create; it refuses to
  overwrite an existing file). Nothing in this repo modifies it afterwards.
- Only the evaluation harness (`scamradar eval`) reads it. Training, the data
  factory, dedupe, datasheets and baseline training refuse paths under here.
- Never use it for prompt engineering, few-shot examples, threshold tuning or
  error analysis that feeds back into training. Use the train pool for that.
- Messages must be anonymised (`scamradar anonymise`) and come from people who
  consented to their messages being used.

Expected record format (JSONL, one per line):

```json
{"id": "t-0001", "text": "...", "label": "scam|code_theft|asks_for_code,urgency", "tags": ["otp"]}
```

`tags` is optional; the harness reports the false-alarm rate separately for
records tagged `otp`.
