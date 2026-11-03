"""Scam Radar command-line interface: data factory, baselines, training and evaluation."""

from __future__ import annotations

import argparse
import json
import logging
import sys
from collections.abc import Callable, Sequence
from pathlib import Path
from typing import TYPE_CHECKING

from scamradar import __version__
from scamradar.io import read_records, write_json, write_jsonl, write_text
from scamradar.schema import validate_line

if TYPE_CHECKING:
    from scamradar.factory.teacher import ChatClient
    from scamradar.predictor import Predictor

Handler = Callable[[argparse.Namespace], int]


def _out(msg: str) -> None:
    print(msg, file=sys.stdout)


def _err(msg: str) -> None:
    print(msg, file=sys.stderr)


# --- validate ---------------------------------------------------------------


def cmd_validate(args: argparse.Namespace) -> int:
    if args.line is not None:
        errors = validate_line(args.line)
        _out("OK" if not errors else "INVALID: " + "; ".join(errors))
        return 0 if not errors else 1
    records = read_records(args.input)
    bad = 0
    for rec in records:
        if "label" not in rec:
            bad += 1
            _err(f"{rec['id']}: missing label")
            continue
        errors = validate_line(str(rec["label"]))
        if errors:
            bad += 1
            _err(f"{rec['id']}: {'; '.join(errors)}")
    _out(f"{len(records) - bad}/{len(records)} valid")
    return 0 if bad == 0 else 1


# --- anonymise --------------------------------------------------------------


def cmd_anonymise(args: argparse.Namespace) -> int:
    from scamradar.anonymise import Anonymiser, load_names

    names = load_names(args.names) if args.names else ()
    anon = Anonymiser(names=names, numbered=args.numbered)
    records = read_records(args.input)
    out = [{**r, "text": anon(r["text"])} for r in records]
    write_jsonl(args.output, out)
    _out(f"anonymised {len(out)} records -> {args.output}")
    return 0


# --- split ------------------------------------------------------------------


def cmd_split(args: argparse.Namespace) -> int:
    from scamradar.split import split_files

    res = split_files(
        args.input,
        args.train_out,
        args.locked_out,
        test_fraction=args.test_fraction,
        seed=args.seed,
    )
    _out(f"train pool: {len(res.train)} -> {args.train_out}")
    _out(f"locked test: {len(res.test)} -> {args.locked_out}")
    return 0


# --- factory ----------------------------------------------------------------


def _teacher(args: argparse.Namespace) -> ChatClient:
    from scamradar.factory.teacher import OpenAICompatibleClient

    return OpenAICompatibleClient.from_env(base_url=args.base_url, model=args.model)


def cmd_generate(args: argparse.Namespace) -> int:
    from scamradar.factory.generate import GenerateConfig, generate_from_seeds

    seeds = read_records(args.seeds)
    if args.categories:
        wanted = set(args.categories.split(","))
        seeds = [s for s in seeds if s.get("category") in wanted]
    cfg = GenerateConfig(n_per_seed=args.n, temperature=args.temperature)
    n = write_jsonl(args.output, generate_from_seeds(seeds, _teacher(args), cfg))
    _out(f"generated {n} records from {len(seeds)} seeds -> {args.output}")
    return 0


def cmd_label(args: argparse.Namespace) -> int:
    from scamradar.factory.label import LabelConfig, label_records

    records = read_records(args.input)
    rejects: list[dict[str, object]] = []
    cfg = LabelConfig(max_retries=args.retries)
    n = write_jsonl(args.output, label_records(records, _teacher(args), cfg, rejects=rejects))
    if args.rejects:
        write_jsonl(args.rejects, rejects)
    _out(f"labelled {n}/{len(records)} records -> {args.output} ({len(rejects)} rejected)")
    return 0


def cmd_dedupe(args: argparse.Namespace) -> int:
    from scamradar.factory.dedupe import DedupeConfig, dedupe

    records: list[dict[str, object]] = []
    for path in args.inputs:
        records.extend(read_records(path))
    res = dedupe(records, DedupeConfig(threshold=args.threshold, ngram=args.ngram))
    write_jsonl(args.output, res.kept)
    if args.dupes:
        write_jsonl(args.dupes, res.exact_dupes + res.near_dupes)
    _out(
        f"kept {len(res.kept)}/{len(records)} (exact dupes {len(res.exact_dupes)}, "
        f"near dupes {len(res.near_dupes)}) -> {args.output}"
    )
    return 0


