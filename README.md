# Scam Radar

An offline Android scam detector for Pakistani phone messages. A 270M-parameter
Gemma model, small enough to run on the phone, labels SMS and WhatsApp texts in
Roman Urdu, Urdu script, English and code-mixed. This repo holds the data,
training and evaluation pipeline.

## Results

Results land after the first Spark run. The locked test set is real,
human-labelled and never used for training.

| Metric (locked test set) | Target | Gemma 3 270M (fine-tuned) | TF-IDF baseline | Base model, prompted |
|---|---|---|---|---|
| Scam recall | >= 90% | – | – | – |
| False-alarm rate, genuine (safe + promo) | <= 5% | – | – | – |
| False-alarm rate, genuine OTP messages | <= 5% | – | – | – |
| Scam-type accuracy (true scams) | >= 80% | – | – | – |
| Beats TF-IDF on all three | yes | – | n/a | – |
| Mean latency on device | report | – | – | – |

## How it works

The model reads one message and emits one compact line:

```
label|scam_type|flags
```

| Field | Values |
|---|---|
| `label` | `safe`, `promo`, `scam` |
| `scam_type` | when `label=scam`: `fake_govt_aid`, `fake_prize`, `code_theft`, `bank_or_wallet_impersonation`, `family_emergency`, `fake_job_or_investment`, `phishing_link`, `other`. Otherwise `none` |
| `flags` | comma list in this order: `asks_for_code`, `asks_for_money`, `has_link`, `urgency`, `claims_official`. Or `none` |

So `scam|code_theft|asks_for_code,urgency` or `safe|none|none`.

The pipeline:

```
real consented messages ──anonymise──▶ split ──┬──▶ eval/locked/ (human labels, never trained on)
                                               └──▶ train pool
fictional seeds ──generate (teacher)──▶ variants ──┐
                                                   ├──▶ label (teacher) ──▶ dedupe ──▶ datasheet
train pool ────────────────────────────────────────┘                          │
                                                                              ▼
                       TF-IDF baseline ◀──────────── train set ──────────▶ SFT Gemma 3 270M
                              │                                               │
                              └────────────▶ eval harness ◀───────────────────┘
                                         (targets, pass/fail)                 │
                                                                              ▼
                                                                  .litertlm ──▶ Android app
```

1. **Teacher.** An open-weight model (default `google/gemma-4-31b-it`) runs on
   the DGX Spark behind an OpenAI-compatible endpoint (vLLM, SGLang, etc.). It
   writes message variants from fictional seeds and labels every training
   text. Each record keeps the teacher model id and a hash of the prompt.
2. **Hard negatives.** The generator writes genuine messages that look scammy:
   bank OTPs that say "do not share", BISP messages from the official 8171
   short code, courier delivery codes and telco promos. These keep false alarms
   down, which matters as much as catching scams.
3. **Dedupe.** Normalised exact matches plus character n-gram Jaccard
   (MinHash + LSH for candidates), so paraphrases don't inflate the set.
4. **Train.** Full fine-tune of `google/gemma-3-270m-it` with TRL's
   `SFTTrainer`. The prompt is a single user turn `Classify this message:\n<text>`
   and the loss is on the compact line only.
5. **Evaluate.** The harness scores against the locked set and compares with a
   char n-gram TF-IDF + logistic regression baseline. The fine-tuned model has
   to beat it to be worth shipping.
6. **Export.** Convert to `.litertlm` for LiteRT-LM on Android. See
   [`export/README.md`](export/README.md).

## Quickstart

Needs Python 3.11 or newer.

```bash
pip install -e ".[dev]"          # CPU only: schema, factory, baselines, eval, dry runs
pytest                           # no network, GPU or model weights needed

scamradar validate --line "scam|code_theft|asks_for_code,urgency"
scamradar datasheet data/seeds/seeds.example.jsonl
```

On the DGX Spark:

