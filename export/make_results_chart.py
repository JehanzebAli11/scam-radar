"""LinkedIn-ready chart of the export experiment (export/README.md section 4).

Reads the same experiment directories as ``scamradar compare-exports`` and draws
one 1080x1350 (4:5) image: memory per export as an emphasis bar chart (the
export you ship in blue, the rest in grey, values labelled), plus a small table
of file size and accuracy underneath (a different scale, so not on the same
axis). Optionally writes a short MP4 where the bars grow in.

    python export/make_results_chart.py runs/exp/* --ship int4-cache512 \\
        --device "Pixel 6a" --png runs/exp/results.png --mp4 runs/exp/results.mp4

Memory is the phone's TOTAL PSS with the model loaded when every export has a
device.json, otherwise the computer-side model RAM from stats.json (labelled
as such). ``--sample`` draws made-up numbers, stamped "SAMPLE DATA", only to
preview the layout. Needs Pillow; the MP4 also needs ffmpeg.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from scamradar.export_compare import load_row

W, H = 1080, 1350
SURFACE = (252, 252, 251)  # #fcfcfb
INK = (11, 11, 11)  # #0b0b0b
INK_2 = (82, 81, 78)  # #52514e
INK_3 = (140, 139, 134)
GRID = (229, 228, 224)
ACCENT = (42, 120, 214)  # #2a78d6, validated against SURFACE
MUTED_BAR = (195, 194, 183)  # #c3c2b7 de-emphasis grey
PASS = (0, 131, 0)
FAIL = (196, 40, 40)

FONT_CANDIDATES = {
    False: [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/Library/Fonts/Arial.ttf",
        "C:/Windows/Fonts/arial.ttf",
    ],
    True: [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
        "/Library/Fonts/Arial Bold.ttf",
        "C:/Windows/Fonts/arialbd.ttf",
    ],
}


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    for path in FONT_CANDIDATES[bold]:
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default(size)


@dataclass
class Row:
    name: str
    memory_mb: float | None
    file_mb: float | None
    recall: float | None
    false_alarms: float | None
    passed: bool | None


SHORT = {"int8": "int8", "emb4": "int8 + int4 table", "int4": "int4"}


def short_name(name: str) -> str:
    """'emb4-cache512' -> 'int8 + int4 table · 512'."""
    m = re.search(r"cache(\d+)", name)
    base = SHORT.get(name.split("-")[0], name)
    return f"{base} · {int(m.group(1)):,}" if m else base


def describe(name: str) -> tuple[str, str]:
    """'int4-cache512' -> ('int4 weights', '512-token memory')."""
    weights = {
        "int8": "int8 weights",
        "emb4": "int8 + int4 word table",
        "int4": "int4 weights",
    }.get(name.split("-")[0], name)
    m = re.search(r"cache(\d+)", name)
    cache = f"{int(m.group(1)):,}-token memory" if m else ""
    return weights, cache


def load(dirs: list[str]) -> tuple[list[Row], str]:
    raw = [load_row(d) for d in dirs if Path(d).is_dir()]
    on_phone = bool(raw) and all(r["device"].get("pss_loaded_mb") is not None for r in raw)
    rows = [
        Row(
            name=r["name"],
            memory_mb=r["device"].get("pss_loaded_mb")
            if on_phone
            else r["stats"].get("model_rss_mb"),
            file_mb=r["stats"].get("model_file_mb"),
            recall=r["metrics"].get("scam_recall"),
            false_alarms=r["metrics"].get("false_alarm_rate"),
            passed=r["all_passed"],
        )
        for r in raw
    ]
    unit = (
        "app memory while checking (MB, phone)"
        if on_phone
        else "model memory (MB, measured on a computer)"
    )
    return rows, unit


def sample() -> tuple[list[Row], str]:
    """Made-up numbers for previewing the layout only."""
    return (
        [
            Row("int8-cache4096", 470, 300, 0.93, 0.04, True),
            Row("int8-cache512", 360, 300, 0.93, 0.04, True),
            Row("emb4-cache512", 300, 210, 0.92, 0.04, True),
            Row("int4-cache512", 250, 160, 0.90, 0.05, True),
        ],
        "app memory while checking (MB, phone)",
    )


def _pct(v: float | None) -> str:
    return "–" if v is None else f"{v:.0%}"


def _mb(v: float | None) -> str:
    return "–" if v is None else f"{v:,.0f} MB"


def draw(
    rows: list[Row],
    unit: str,
    ship: str | None,
    device: str | None,
    progress: float = 1.0,
    watermark: bool = False,
) -> Image.Image:
    img = Image.new("RGB", (W, H), SURFACE)
    d = ImageDraw.Draw(img)
    x0, x1 = 72, W - 72

    count = {1: "One", 2: "Two", 3: "Three", 4: "Four", 5: "Five", 6: "Six"}.get(
        len(rows), str(len(rows))
    )
    noun = "export" if len(rows) == 1 else "exports"
    d.text((x0, 70), f"Same model. {count} {noun}.", font=font(54, True), fill=INK)
    d.text(
        (x0, 140), "How much memory does an on-phone scam checker need?", font=font(30), fill=INK_2
    )

    # Bars: emphasis form, one value per export, labelled, shared baseline.
    d.text((x0, 222), unit[0].upper() + unit[1:] + " · lower is better", font=font(24), fill=INK_2)
    top, bar_h, gap = 284, 64, 72
    label_w = 330
    bx0, bx1 = x0 + label_w, x1 - 120
    vmax = max((r.memory_mb or 0) for r in rows) or 1.0
    for i, r in enumerate(rows):
        y = top + i * (bar_h + gap)
        weights, cache = describe(r.name)
        is_ship = r.name == ship
        d.text((x0, y + 2), weights, font=font(26, is_ship), fill=INK)
        d.text((x0, y + 34), cache, font=font(21), fill=INK_2)
        d.line([(bx0, y - 8), (bx0, y + bar_h + 8)], fill=GRID, width=2)
        if r.memory_mb is None:
            d.text((bx0 + 12, y + 14), "not measured", font=font(22), fill=INK_3)
            continue
        length = (bx1 - bx0) * (r.memory_mb / vmax) * progress
        color = ACCENT if is_ship or ship is None else MUTED_BAR
        if length > 8:
            # Square at the baseline, 4px-rounded data end.
            d.rounded_rectangle([bx0, y + 6, bx0 + length, y + bar_h - 6], radius=4, fill=color)
            d.rectangle([bx0, y + 6, bx0 + 4, y + bar_h - 6], fill=color)
        if progress >= 1.0:
            d.text((bx0 + length + 14, y + 12), _mb(r.memory_mb), font=font(26, is_ship), fill=INK)
            if is_ship:
                d.text((bx0 + length + 14, y + 42), "shipped", font=font(18, True), fill=ACCENT)

    # Saving headline, computed from the data.
    first, chosen = rows[0], next((r for r in rows if r.name == ship), rows[-1])
    ty = top + len(rows) * (bar_h + gap) + 6
    if first.memory_mb and chosen.memory_mb and chosen is not first:
        saved = 1 - chosen.memory_mb / first.memory_mb
        if saved >= 0.01:
            headline = f"{saved:.0%} less memory than the default export"
        elif saved <= -0.01:
            headline = f"{-saved:.0%} more memory than the default export"
        else:
            headline = "No memory difference from the default export"
        d.text((x0, ty), headline, font=font(34, True), fill=INK)
        ty += 56

    # Accuracy table: a different measure, so its own block, not a second axis.
    ty += 18
    d.line([(x0, ty), (x1, ty)], fill=GRID, width=2)
    cols = [x0, x0 + 305, x0 + 455, x0 + 650]
    heads = ["Export", "File size", "Scams caught", "False alarms"]
    for c, h in zip(cols, heads, strict=True):
        d.text((c, ty + 18), h, font=font(22, True), fill=INK_2)
    d.text((x1 - 92, ty + 18), "Targets", font=font(22, True), fill=INK_2)
    for i, r in enumerate(rows):
        y = ty + 66 + i * 52
        name = short_name(r.name)
        d.text((cols[0], y), name, font=font(23, r.name == ship), fill=INK)
        d.text((cols[1], y), _mb(r.file_mb), font=font(23), fill=INK)
        d.text((cols[2], y), _pct(r.recall), font=font(23), fill=INK)
        d.text((cols[3], y), _pct(r.false_alarms), font=font(23), fill=INK)
        if r.passed is not None:
            mark, color = ("pass", PASS) if r.passed else ("fail", FAIL)
            d.text((x1 - 70, y), mark, font=font(21, True), fill=color)

    scored = any(r.recall is not None for r in rows)
    foot = "Gemma 3 270M fine-tuned for Scam Radar" + (
        " · scored on a held-out, human-labelled test set"
        if scored
        else " · accuracy not scored yet"
    )
    d.text((x0, H - 96), foot, font=font(20), fill=INK_3)
    if device:
        d.text((x0, H - 66), f"Phone: {device}", font=font(20), fill=INK_3)

    if watermark:
        # Draw on a larger canvas, rotate, then crop, so the text is never clipped.
        big = Image.new("RGBA", (W * 2, H * 2), (0, 0, 0, 0))
        md = ImageDraw.Draw(big)
        text = "SAMPLE DATA - NOT MEASURED"
        f = font(56, True)
        tw = md.textlength(text, font=f)
        md.text((W - tw / 2, H - 30), text, font=f, fill=(196, 40, 40, 120))
        big = big.rotate(28, center=(W, H))
        mark_img = big.crop((W // 2, H // 2, W // 2 + W, H // 2 + H))
        img = Image.alpha_composite(img.convert("RGBA"), mark_img).convert("RGB")
    return img


def write_mp4(path: Path, frame_fn: object, seconds_hold: float = 5.0) -> None:
    fps, grow = 30, 36
    with tempfile.TemporaryDirectory() as tmp:
        t = Path(tmp)
        n = 0
        for i in range(grow + 1):
            p = 1 - (1 - i / grow) ** 3  # ease-out
            frame_fn(p).save(t / f"{n:04d}.png")  # type: ignore[operator]
            n += 1
        final = frame_fn(1.0)  # type: ignore[operator]
        for _ in range(int(fps * seconds_hold)):
            final.save(t / f"{n:04d}.png")
            n += 1
        cmd = ["ffmpeg", "-y", "-loglevel", "error", "-framerate", str(fps)]
        cmd += ["-i", str(t / "%04d.png"), "-c:v", "libx264", "-pix_fmt", "yuv420p"]
        cmd += ["-crf", "20", "-movflags", "+faststart", str(path)]
        subprocess.run(cmd, check=True)


def main() -> None:
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    ap.add_argument("dirs", nargs="*", help="experiment dirs (as for compare-exports)")
    ap.add_argument("--ship", help="the export you chose; drawn in blue, the rest grey")
    ap.add_argument("--device", help='phone model for the footnote, e.g. "Pixel 6a"')
    ap.add_argument("--png", default="results.png")
    ap.add_argument("--mp4", help="also write an animated MP4 here")
    ap.add_argument(
        "--sample", action="store_true", help="made-up numbers, watermarked, for a layout preview"
    )
    args = ap.parse_args()

    if args.sample:
        rows, unit = sample()
        ship = args.ship or "int4-cache512"
    else:
        if not args.dirs:
            ap.error("pass experiment dirs, or --sample for a preview")
        rows, unit = load(args.dirs)
        ship = args.ship

    def frame(p: float) -> Image.Image:
        return draw(rows, unit, ship, args.device, progress=p, watermark=args.sample)

    Path(args.png).parent.mkdir(parents=True, exist_ok=True)
    frame(1.0).save(args.png)
    print(f"wrote {args.png}")
    if args.mp4:
        write_mp4(Path(args.mp4), frame)
        print(f"wrote {args.mp4}")


if __name__ == "__main__":
    main()