def cmd_datasheet(args: argparse.Namespace) -> int:
    from scamradar.factory.datasheet import build_datasheet, datasheet_markdown

    records = read_records(args.input)
    sheet = build_datasheet(records, name=args.name or Path(args.input).stem)
    if args.output:
        write_json(args.output, sheet)
    if args.markdown:
        write_text(args.markdown, datasheet_markdown(sheet))
    if not args.output and not args.markdown:
        _out(json.dumps(sheet, ensure_ascii=False, indent=2))
    return 0


# --- baselines / predict ----------------------------------------------------


def cmd_baseline_tfidf(args: argparse.Namespace) -> int:
    from scamradar.baselines.tfidf import TfidfBaseline

    if args.train:
        model = TfidfBaseline().fit(read_records(args.train))
        model.save(args.model)
        _out(f"trained TF-IDF baseline -> {args.model}")
    if args.predict:
        _write_predictions(TfidfBaseline.load(args.model), args.predict, args.output)
    if not args.train and not args.predict:
        _err("nothing to do: pass --train and/or --predict")
        return 2
    return 0


def cmd_predict(args: argparse.Namespace) -> int:
    from scamradar.baselines.prompt_baseline import PromptBaseline
    from scamradar.factory.teacher import OpenAICompatibleClient

    client = OpenAICompatibleClient(base_url=args.base_url, model=args.model)
    _write_predictions(PromptBaseline(client, style=args.style), args.input, args.output)
    return 0


def _write_predictions(predictor: Predictor, input_path: str, output: str | None) -> None:
    # Predicting on the locked set is part of evaluation, so inputs are loaded
    # through the harness (the only reader allowed in eval/locked/).
    from scamradar.eval.harness import load_inputs, predict_records

    if not output:
        raise SystemExit("--output is required for predictions")
    preds = predict_records(predictor, load_inputs(input_path))
    write_jsonl(output, preds.values())
    _out(f"wrote {len(preds)} predictions -> {output}")


# --- eval -------------------------------------------------------------------


def cmd_eval(args: argparse.Namespace) -> int:
    from scamradar.eval.harness import evaluate, join, load_gold, load_predictions, write_report

    gold = load_gold(args.gold)
    examples = join(gold, load_predictions(args.predictions))
    base = join(gold, load_predictions(args.baseline)) if args.baseline else None
    report = evaluate(examples, model_name=args.name, baseline_examples=base)
    write_report(report, args.json, args.markdown)
    _out(report.to_markdown())
    return 0 if report.all_passed or not args.strict else 1


# --- train ------------------------------------------------------------------


def cmd_train(args: argparse.Namespace) -> int:
    from scamradar.train.config import ConfigError, load_config
    from scamradar.train.sft import dry_run, train

    try:
        cfg = load_config(
            args.config,
            train_file=args.train_file,
            eval_file=args.eval_file,
            output_dir=args.output_dir,
            resume_from_checkpoint=args.resume,
        )
    except ConfigError as exc:
        _err("config invalid:\n" + "\n".join(f"  - {e}" for e in exc.errors))
        return 1
    if args.dry_run:
        report = dry_run(cfg)
        _out(report.summary())
        return 0 if report.ok else 1
    _out(f"training finished -> {train(cfg)}")
    return 0


# --- parser -----------------------------------------------------------------


def _add_teacher_args(p: argparse.ArgumentParser) -> None:
    p.add_argument("--base-url", default=None, help="teacher endpoint (default: localhost:8000/v1)")
    p.add_argument(
        "--model", default=None, help="teacher model id (default: google/gemma-4-31b-it)"
    )