```bash
pip install -e ".[train]"

# 0. Serve the open-weight teacher locally (any OpenAI-compatible server works).
vllm serve google/gemma-4-31b-it --port 8000
export SCAMRADAR_TEACHER_BASE_URL=http://localhost:8000/v1   # the default
export SCAMRADAR_TEACHER_MODEL=google/gemma-4-31b-it         # the default

# 1. Real consented messages: anonymise, then split once into train pool + locked test.
scamradar anonymise data/real/raw.jsonl -o data/real/anon.jsonl --names data/real/names.txt
scamradar split data/real/anon.jsonl --train-out data/real/train_pool.jsonl \
  --locked-out eval/locked/test.jsonl            # refuses to overwrite an existing file

# 2. Synthetic data from fictional seeds (your own seeds go in data/seeds/, git-ignored).
scamradar generate data/seeds/seeds.example.jsonl -o data/gen/variants.jsonl -n 20
scamradar label data/gen/variants.jsonl -o data/gen/labelled.jsonl --rejects data/gen/rejects.jsonl
scamradar dedupe data/real/train_pool.jsonl data/gen/labelled.jsonl -o data/train/train.jsonl
scamradar datasheet data/train/train.jsonl -o data/train/datasheet.json --markdown data/train/DATASHEET.md

# 3. Baseline.
scamradar baseline-tfidf --train data/train/train.jsonl --model runs/tfidf/model.joblib \
  --predict eval/locked/test.jsonl -o runs/tfidf/preds.jsonl

# 4. Fine-tune (check config and data first; the dry run needs no torch).
scamradar train --config scamradar/train/configs/v1.yaml --dry-run
scamradar train --config scamradar/train/configs/v1.yaml
scamradar train --config scamradar/train/configs/v1.yaml --resume latest   # after an interruption

# 5. Serve the fine-tuned checkpoint and evaluate.
vllm serve runs/v1/final --served-model-name scamradar-v1 --port 8001
scamradar predict eval/locked/test.jsonl -o runs/v1/preds.jsonl \
  --base-url http://localhost:8001/v1 --model scamradar-v1 --style finetuned
scamradar eval --predictions runs/v1/preds.jsonl --baseline runs/tfidf/preds.jsonl \
  --json runs/v1/eval.json --markdown runs/v1/EVAL.md --strict
```

The prompted base-model baseline uses the same `predict` command with
`--model google/gemma-3-270m-it --style few_shot`. The eval harness takes
predictions from any source as JSONL `{"id", "pred", "latency_ms"?}`, so
on-device latency measured on a phone can be fed in the same way.

## Ground rules

These are enforced in code and tests, and repeated in [`AGENTS.md`](AGENTS.md)
and [`CLAUDE.md`](CLAUDE.md) for coding agents.

1. **Training data and labels come only from an open-weight teacher model
   served locally**, via an OpenAI-compatible endpoint (default
   `http://localhost:8000/v1`, model `google/gemma-4-31b-it`, both
   configurable), **or from real consented messages.** Nothing in this repo
   calls Claude/Anthropic, OpenAI or Gemini APIs. The teacher client refuses
   those hosts, and a test checks the package for their SDKs.
2. **`eval/locked/` holds the real human-labelled test set.** Code never writes
   to it; `split` creates it once and refuses to overwrite. Only the eval
   harness reads it, and every other command refuses paths inside it. It is
   git-ignored, apart from a [README](eval/locked/README.md).
3. **No real personal data in the repo.** Seeds and fixtures are fictional,
   with made-up brands (FictionBank, ZetaPay, NovaTel) and placeholder numbers
   (`0300-0000000`). Real messages are anonymised and stay in git-ignored
   `data/`.

## Repo layout

```
scamradar/
  schema.py            label|scam_type|flags: enums, parse/format, strict validator
  anonymise.py         PK phones, CNIC, IBAN, email, URL paths, names -> placeholders
  split.py             stratified train-pool / locked-test split
  paths.py, io.py      JSONL/CSV I/O with the eval/locked guard
  prompting.py         the single training/inference prompt
  factory/
    teacher.py         OpenAI-compatible HTTP client (injectable; refuses proprietary APIs)
    prompts.py         generation + labelling prompts, hard-negative categories
    generate.py        N variants per seed
    label.py           teacher labelling with parse/validate/retry + provenance
    dedupe.py          exact + MinHash/Jaccard near-duplicate removal
    datasheet.py       counts per label/type/language, provenance
  baselines/
    tfidf.py           char n-gram TF-IDF + logistic regression (joblib)
    prompt_baseline.py base or fine-tuned model via the endpoint
  eval/
    metrics.py         target metrics and pass/fail
    harness.py         gold + predictions -> markdown + JSON report
  train/
    configs/v1.yaml    3 epochs, lr 5e-5, batch 16, max length 256, bf16
    config.py, data.py config validation and SFT formatting (no torch)
    sft.py             TRL SFTTrainer, checkpoints, resume, --dry-run
  cli.py               `scamradar` entry point
data/seeds/seeds.example.jsonl   45 fictional seeds: 3 per scam type and safe/promo/hard-negative category
eval/locked/           locked test set (git-ignored)
export/README.md       .litertlm conversion
tests/                 pytest suite (fake teacher, tiny fixtures)
```

## Licence

Code: [MIT](LICENSE), Copyright 2026 Jehanzeb Ali.

Model weights: the fine-tuned model is derived from Google's Gemma 3, so any
released weights (including the `.litertlm` file shipped in the app) are
covered by the [Gemma Terms of Use](https://ai.google.dev/gemma/terms) and the
Gemma Prohibited Use Policy, not by the MIT licence. Because the student is
also trained on a Gemma-family teacher's outputs, it counts as a Gemma model
derivative under those terms either way. If you swap in a different teacher,
check its licence too before releasing weights or generated datasets.
