# -*- coding: utf-8 -*-
"""Compare all.svg vs all-1.svg and overlay both on 杭州.png."""
from __future__ import annotations

import re
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(r"D:\ccb")
MAP = ROOT / "map"
CHECK = ROOT / "tools" / "map_align_out"
CHECK.mkdir(parents=True, exist_ok=True)


def parse(path: Path):
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
    w1, h1, d1 = parse(MAP / "all.svg")
    w2, h2, d2 = parse(MAP / "all-1.svg")
    p1, p2 = path_pts(d1), path_pts(d2)
    print("all.svg  ", w1, h1, "pts", len(p1), "bbox", p1.min(0), p1.max(0))
    print("all-1.svg", w2, h2, "pts", len(p2), "bbox", p2.min(0), p2.max(0))
    print("same file?", d1 == d2)
    print("d len", len(d1), len(d2))

    # raster both at native size and IoU
    def raster(pts, w, h):
        img = Image.new("L", (int(math.ceil(w)), int(math.ceil(h))), 0)
        ImageDraw.Draw(img).polygon([(float(x), float(y)) for x, y in pts], fill=255)
        return np.asarray(img) > 0

    import math

    a = raster(p1, w1, h1)
    b = raster(p2, w2, h2)
    # pad to same size
    H = max(a.shape[0], b.shape[0])
    W = max(a.shape[1], b.shape[1])
    A = np.zeros((H, W), dtype=bool)
    B = np.zeros((H, W), dtype=bool)
    A[: a.shape[0], : a.shape[1]] = a
    B[: b.shape[0], : b.shape[1]] = b
    inter = (A & B).sum()
    union = (A | B).sum()
    print("IoU all vs all-1", inter / union)
    print("only all", (A & ~B).sum(), "only all-1", (B & ~A).sum())

    # visual: all red, all-1 green on white
    vis = Image.new("RGB", (W, H), (255, 255, 255))
    px = vis.load()
    for y in range(H):
        for x in range(W):
            if A[y, x] and B[y, x]:
                px[x, y] = (180, 180, 80)
            elif A[y, x]:
                px[x, y] = (220, 80, 80)
            elif B[y, x]:
                px[x, y] = (80, 180, 100)
    ImageDraw.Draw(vis).polygon([(float(x), float(y)) for x, y in p1], outline=(180, 0, 0))
    ImageDraw.Draw(vis).polygon([(float(x), float(y)) for x, y in p2], outline=(0, 140, 40))
    vis.save(CHECK / "all_vs_all1.png")

    # overlay both on PNG (bbox stretch)
    png = Image.open(sorted(MAP.glob("*.png"), key=lambda p: p.stat().st_size, reverse=True)[0]).convert("RGBA")
    rgb = np.asarray(png.convert("RGB"))
    land = ~((rgb[:, :, 0] < 40) & (rgb[:, :, 1] < 40) & (rgb[:, :, 2] < 40))
    ys, xs = np.where(land)
    px0, py0, px1, py1 = int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())

    def stretch(pts):
        ax0, ay0 = pts[:, 0].min(), pts[:, 1].min()
        awb, ahb = pts[:, 0].max() - ax0, pts[:, 1].max() - ay0
        out = np.empty_like(pts)
        out[:, 0] = (pts[:, 0] - ax0) / awb * (px1 - px0) + px0
        out[:, 1] = (pts[:, 1] - ay0) / ahb * (py1 - py0) + py0
        return out

    over = png.copy()
    draw = ImageDraw.Draw(over)
    draw.polygon([(float(x), float(y)) for x, y in stretch(p1)], outline=(255, 40, 40, 255))
    draw.polygon([(float(x), float(y)) for x, y in stretch(p2)], outline=(40, 220, 80, 255))
    over.save(CHECK / "all_and_all1_on_png.png")
    print("saved QA overlays")


if __name__ == "__main__":
    main()