def build_parser() -> argparse.ArgumentParser:
    from scamradar.baselines.prompt_baseline import DEFAULT_BASE_MODEL, PROMPT_STYLES
    from scamradar.factory.teacher import DEFAULT_BASE_URL
    from scamradar.train.config import DEFAULT_CONFIG

    parser = argparse.ArgumentParser(prog="scamradar", description=__doc__)
    parser.add_argument("--version", action="version", version=f"scamradar {__version__}")
    parser.add_argument("-v", "--verbose", action="store_true")
    sub = parser.add_subparsers(dest="command", required=True)

    def add(name: str, handler: Handler, help_: str) -> argparse.ArgumentParser:
        p = sub.add_parser(name, help=help_, description=help_)
        p.set_defaults(handler=handler)
        return p

    p = add("validate", cmd_validate, "validate a compact line or a labelled dataset")
    g = p.add_mutually_exclusive_group(required=True)
    g.add_argument("--line")
    g.add_argument("--input")

    p = add("anonymise", cmd_anonymise, "replace phones, CNICs, IBANs, emails, URL paths, names")
    p.add_argument("input")
    p.add_argument("-o", "--output", required=True)
    p.add_argument("--names", help="file with one name per line")
    p.add_argument("--numbered", action="store_true", help="<PHONE_1>, <PHONE_2>, ...")

    p = add("split", cmd_split, "stratified split of real messages into train pool + locked test")
    p.add_argument("input")
    p.add_argument("--train-out", required=True)
    p.add_argument("--locked-out", default="eval/locked/test.jsonl")
    p.add_argument("--test-fraction", type=float, default=0.5)
    p.add_argument("--seed", type=int, default=13)

    p = add("generate", cmd_generate, "generate variants from seeds with the local teacher")
    p.add_argument("seeds")
    p.add_argument("-o", "--output", required=True)
    p.add_argument("-n", type=int, default=5, help="variants per seed")
    p.add_argument("--temperature", type=float, default=0.9)
    p.add_argument("--categories", help="comma-separated subset of seed categories")
    _add_teacher_args(p)

    p = add("label", cmd_label, "label texts into the schema with the local teacher")
    p.add_argument("input")
    p.add_argument("-o", "--output", required=True)
    p.add_argument("--rejects", help="where to write records that failed to label")
    p.add_argument("--retries", type=int, default=3)
    _add_teacher_args(p)

    p = add("dedupe", cmd_dedupe, "remove exact and near-duplicate texts")
    p.add_argument("inputs", nargs="+", help="earlier files win ties (put real data first)")
    p.add_argument("-o", "--output", required=True)
    p.add_argument("--dupes", help="where to write removed duplicates")
    p.add_argument("--threshold", type=float, default=0.8)
    p.add_argument("--ngram", type=int, default=4)

    p = add("datasheet", cmd_datasheet, "counts per label/type/language + provenance")
    p.add_argument("input")
    p.add_argument("-o", "--output", help="JSON output")
    p.add_argument("--markdown", help="Markdown output")
    p.add_argument("--name")

    p = add("baseline-tfidf", cmd_baseline_tfidf, "train and/or run the TF-IDF baseline")
    p.add_argument("--train", help="labelled training JSONL/CSV")
    p.add_argument("--model", default="runs/tfidf/model.joblib")
    p.add_argument("--predict", help="records to predict on")
    p.add_argument("-o", "--output", help="predictions JSONL")

    p = add("predict", cmd_predict, "predict via an OpenAI-compatible endpoint (base or tuned)")
    p.add_argument("input")
    p.add_argument("-o", "--output", required=True)
    p.add_argument("--base-url", default=DEFAULT_BASE_URL)
    p.add_argument("--model", default=DEFAULT_BASE_MODEL)
    p.add_argument("--style", choices=PROMPT_STYLES, default="few_shot")

    p = add("eval", cmd_eval, "score predictions against gold; report targets")
    p.add_argument("--gold", default="eval/locked/test.jsonl")
    p.add_argument("--predictions", required=True)
    p.add_argument("--baseline", help="TF-IDF baseline predictions for comparison")
    p.add_argument("--name", default="gemma-3-270m-scamradar")
    p.add_argument("--json", help="write metrics JSON here")
    p.add_argument("--markdown", help="write markdown report here")
    p.add_argument("--strict", action="store_true", help="exit 1 if any target fails")

    p = add("train", cmd_train, "fine-tune Gemma 3 270M (use --dry-run without a GPU)")
    p.add_argument("--config", default=str(DEFAULT_CONFIG))
    p.add_argument("--train-file")
    p.add_argument("--eval-file")
    p.add_argument("--output-dir")
    p.add_argument("--resume", help="checkpoint path or 'latest'")
    p.add_argument("--dry-run", action="store_true")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO if args.verbose else logging.WARNING)
    try:
        return int(args.handler(args))
    except (FileExistsError, PermissionError, ValueError) as exc:
        _err(f"error: {exc}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
