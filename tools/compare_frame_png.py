# -*- coding: utf-8 -*-
"""Analyze how all.svg relates to PNG silhouette; try scale+translate registration."""
from __future__ import annotations

import re
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageOps

ROOT = Path(r"D:\ccb")
MAP = ROOT / "map"
CHECK = ROOT / "tools" / "map_align_out"


def parse_svg(path: Path):
    t = path.read_text(encoding="utf-8")
    w = float(re.search(r'width="([\d.]+)', t).group(1))
    h = float(re.search(r'height="([\d.]+)', t).group(1))
    d = re.search(r'\bd="([^"]+)"', t).group(1)
    return w, h, d


def path_pts(d: str):
    tok = re.findall(r"[MmLlCcZz]|-?\d*\.?\d+(?:e[-+]?\d+)?", d.replace(",", " "))
    pts, i = [], 0
    cx = cy = sx = sy = 0.0
    cmd = None

    def n():
        nonlocal i
        v = float(tok[i])
        i += 1
        return v

    while i < len(tok):
        t = tok[i]
        if re.match(r"[A-Za-z]", t):
            cmd = t
            i += 1
            if cmd in "Zz":
                pts.append((sx, sy))
                cx, cy = sx, sy
            continue
        if cmd in "Mm":
            x, y = n(), n()
            if cmd == "m":
                x += cx
                y += cy
            cx, cy, sx, sy = x, y, x, y
            pts.append((cx, cy))
            cmd = "L" if cmd == "M" else "l"
        elif cmd in "Ll":
            x, y = n(), n()
            if cmd == "l":
                x += cx
                y += cy
            cx, cy = x, y
            pts.append((cx, cy))
        elif cmd in "Cc":
            x1, y1, x2, y2, x, y = n(), n(), n(), n(), n(), n()
            if cmd == "c":
                x1 += cx
                y1 += cy
                x2 += cx
                y2 += cy
                x += cx
                y += cy
            for k in range(1, 5):
                tt = k / 4
                mt = 1 - tt
                pts.append(
                    (
                        mt**3 * cx + 3 * mt**2 * tt * x1 + 3 * mt * tt**2 * x2 + tt**3 * x,
                        mt**3 * cy + 3 * mt**2 * tt * y1 + 3 * mt * tt**2 * y2 + tt**3 * y,
                    )
                )
            cx, cy = x, y
        else:
            i += 1
    return np.array(pts, dtype=np.float64)


def png_mask(img: Image.Image) -> np.ndarray:
    """Non-white / non-near-white pixels as silhouette."""
    a = np.asarray(img.convert("RGB"), dtype=np.int16)
    # near-white background
    white = (a[:, :, 0] > 245) & (a[:, :, 1] > 245) & (a[:, :, 2] > 245)
    # also ignore very light gray
    light = (a.min(axis=2) > 235)
    mask = ~(white | light)
    return mask.astype(np.uint8) * 255


def main():
    aw, ah, ad = parse_svg(MAP / "all.svg")
    pts = path_pts(ad)
    png = Image.open(sorted(MAP.glob("*.png"), key=lambda p: p.stat().st_size, reverse=True)[0]).convert("RGBA")
    pm = png_mask(png)
    # bbox of png content
    ys, xs = np.where(pm > 0)
    print("png content bbox", xs.min(), ys.min(), xs.max(), ys.max(), "size", png.size)
    print("all.svg bbox", pts[:, 0].min(), pts[:, 1].min(), pts[:, 0].max(), pts[:, 1].max())

    # Render all.svg mask at PNG resolution with affine search: sx,sy,tx,ty
    # Coarse: match bounding boxes with independent scale
    px0, py0, px1, py1 = xs.min(), ys.min(), xs.max(), ys.max()
    pw, ph = px1 - px0 + 1, py1 - py0 + 1
    ax0, ay0 = pts[:, 0].min(), pts[:, 1].min()
    awb, ahb = pts[:, 0].max() - ax0, pts[:, 1].max() - ay0

    # Stretch all.svg bbox to png content bbox
    sx = pw / awb
    sy = ph / ahb
    mapped = np.empty_like(pts)
    mapped[:, 0] = (pts[:, 0] - ax0) * sx + px0
    mapped[:, 1] = (pts[:, 1] - ay0) * sy + py0

    overlay = png.copy()
    draw = ImageDraw.Draw(overlay)
    draw.polygon([(float(x), float(y)) for x, y in mapped], outline=(255, 0, 0, 255))
    overlay.save(CHECK / "all_stretched_on_png.png")

    # Also uniform scale + center
    s = min(pw / awb, ph / ahb)
    mapped2 = np.empty_like(pts)
    mapped2[:, 0] = (pts[:, 0] - ax0) * s + px0 + (pw - awb * s) / 2
    mapped2[:, 1] = (pts[:, 1] - ay0) * s + py0 + (ph - ahb * s) / 2
    overlay2 = png.copy()
    ImageDraw.Draw(overlay2).polygon([(float(x), float(y)) for x, y in mapped2], outline=(0, 180, 80, 255))
    overlay2.save(CHECK / "all_uniform_on_png.png")

    # Save png mask preview
    Image.fromarray(pm).save(CHECK / "png_mask.png")
    print("sx,sy stretch", sx, sy, "uniform", s)
    print("saved overlays")


if __name__ == "__main__":
    main()
