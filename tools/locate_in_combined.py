# -*- coding: utf-8 -*-
import re
from pathlib import Path
from PIL import Image, ImageDraw
import numpy as np

BASE = Path(r"D:\ccb\map")


def parse(p):
    t = Path(p).read_text(encoding="utf-8")
    w = float(re.search(r'width="([\d.]+)', t).group(1))
    h = float(re.search(r'height="([\d.]+)', t).group(1))
    d = re.search(r'\bd="([^"]+)"', t).group(1)
    return w, h, d


def pts(d):
    tok = re.findall(r"[MmLlCcZz]|-?\d*\.?\d+(?:e[-+]?\d+)?", d.replace(",", " "))
    out, i = [], 0
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
                out.append((sx, sy))
                cx, cy = sx, sy
            continue
        if cmd in "Mm":
            x, y = n(), n()
            if cmd == "m":
                x += cx
                y += cy
            cx, cy, sx, sy = x, y, x, y
            out.append((cx, cy))
            cmd = "L" if cmd == "M" else "l"
        elif cmd in "Ll":
            x, y = n(), n()
            if cmd == "l":
                x += cx
                y += cy
            cx, cy = x, y
            out.append((cx, cy))
        elif cmd in "Cc":
            x1, y1, x2, y2, x, y = n(), n(), n(), n(), n(), n()
            if cmd == "c":
                x1 += cx
                y1 += cy
                x2 += cx
                y2 += cy
                x += cx
                y += cy
            for k in range(1, 4):
                tt = k / 3
                mt = 1 - tt
                out.append(
                    (
                        mt**3 * cx + 3 * mt**2 * tt * x1 + 3 * mt * tt**2 * x2 + tt**3 * x,
                        mt**3 * cy + 3 * mt**2 * tt * y1 + 3 * mt * tt**2 * y2 + tt**3 * y,
                    )
                )
            cx, cy = x, y
        else:
            i += 1
    return np.array(out)


def ras(d, scale=1.0):
    p = pts(d) * scale
    minx, miny = p.min(0)
    maxx, maxy = p.max(0)
    ow = int(maxx - minx) + 4
    oh = int(maxy - miny) + 4
    sh = [(a - minx + 2, b - miny + 2) for a, b in p]
    im = Image.new("L", (ow, oh), 0)
    ImageDraw.Draw(im).polygon(sh, fill=255)
    return np.asarray(im), (minx - 2, miny - 2)


cw, ch, cd = parse(BASE / "cunanxian&tongluxian.svg")
c_fill, _ = ras(cd, 1.0)
print("combined", cw, ch, c_fill.shape)
for name in ["chunan.svg", "tonglu.svg"]:
    w, h, d = parse(BASE / name)
    best = None
    for sc in [0.7, 0.85, 1.0, 1.15, 1.3, 1.45]:
        f, _ = ras(d, sc)
        ph, pw = f.shape
        H, W = c_fill.shape
        if ph >= H or pw >= W:
            continue
        for y in range(0, H - ph, 4):
            for x in range(0, W - pw, 4):
                patch = c_fill[y : y + ph, x : x + pw]
                inter = ((patch > 0) & (f > 0)).sum()
                cover = inter / ((f > 0).sum() + 1e-6)
                if best is None or cover > best[0]:
                    best = (cover, x, y, sc)
    print(name, best)
