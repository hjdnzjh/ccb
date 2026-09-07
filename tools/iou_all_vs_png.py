# -*- coding: utf-8 -*-
"""Find best affine (tx,ty,sx,sy,rot) mapping all.svg -> PNG land mask."""
from __future__ import annotations

import math
import re
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

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


def main():
    png = Image.open(sorted(MAP.glob("*.png"), key=lambda p: p.stat().st_size, reverse=True)[0]).convert("RGB")
    W, H = png.size
    rgb = np.asarray(png)
    land = ~((rgb[:, :, 0] < 40) & (rgb[:, :, 1] < 40) & (rgb[:, :, 2] < 40))
    land_f = land.astype(np.float32)
    ys, xs = np.where(land)
    px0, py0, px1, py1 = xs.min(), ys.min(), xs.max(), ys.max()
    print("land bbox", px0, py0, px1, py1, "area", land.sum())

    aw, ah, ad = parse_svg(MAP / "all.svg")
    pts = path_pts(ad)
    ax0, ay0 = pts[:, 0].min(), pts[:, 1].min()
    awb, ahb = pts[:, 0].max() - ax0, pts[:, 1].max() - ay0

    # downsample for speed
    f = 4
    land_s = land_f[::f, ::f]
    Hs, Ws = land_s.shape

    best = None
    # try uniform and anisotropic scales near bbox fit
    for sx in np.linspace(0.9, 1.15, 6):
        for sy in np.linspace(0.9, 1.15, 6):
            scale_x = (px1 - px0) / awb * sx
            scale_y = (py1 - py0) / ahb * sy
            for dtx in range(-40, 41, 10):
                for dty in range(-40, 41, 10):
                    mapped = np.empty_like(pts)
                    mapped[:, 0] = (pts[:, 0] - ax0) * scale_x + px0 + dtx
                    mapped[:, 1] = (pts[:, 1] - ay0) * scale_y + py0 + dty
                    img = Image.new("L", (W, H), 0)
                    ImageDraw.Draw(img).polygon([(float(x), float(y)) for x, y in mapped], fill=255)
                    m = np.asarray(img)[::f, ::f].astype(np.float32) / 255.0
                    inter = (m * land_s).sum()
                    union = ((m + land_s) > 0).sum()
                    iou = inter / (union + 1e-6)
                    if best is None or iou > best[0]:
                        best = (iou, scale_x, scale_y, dtx, dty, mapped)

    print("best IoU", best[0], "sx", best[1], "sy", best[2], "dtx", best[3], "dty", best[4])

    # also try uniform scale only
    bestu = None
    for s in np.linspace(1.4, 1.9, 26):
        for dtx in range(-80, 81, 8):
            for dty in range(-80, 81, 8):
                mapped = np.empty_like(pts)
                mapped[:, 0] = (pts[:, 0] - ax0) * s + px0 + dtx
                mapped[:, 1] = (pts[:, 1] - ay0) * s + py0 + dty
                img = Image.new("L", (W, H), 0)
                ImageDraw.Draw(img).polygon([(float(x), float(y)) for x, y in mapped], fill=255)
                m = np.asarray(img)[::f, ::f].astype(np.float32) / 255.0
                inter = (m * land_s).sum()
                union = ((m + land_s) > 0).sum()
                iou = inter / (union + 1e-6)
                if bestu is None or iou > bestu[0]:
                    bestu = (iou, s, dtx, dty, mapped)
    print("best uniform IoU", bestu[0], "s", bestu[1], "dtx", bestu[2], "dty", bestu[3])

    # save best overlay
    over = png.convert("RGBA")
    draw = ImageDraw.Draw(over)
    draw.polygon([(float(x), float(y)) for x, y in best[5]], outline=(255, 0, 0, 255))
    over.save(CHECK / "all_best_iou_on_png.png")
    over2 = png.convert("RGBA")
    ImageDraw.Draw(over2).polygon([(float(x), float(y)) for x, y in bestu[4]], outline=(0, 255, 0, 255))
    over2.save(CHECK / "all_best_uniform_on_png.png")


if __name__ == "__main__":
    main()
