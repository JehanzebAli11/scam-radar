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
  FP32 activations). Re-run the eval on the exported model before switching
  recipes: quantisation can hurt scam recall on rare scam types.
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
