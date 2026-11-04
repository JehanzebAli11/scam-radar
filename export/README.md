# Exporting to `.litertlm` for the Android app

The app runs the fine-tuned model on-device with
[LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM). Conversion is done
with `litert-torch` (its Hugging Face export is built in). These steps are run
by hand after a training run; nothing in CI depends on them.

## 1. Inputs

- `runs/v1/final/` from `scamradar train` (safetensors + tokenizer + chat
  template). This is a local directory, so no Hugging Face download is needed.
- Before exporting, run the full eval (`scamradar eval --strict ...`) on the
  served checkpoint. Only export a checkpoint that passes.

## 2. Convert

```bash
pip install litert-torch            # check the repo for the current package name/version

litert-torch export_hf \
  --model=runs/v1/final \
  --output_dir=runs/v1/litertlm
```

Notes:

- The default `--quantization_recipe` is `dynamic_wi8_afp32` (INT8 weights,
  FP32 activations); `dynamic_wi8_emb4_afp32` and `dynamic_wi4_afp32` are
  smaller. The default `--cache_length` is 4096 tokens, far more than a scam
  check needs; see section 4 before choosing. Re-run the eval on the exported
  model before switching recipes: quantisation can hurt scam recall on rare
  scam types.
- Some docs write the subcommand as `export-hf`; use whichever your installed
  version accepts (`litert-torch --help`).
- If the chat template bundled with the checkpoint does not work in LiteRT-LM,
  pass `--jinja_chat_template_override=<file>`. The prompt must stay exactly
  `Classify this message:\n<text>` as a single user turn (see
  `scamradar/prompting.py`).
- NPU builds need extra `--aot_backend` / `--aot_soc_model` flags and the
  vendor SDK. Start with CPU/GPU.

## 3. Check with the LiteRT-LM CLI

```bash
uv tool install litert-lm
litert-lm run --help                 # confirm the flags for running a local .litertlm
```

Smoke test a few fictional messages from `data/seeds/seeds.example.jsonl` and
check the output is a valid compact line:

```bash
scamradar validate --line "$(litert-lm run <local .litertlm args> \
  --prompt=$'Classify this message:\nGhalti se code aap ke number par chala gaya, wapis bhej dein')"
```

