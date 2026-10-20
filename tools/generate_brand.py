#!/usr/bin/env python3
"""Generate VictorLaunch brand PNGs (adaptive icon + wordmark) from the attached lockup."""

from __future__ import annotations

import math
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
OUT_RES = os.path.join(ROOT, "app", "src", "main", "res")
OUT_BRAND = os.path.join(ROOT, "docs", "screenshots")

CHARCOAL = (11, 12, 18, 255)
CYAN = (0, 229, 255, 255)
MAGENTA = (232, 64, 255, 255)
MID = (120, 90, 255, 255)


def lerp(a: tuple[int, ...], b: tuple[int, ...], t: float) -> tuple[int, ...]:
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def ring_color(angle: float) -> tuple[int, int, int, int]:
    # 0 = top, clockwise-ish. Cyan at ~315deg (upper-left), magenta at ~135deg.
    t = (math.sin(angle) * 0.5 + 0.5)
    if t < 0.5:
        c = lerp(CYAN, MID, t * 2)
    else:
        c = lerp(MID, MAGENTA, (t - 0.5) * 2)
    return (c[0], c[1], c[2], 255)


def draw_glow_ring(img: Image.Image, cx: float, cy: float, r: float, width: float) -> None:
    w, h = img.size
    px = img.load()
    outer_glow = width * 1.8
    core_half = width * 0.38
    for y in range(h):
        dy = y - cy
        for x in range(w):
            dx = x - cx
            dist = math.hypot(dx, dy)
            delta = abs(dist - r)
            if delta > outer_glow:
                continue
            angle = math.atan2(dy, dx)
            col = ring_color(angle)
            if delta <= core_half:
                alpha = 255
            else:
                t = (delta - core_half) / (outer_glow - core_half)
                alpha = int(255 * (1.0 - t) ** 2)
            if alpha <= 0:
                continue
            r0, g0, b0, a0 = px[x, y]
            a = alpha / 255.0
            out_a = a + (a0 / 255.0) * (1 - a)
            if out_a <= 0:
                continue
            px[x, y] = (
                int((col[0] * a + r0 * (a0 / 255.0) * (1 - a)) / out_a),
                int((col[1] * a + g0 * (a0 / 255.0) * (1 - a)) / out_a),
                int((col[2] * a + b0 * (a0 / 255.0) * (1 - a)) / out_a),
                int(out_a * 255),
            )


