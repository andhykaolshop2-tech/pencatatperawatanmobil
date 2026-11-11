#!/usr/bin/env python3
"""Turns the raw app screenshots into Play Store screenshots (1080x1920, 9:16).

Every picture gets the same layout: a short caption and, below it, the real and unchanged
screenshot inside a simple drawn phone frame, on the teal background.

    python tools/take_screenshots.py     # raw screenshots -> play-store/screenshots/raw/<language>/
    python tools/frame_screenshots.py    # framed pictures -> play-store/screenshots/<language>/

Captions: play-store/screenshots/captions.json (a line break is written as \\n).
Background: play-store/graphics/screenshot-background.png, made once by
`python tools/generate_images.py --only screenshot_bg`; without that file a plain gradient
is drawn instead, so this script never needs the image API.
Needs Pillow (pip install -r tools/requirements.txt).

Play Store rules these files follow: PNG without transparency, 9:16, every side between
320 and 3840 px, 2 to 8 screenshots per language.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
RAW = ROOT / "play-store" / "screenshots" / "raw"
OUT = ROOT / "play-store" / "screenshots"
CAPTIONS = OUT / "captions.json"
BACKGROUND = ROOT / "play-store" / "graphics" / "screenshot-background.png"

WIDTH, HEIGHT = 1080, 1920
SCREEN_WIDTH = 670            # width of the screenshot inside the frame
BEZEL = 16
BODY_RADIUS = 66
SCREEN_RADIUS = 50
BOTTOM_MARGIN = 52
MIN_CAPTION_AREA = 300        # space kept free above the phone
CAPTION_MAX_WIDTH = WIDTH - 2 * 64
CAPTION_SIZES = range(76, 43, -2)

TEAL = (11, 95, 106)
TEAL_DARK = (6, 58, 66)
BODY_COLOR = (18, 22, 26)
RIM_COLOR = (92, 104, 112)
CAPTION_COLOR = (255, 255, 255)
ACCENT_COLOR = (255, 179, 0)

# The first font that exists is used (Inter is part of Android Studio's bundled runtime).
FONT_CANDIDATES = [
    r"C:\Program Files\Android\Android Studio\jbr\lib\fonts\Inter-SemiBold.otf",
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home/lib/fonts/Inter-SemiBold.otf",
    "/opt/android-studio/jbr/lib/fonts/Inter-SemiBold.otf",
    r"C:\Windows\Fonts\segoeuib.ttf",
    r"C:\Windows\Fonts\arialbd.ttf",
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]

try:
    from PIL import Image, ImageDraw, ImageFilter, ImageFont
except ImportError:
    sys.exit("Pillow is missing. Install it with:  pip install -r tools/requirements.txt")

LANCZOS = getattr(Image, "Resampling", Image).LANCZOS


def find_font(explicit: str | None) -> str:
    candidates = [explicit] if explicit else FONT_CANDIDATES
    for candidate in candidates:
        if candidate and pathlib.Path(candidate).exists():
            return str(candidate)
    sys.exit("No caption font found. Pass one with --font path/to/font.ttf")


def cover(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    """Scales and centre-crops the picture so that it fills `size`."""
    width, height = size
    scale = max(width / image.width, height / image.height)
    scaled = image.resize((max(width, round(image.width * scale)), max(height, round(image.height * scale))), LANCZOS)
    left, top = (scaled.width - width) // 2, (scaled.height - height) // 2
    return scaled.crop((left, top, left + width, top + height))


def make_background() -> Image.Image:
    if BACKGROUND.exists():
        return cover(Image.open(BACKGROUND).convert("RGB"), (WIDTH, HEIGHT))
    column = Image.new("RGB", (1, HEIGHT))
    for y in range(HEIGHT):
        t = y / (HEIGHT - 1)
        column.putpixel((0, y), tuple(round(a + (b - a) * t) for a, b in zip(TEAL, TEAL_DARK)))
    return column.resize((WIDTH, HEIGHT))


def rounded_mask(size: tuple[int, int], radius: int, oversample: int = 4) -> Image.Image:
    """An anti-aliased rounded rectangle as a mask."""
    width, height = size
    big = Image.new("L", (width * oversample, height * oversample), 0)
    ImageDraw.Draw(big).rounded_rectangle(
        (0, 0, width * oversample - 1, height * oversample - 1), radius * oversample, fill=255)
    return big.resize(size, LANCZOS)


def fit_font(draw: ImageDraw.ImageDraw, lines: list[str], font_path: str) -> ImageFont.FreeTypeFont:
    """The largest caption size at which every line fits the picture."""
    font = None
    for size in CAPTION_SIZES:
        font = ImageFont.truetype(font_path, size)
        if max(draw.textlength(line, font=font) for line in lines) <= CAPTION_MAX_WIDTH:
            break
    return font


def frame(raw_path: pathlib.Path, caption: str, target: pathlib.Path, background: Image.Image, font_path: str) -> None:
    raw = Image.open(raw_path).convert("RGB")
    canvas = background.copy()

    # ---- phone geometry (the screenshot keeps its own aspect ratio)
    screen_w = SCREEN_WIDTH
    screen_h = round(screen_w * raw.height / raw.width)
    limit = HEIGHT - MIN_CAPTION_AREA - BOTTOM_MARGIN - 2 * BEZEL
    if screen_h > limit:
        screen_h = limit
        screen_w = round(screen_h * raw.width / raw.height)
    body_w, body_h = screen_w + 2 * BEZEL, screen_h + 2 * BEZEL
    body_x, body_y = (WIDTH - body_w) // 2, HEIGHT - BOTTOM_MARGIN - body_h

    # ---- soft shadow under the phone
    shadow = Image.new("L", (WIDTH, HEIGHT), 0)
    shadow.paste(rounded_mask((body_w, body_h), BODY_RADIUS).point(lambda value: value * 130 // 255),
                 (body_x, body_y + 26))
    canvas.paste((0, 26, 31), (0, 0), shadow.filter(ImageFilter.GaussianBlur(32)))

    # ---- side buttons, body, rim
    draw = ImageDraw.Draw(canvas)
    for top, height in ((300, 120), (470, 190)):
        draw.rounded_rectangle((body_x + body_w - 4, body_y + top, body_x + body_w + 5, body_y + top + height),
                               4, fill=BODY_COLOR)
    canvas.paste(RIM_COLOR, (body_x - 2, body_y - 2), rounded_mask((body_w + 4, body_h + 4), BODY_RADIUS + 2))
    canvas.paste(BODY_COLOR, (body_x, body_y), rounded_mask((body_w, body_h), BODY_RADIUS))

    # ---- the real screenshot
    screen = raw.resize((screen_w, screen_h), LANCZOS)
    canvas.paste(screen, (body_x + BEZEL, body_y + BEZEL), rounded_mask((screen_w, screen_h), SCREEN_RADIUS))

    # ---- caption, centred in the space above the phone, with a small accent bar
    draw = ImageDraw.Draw(canvas)
    lines = [line.strip() for line in caption.split("\n") if line.strip()]
    font = fit_font(draw, lines, font_path)
    line_height = round(font.size * 1.22)
    accent_h, gap = 8, 30
    block_h = accent_h + gap + line_height * len(lines)
    block_top = max(40, (body_y - block_h) // 2)
    draw.rounded_rectangle((WIDTH // 2 - 44, block_top, WIDTH // 2 + 44, block_top + accent_h), 4, fill=ACCENT_COLOR)
    text_top = block_top + accent_h + gap
    for index, line in enumerate(lines):
        draw.text((WIDTH // 2, text_top + line_height * (index + 0.5)), line, font=font,
                  fill=CAPTION_COLOR, anchor="mm")

    target.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(target, optimize=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--only", help="one listing language, for example de-DE")
    parser.add_argument("--font", help="caption font file (default: Inter from Android Studio, then system fonts)")
    args = parser.parse_args()

    if not RAW.exists():
        sys.exit("No raw screenshots yet: run tools/take_screenshots.py first.")
    captions = json.loads(CAPTIONS.read_text(encoding="utf-8"))
    font_path = find_font(args.font)
    background = make_background()
    if not BACKGROUND.exists():
        print("note: no screenshot-background.png, using a plain gradient")

    problems = 0
    for folder in sorted(p for p in RAW.iterdir() if p.is_dir()):
        language = folder.name
        if args.only and language != args.only:
            continue
        texts = captions.get(language, {})
        out_folder = OUT / language
        if out_folder.exists():
            for old in out_folder.glob("*.png"):
                old.unlink()
        count = 0
        for raw_path in sorted(folder.glob("*.png")):
            caption = texts.get(raw_path.stem)
            if not caption:
                print(f"  ! no caption for {language}/{raw_path.stem} in captions.json - skipped")
                problems += 1
                continue
            target = out_folder / raw_path.name
            frame(raw_path, caption, target, background, font_path)
            with Image.open(target) as check:
                ok = check.size == (WIDTH, HEIGHT) and check.mode == "RGB" and target.stat().st_size < 8 * 1024 * 1024
            print(f"  {'ok' if ok else '!!'} {target.relative_to(ROOT)}  {target.stat().st_size // 1024} KB")
            problems += 0 if ok else 1
            count += 1
        if not 2 <= count <= 8:
            print(f"  ! {language}: {count} screenshots (Play Store wants 2 to 8)")
            problems += 1
    print("done" if problems == 0 else f"finished with {problems} problem(s)")
    return 0 if problems == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