Then measure latency on a real Android device (the number that matters for the
app), not on the Spark. To load the file in the app, push it as
`scamradar.litertlm` to the app's storage; see
[`app/README.md`](../app/README.md#4-push-the-model).

## 4. Experiment: how small can it get?

Two things decide how much memory the model takes on a phone: how the
weights are stored (int8 vs int4) and how big the KV cache (the model's
working memory) is. This experiment exports several variants and measures each
for accuracy **and** cost, so the lightest one that still passes the eval can
ship.

What is known before running it:

- `litert-torch` 0.10.0 defaults: `--quantization_recipe=dynamic_wi8_afp32`,
  `--cache_length=4096`, `--prefill_lengths=[128]`. The cache length is baked
  into the `.litertlm` file as a fixed size.
- The LiteRT-LM engine asks for 4096 tokens unless told otherwise. When the
  file's fixed cache is smaller, it logs "larger than what the static model
  supports ... Using model limit". So shrinking the cache has to happen **at
  export** (`--cache_length`); the app's own cap
  (`LiteRtClassifier.MAX_NUM_TOKENS`, 512) is a safety net on top.
- A scam check needs well under 512 tokens: the message (training max length
  256) plus a compact line of under 48 tokens.
- Estimated KV cache for Gemma 3 270M (18 layers, 1 KV head, head dim 256,
  fp32): ~36 KB per token, so ~144 MB at 4096 tokens and ~18 MB at 512. Gemma 3's
  sliding-window layers may keep part of that small already, so the real
  saving is somewhere between ~20 MB and ~125 MB. That is what the experiment
  measures.
- About 170M of the 270M parameters are the embedding table (262,144 tokens ×
  640), which is why `dynamic_wi8_emb4_afp32` (int8 layers, int4 embeddings) is
  worth trying as a middle option.

### Variants

| Name | `--quantization_recipe` | `--cache_length` |
|---|---|---|
| `int8-cache4096` | `dynamic_wi8_afp32` (default) | 4096 (default) |
| `int8-cache512` | `dynamic_wi8_afp32` | 512 |
| `emb4-cache512` | `dynamic_wi8_emb4_afp32` | 512 |
| `int4-cache512` | `dynamic_wi4_afp32` | 512 |

### Run it

```bash
pip install -e ".[litertlm]"   # LiteRT-LM Python API (Linux x86_64/aarch64, macOS arm64, Windows)
```

```bash
while read -r name recipe cache; do
  mkdir -p runs/exp/$name
  litert-torch export_hf --model=runs/v1/final --output_dir=runs/exp/$name \
    --quantization_recipe=$recipe --cache_length=$cache
  model=$(ls runs/exp/$name/*.litertlm | head -1)
  scamradar predict-litertlm eval/locked/test.jsonl --model "$model" \
    -o runs/exp/$name/preds.jsonl --stats runs/exp/$name/stats.json
  scamradar eval --predictions runs/exp/$name/preds.jsonl --baseline runs/tfidf/preds.jsonl \
    --json runs/exp/$name/eval.json --markdown runs/exp/$name/EVAL.md
done <<'EOF'
int8-cache4096 dynamic_wi8_afp32 4096
int8-cache512 dynamic_wi8_afp32 512
emb4-cache512 dynamic_wi8_emb4_afp32 512
int4-cache512 dynamic_wi4_afp32 512
EOF
```

```bash
scamradar compare-exports runs/exp/* --markdown runs/exp/RESULTS.md
```

`predict-litertlm` runs each file the way the app does (exact training prompt,
greedy, fresh conversation per message, `--max-num-tokens 512` by default; pass
`--max-num-tokens 0` to see the engine default). Each command is a fresh
process, so `stats.json`'s `model_rss_mb` is that export's own memory on the
computer. Predictions go through the eval harness, the only reader of
`eval/locked/`. Check the exact flag spelling with
`litert-torch export_hf --help` if your version differs.

### On the phone

Desktop RAM is a guide; the phone is what counts. For each variant you want to
ship:

1. Push it as `scamradar.litertlm` (see `app/README.md`), open the app, tap
   *Look for the model again* in Settings, then check one message.
2. Right away run `adb shell dumpsys meminfo io.github.jehanzebali11.scamradar`
   and note TOTAL PSS. Wait 5 minutes (the app unloads the model when idle) and
   run it again.
3. Write both into `runs/exp/<name>/device.json`, e.g.
   `{"pss_loaded_mb": 412, "pss_idle_mb": 96, "first_check_s": 1.4}`, and
   re-run `compare-exports`.

To test the app's own cap separately, build once with the default engine
cache and once with the cap:

```bash
cd app && ./gradlew :android:assembleDebug -PscamradarMaxTokens=0
```

```bash
cd app && ./gradlew :android:assembleDebug
```

Ship the smallest variant whose eval still passes `--strict`. Quantisation
tends to hurt rare scam types first, so read the per-type numbers in
`EVAL.md`, not just the headline.

## ARM64 fallback (DGX Spark)

The Spark is ARM64 (aarch64). If `litert-torch` or one of its dependencies has
no aarch64 Linux wheel, or the export fails there:

1. Upload `runs/v1/final/` (the fine-tuned safetensors, not any data) to a
   Google Colab runtime (x86_64).
2. Run the same `pip install` and `litert-torch export_hf` commands there.
3. Download the `.litertlm` file and verify it with the LiteRT-LM CLI as above.

The LiteRT-LM CLI itself has Linux arm64 builds, so the step 3 check can run on
the Spark.

## Licence reminder

The exported file is a Gemma model derivative. Shipping it in the app means
shipping it under the [Gemma Terms of Use](https://ai.google.dev/gemma/terms)
and the Gemma Prohibited Use Policy, with the terms notice included.