def rounded_rect(draw: ImageDraw.ImageDraw, box, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def draw_grid_and_rocket(img: Image.Image, cx: float, cy: float, size: float) -> None:
    d = ImageDraw.Draw(img)
    half = size / 2
    outer = [cx - half, cy - half, cx + half, cy + half]
    # dark plate
    rounded_rect(
        d,
        outer,
        radius=size * 0.18,
        fill=(12, 16, 32, 230),
        outline=(40, 60, 120, 180),
        width=max(2, int(size * 0.012)),
    )
    gap = size * 0.045
    cell = (size - gap * 4) / 3
    origin_x = cx - half + gap
    origin_y = cy - half + gap
    colors = [
        [(30, 80, 200, 220), (20, 50, 140, 220), (40, 40, 160, 220)],
        [(25, 70, 190, 220), (0, 0, 0, 0), (140, 50, 220, 220)],
        [(80, 40, 200, 220), (160, 50, 230, 220), (30, 70, 180, 220)],
    ]
    for row in range(3):
        for col in range(3):
            if row == 1 and col == 1:
                continue
            x0 = origin_x + col * (cell + gap)
            y0 = origin_y + row * (cell + gap)
            fill = colors[row][col]
            rounded_rect(
                d,
                [x0, y0, x0 + cell, y0 + cell],
                radius=cell * 0.28,
                fill=fill,
            )

    # rocket
    rocket = Image.new("RGBA", img.size, (0, 0, 0, 0))
    rd = ImageDraw.Draw(rocket)
    body_top = cy - size * 0.38
    body_bot = cy + size * 0.12
    # fuselage diamond-ish
    nose = (cx, body_top)
    left = (cx - size * 0.16, body_bot)
    right = (cx + size * 0.16, body_bot)
    mid_l = (cx - size * 0.11, cy - size * 0.02)
    mid_r = (cx + size * 0.11, cy - size * 0.02)
    rd.polygon([nose, mid_r, right, left, mid_l], fill=(40, 140, 255, 255))
    # right half magenta overlay
    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    od.polygon([nose, mid_r, right, (cx, body_bot)], fill=(180, 70, 255, 210))
    rocket.alpha_composite(overlay)
    # fins
    rd.polygon(
        [
            (cx - size * 0.04, cy + size * 0.02),
            (cx - size * 0.28, cy + size * 0.34),
            (cx - size * 0.02, cy + size * 0.16),
        ],
        fill=(0, 210, 255, 255),
    )
    rd.polygon(
        [
            (cx + size * 0.04, cy + size * 0.02),
            (cx + size * 0.28, cy + size * 0.34),
            (cx + size * 0.02, cy + size * 0.16),
        ],
        fill=(210, 70, 255, 255),
    )
    # window diamond
    w = size * 0.055
    wy = cy - size * 0.14
    rd.polygon(
        [(cx, wy - w), (cx + w, wy), (cx, wy + w), (cx - w, wy)],
        fill=(8, 12, 28, 255),
        outline=(180, 230, 255, 255),
    )
    glow = rocket.filter(ImageFilter.GaussianBlur(radius=2))
    img.alpha_composite(glow)
    img.alpha_composite(rocket)


def draw_wordmark(img: Image.Image, cx: float, y: float, font_size: int) -> None:
    # Try a bold sans; fall back to default.
    try:
        font = ImageFont.truetype("DejaVuSans-Bold.ttf", font_size)
    except OSError:
        try:
            font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", font_size)
        except OSError:
            font = ImageFont.load_default()
    text = "VictorLaunch"
    dtmp = ImageDraw.Draw(img)
    bbox = dtmp.textbbox((0, 0), text, font=font)
    w = bbox[2] - bbox[0]
    h = bbox[3] - bbox[1]
    x = cx - w / 2
    # draw per-glyph gradient
    cursor = x
    for i, ch in enumerate(text):
        t = i / max(1, len(text) - 1)
        col = lerp(CYAN, MAGENTA, t)
        dtmp.text((cursor, y), ch, font=font, fill=col)
        cursor += dtmp.textbbox((0, 0), ch, font=font)[2]


def save_resized(src: Image.Image, path: str, size: int) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    src.resize((size, size), Image.Resampling.LANCZOS).save(path, "PNG")


def main() -> None:
    # Full lockup 1024
    lockup = Image.new("RGBA", (1024, 1024), CHARCOAL)
    draw_glow_ring(lockup, 512, 430, 310, 28)
    draw_grid_and_rocket(lockup, 512, 430, 420)
    draw_wordmark(lockup, 512, 800, 72)
    os.makedirs(OUT_BRAND, exist_ok=True)
    lockup.save(os.path.join(OUT_BRAND, "victorlaunch-lockup.png"), "PNG")
    lockup.save(os.path.join(ROOT, "branding", "victorlaunch-logo-square.png"), "PNG")

    # Adaptive foreground: transparent, emblem only, content in ~66% safe zone
    fg = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
    draw_glow_ring(fg, 512, 512, 300, 26)
    draw_grid_and_rocket(fg, 512, 512, 400)
    fg_path = os.path.join(OUT_RES, "drawable", "ic_launcher_foreground.png")
    os.makedirs(os.path.dirname(fg_path), exist_ok=True)
    fg.save(fg_path, "PNG")

    bg = Image.new("RGBA", (1024, 1024), CHARCOAL)
    bg_path = os.path.join(OUT_RES, "drawable", "ic_launcher_background.png")
    bg.save(bg_path, "PNG")

    # Legacy mipmaps: composite fg on bg
    legacy = Image.new("RGBA", (1024, 1024), CHARCOAL)
    legacy.alpha_composite(fg)
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, px in densities.items():
        save_resized(legacy, os.path.join(OUT_RES, folder, "ic_launcher.png"), px)
        save_resized(legacy, os.path.join(OUT_RES, folder, "ic_launcher_round.png"), px)

    print("brand assets written")


if __name__ == "__main__":
    main()
