"""Turn DemoVideoTest frames into a captioned MP4 walkthrough.

Usage: python app/tools/make_demo_video.py <frames dir> <out.mp4>

Needs Pillow and ffmpeg. Frames come from the Robolectric test
``android/src/test/.../demo/DemoVideoTest.kt`` (see make_demo_video.sh).
"""

from __future__ import annotations

import subprocess
import sys
import tempfile
import textwrap
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

W, H = 1080, 1920
BG = (13, 27, 42)
FG = (240, 244, 248)
MUTED = (150, 170, 190)
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FADE_FRAMES = 8
FOOTER = "Prototype · real app UI rendered with Robolectric, not a phone recording · demo mode"


def _font(path: str, size: int) -> ImageFont.FreeTypeFont:
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        return ImageFont.load_default(size)


def _text_block(
    draw: ImageDraw.ImageDraw,
    text: str,
    y: int,
    size: int,
    width: int,
    fill: tuple[int, int, int],
    bold: bool = False,
) -> int:
    font = _font(FONT_BOLD if bold else FONT, size)
    for line in textwrap.wrap(text, width=width):
        w = draw.textlength(line, font=font)
        draw.text(((W - w) / 2, y), line, font=font, fill=fill)
        y += int(size * 1.35)
    return y


def card(title: str, lines: list[str]) -> Image.Image:
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    y = _text_block(d, title, 640, 72, 22, FG, bold=True) + 60
    for line in lines:
        y = _text_block(d, line, y, 38, 44, MUTED) + 24
    return img


def framed(screen: Path, caption: str, tap: tuple[int, int] | None = None) -> Image.Image:
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    _text_block(d, caption, 60, 40, 44, FG)
    shot = Image.open(screen).convert("RGB")
    max_w, max_h = 760, 1500
    scale = min(max_w / shot.width, max_h / shot.height)
    shot = shot.resize((int(shot.width * scale), int(shot.height * scale)), Image.LANCZOS)
    x, y = (W - shot.width) // 2, 300
    border = 14
    d.rounded_rectangle(
        (x - border, y - border, x + shot.width + border, y + shot.height + border),
        48,
        fill=(0, 0, 0),
    )
    mask = Image.new("L", shot.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, *shot.size), 36, fill=255)
    img.paste(shot, (x, y), mask)
    if tap is not None:
        _touch(img, x + tap[0] * scale, y + tap[1] * scale)
    _text_block(d, FOOTER, H - 90, 24, 80, MUTED)
    return img


def _touch(img: Image.Image, cx: float, cy: float) -> None:
    """A translucent finger-press marker."""
    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    o = ImageDraw.Draw(overlay)
    for r, a in ((62, 50), (44, 90)):
        o.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(255, 255, 255, a))
    o.ellipse((cx - 44, cy - 44, cx + 44, cy + 44), outline=(255, 255, 255, 230), width=5)
    img.paste(Image.alpha_composite(img.convert("RGBA"), overlay).convert("RGB"))


def _same_scene(a: str, b: str) -> bool:
    """Typing frames of one message cut straight to each other; everything else crossfades."""
    return a.split("_")[0] == b.split("_")[0] and "_type" in a and "_type" in b


def main(frames_dir: Path, out: Path) -> None:
    rows = [
        line.split("\t")
        for line in (frames_dir / "captions.tsv").read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    rows.sort(
        key=lambda r: r[0]
    )  # names are ordered; the share preview is rendered last but belongs after b01
    with tempfile.TemporaryDirectory() as tmp:
        t = Path(tmp)
        seq: list[tuple[Path, float]] = []

        def add(img: Image.Image, seconds: float) -> None:
            p = t / f"{len(seq):04d}.png"
            img.save(p)
            seq.append((p, seconds))

        add(
            card(
                "Scam Radar",
                [
                    "Automatic scam warnings for SMS and WhatsApp, in Urdu and English. "
                    "Checked on the phone, offline.",
                    "Prototype walkthrough: the real app screens rendered by Robolectric on a "
                    "computer, not a phone recording. Demo mode, fictional messages.",
                ],
            ),
            5.0,
        )
        prev: tuple[str, Image.Image] | None = None
        for row in rows:
            name, seconds, caption = row[0], float(row[1]), row[2]
            tap = tuple(int(v) for v in row[3].split(",")) if len(row) > 3 and row[3] else None
            img = framed(frames_dir / f"{name}.png", caption, tap)  # type: ignore[arg-type]
            if prev is not None and not _same_scene(prev[0], name):
                for i in range(1, FADE_FRAMES + 1):
                    add(Image.blend(prev[1], img, i / (FADE_FRAMES + 1)), 1 / 30)
            add(img, seconds)
            prev = (name, img)
        add(
            card(
                "Fully offline",
                [
                    "No internet permission. Messages are checked on the phone and never stored.",
                    "Built for parents and grandparents who may not spot a scam in time.",
                    "Prototype: not yet on the Play Store.",
                ],
            ),
            5.0,
        )

        concat = t / "list.txt"
        with concat.open("w") as f:
            for p, s in seq:
                f.write(f"file '{p}'\nduration {s}\n")
            f.write(f"file '{seq[-1][0]}'\n")  # ffmpeg's concat demuxer needs the last file twice
        cmd = ["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0"]
        cmd += ["-i", str(concat), "-vf", "fps=30,format=yuv420p", "-c:v", "libx264"]
        cmd += ["-crf", "20", "-movflags", "+faststart", str(out)]
        subprocess.run(cmd, check=True)
    print(f"wrote {out}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(Path(sys.argv[1]), Path(sys.argv[2]))
