# -*- coding: utf-8 -*-
"""
Fit district SVGs to 杭州.png template (not shown in UI).
Export hangzhouShapes.js + white-bg composite for QA.
"""
from __future__ import annotations

import json
import math
import re
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(r"D:\ccb")
MAP = ROOT / "map"
OUT_JS = ROOT / "web-admin" / "src" / "data" / "hangzhouShapes.js"
CHECK = ROOT / "tools" / "map_align_out"
CHECK.mkdir(parents=True, exist_ok=True)

REF = sorted(MAP.glob("*.png"), key=lambda p: p.stat().st_size, reverse=True)[0]

FILES = [
    ("linan.svg", "临安区"),
    ("yuhang.svg", "余杭区"),
    ("linping.svg", "临平区"),
    ("qiantang.svg", "钱塘区"),
    ("gongshu.svg", "拱墅区"),
    ("xihu.svg", "西湖区"),
    ("shangche.svg", "上城区"),
    ("binjiang.svg", "滨江区"),
    ("xiaoshan.svg", "萧山区"),
    ("fuyang.svg", "富阳区"),
    ("jiandeshi.svg", "建德市"),
    ("chunan.svg", "淳安县"),
    ("tonglu.svg", "桐庐县"),
]

# Manual seed centers on template (1078x750) — from label positions
SEEDS = {
    "临安区": (340, 195),
    "余杭区": (620, 155),
    "临平区": (760, 105),
    "钱塘区": (925, 235),
    "拱墅区": (655, 245),
    "西湖区": (595, 305),
    "上城区": (680, 305),
    "滨江区": (655, 360),
    "萧山区": (820, 430),
    "富阳区": (560, 430),
    "建德市": (515, 620),
    "淳安县": (230, 530),
    "桐庐县": (430, 490),
}

# Optional hard overrides after visual QA: name -> (x, y, scale, rotate)
# Leave empty first; fill when tuning.
OVERRIDES: dict = {}


def parse_svg(p: Path):
    t = p.read_text(encoding="utf-8")
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
    return pts


def raster(d, scale=1.0, angle=0.0, pad=2):
    pts = np.array(path_pts(d), dtype=np.float64)
    minx, miny = pts.min(0)
    maxx, maxy = pts.max(0)
    cx, cy = (minx + maxx) / 2, (miny + maxy) / 2
    rad = math.radians(angle)
    c, s = math.cos(rad), math.sin(rad)
    rot = np.empty_like(pts)
    rot[:, 0] = (c * (pts[:, 0] - cx) - s * (pts[:, 1] - cy) + cx) * scale
    rot[:, 1] = (s * (pts[:, 0] - cx) + c * (pts[:, 1] - cy) + cy) * scale
    minx, miny = rot.min(0)
    maxx, maxy = rot.max(0)
    ow = max(2, int(math.ceil(maxx - minx)) + pad * 2)
    oh = max(2, int(math.ceil(maxy - miny)) + pad * 2)
    shifted = [(p[0] - minx + pad, p[1] - miny + pad) for p in rot]
    img = Image.new("L", (ow, oh), 0)
    ImageDraw.Draw(img).polygon(shifted, fill=255)
    fill = np.asarray(img, dtype=np.float32) / 255.0
    # edge
    er = np.asarray(img.filter(ImageFilter.MinFilter(3)), dtype=np.uint8)
    edge = ((np.asarray(img) > 0) & (er == 0)).astype(np.float32)
    edge = np.asarray(Image.fromarray((edge * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(3)), dtype=np.float32) / 255.0
    return fill, edge, (minx - pad, miny - pad)


def template_maps(img: Image.Image):
    rgb = np.asarray(img.convert("RGB"), dtype=np.float32)
    lum = rgb.mean(2)
    content = (lum > 22).astype(np.float32)
    # bright borders
    bright = (lum > 170).astype(np.float32)
    blur = np.asarray(img.convert("L").filter(ImageFilter.GaussianBlur(1.5)), dtype=np.float32)
    contrast = (np.abs(lum - blur) > 28).astype(np.float32)
    border = np.maximum(bright * (lum < 250), contrast * content)
    border = np.asarray(
        Image.fromarray((border * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(3)),
        dtype=np.float32,
    ) / 255.0
    return content, border


def downsample(a, f):
    if f <= 1:
        return a
    h, w = a.shape
    nh, nw = h // f, w // f
    a = a[: nh * f, : nw * f]
    return a.reshape(nh, f, nw, f).mean((1, 3))


def match_piece(content, border, fill, edge, hint, search=100):
    """Fast coarse-to-fine. Score: edge hit + content cover - spill."""
    H, W = content.shape
    ph, pw = fill.shape
    if ph >= H or pw >= W:
        return None
    f = 4 if max(ph, pw) > 80 else 2
    c_s, b_s = downsample(content, f), downsample(border, f)
    fill_s, edge_s = downsample(fill, f), downsample(edge, f)
    psh, psw = fill_s.shape
    hx, hy = hint[0] / f - psw / 2, hint[1] / f - psh / 2
    win = search / f
    x0 = int(max(0, hx - win))
    y0 = int(max(0, hy - win))
    x1 = int(min(c_s.shape[1] - psw, hx + win))
    y1 = int(min(c_s.shape[0] - psh, hy + win))
    if x1 < x0 or y1 < y0:
        return None

    best = None
    step = max(1, min(psw, psh) // 16)
    es = edge_s.sum() + 1e-6
    fs = fill_s.sum() + 1e-6
    for y in range(y0, y1 + 1, step):
        for x in range(x0, x1 + 1, step):
            cp = c_s[y : y + psh, x : x + psw]
            bp = b_s[y : y + psh, x : x + psw]
            edge_hit = (bp * edge_s).sum() / es
            cover = (cp * fill_s).sum() / fs
            spill = ((1 - cp) * fill_s).sum() / fs
            score = 2.0 * edge_hit + 0.7 * cover - 1.2 * spill
            if best is None or score > best[0]:
                best = (score, x, y)

    if not best:
        return None
    _, sx, sy = best
    # refine full-res
    bx, by = int(sx * f), int(sy * f)
    margin = f * step + 8
    best2 = None
    es = edge.sum() + 1e-6
    fs = fill.sum() + 1e-6
    for y in range(max(0, by - margin), min(H - ph, by + margin) + 1, 2):
        for x in range(max(0, bx - margin), min(W - pw, bx + margin) + 1, 2):
            cp = content[y : y + ph, x : x + pw]
            bp = border[y : y + ph, x : x + pw]
            edge_hit = (bp * edge).sum() / es
            cover = (cp * fill).sum() / fs
            spill = ((1 - cp) * fill).sum() / fs
            score = 2.0 * edge_hit + 0.7 * cover - 1.2 * spill
            if best2 is None or score > best2[0]:
                best2 = (score, x, y)
    if not best2:
        return best[0], bx, by
    # 1px
    _, bx, by = best2
    for y in range(max(0, by - 2), min(H - ph, by + 2) + 1):
        for x in range(max(0, bx - 2), min(W - pw, bx + 2) + 1):
            cp = content[y : y + ph, x : x + pw]
            bp = border[y : y + ph, x : x + pw]
            edge_hit = (bp * edge).sum() / es
            cover = (cp * fill).sum() / fs
            spill = ((1 - cp) * fill).sum() / fs
            score = 2.0 * edge_hit + 0.7 * cover - 1.2 * spill
            if score > best2[0]:
                best2 = (score, x, y)
    return best2


def main():
    img = Image.open(REF).convert("RGB")
    W, H = img.size
    print("template", W, H, REF.name.encode("unicode_escape").decode())
    content, border = template_maps(img)
    Image.fromarray((border * 255).astype(np.uint8)).save(CHECK / "template_border.png")

    shapes = []
    # white canvas composite (what UI will look like)
    white = Image.new("RGBA", (W, H), (255, 255, 255, 255))
    # also overlay on template for QA
    qa = img.convert("RGBA")
    ov_w = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ov_t = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    palette = [
        (70, 145, 220, 160),
        (0, 185, 175, 160),
        (230, 100, 80, 160),
        (150, 100, 210, 160),
        (240, 170, 50, 160),
        (70, 170, 100, 160),
        (220, 120, 180, 160),
    ]

    for idx, (fname, name) in enumerate(FILES):
        sw, sh, d = parse_svg(MAP / fname)
        hint = SEEDS[name]

        if name in OVERRIDES:
            x, y, sc, ang = OVERRIDES[name]
            fill, edge, local_off = raster(d, sc, ang)
            # interpret x,y as origin of svg (0,0)
            rx = int(round(x + local_off[0]))
            ry = int(round(y + local_off[1]))
            score = 0.0
            print(f"{name:8} OVERRIDE origin=({x},{y}) sc={sc} rot={ang}")
        else:
            if max(sw, sh) > 350:
                scales = [0.88, 0.94, 1.0, 1.06, 1.12, 1.2]
                angles = [0, -3, 3, -6, 6]
                search = 130
            elif max(sw, sh) < 90:
                scales = [1.0, 1.15, 1.3, 1.5, 1.7, 1.9]
                angles = [0, -4, 4, -8, 8]
                search = 80
            else:
                scales = [0.9, 0.96, 1.0, 1.08, 1.15, 1.25]
                angles = [0, -3, 3, -5, 5]
                search = 100

            best = None
            for ang in angles:
                for sc in scales:
                    fill, edge, local_off = raster(d, sc, ang)
                    if fill.shape[0] >= H - 2 or fill.shape[1] >= W - 2:
                        continue
                    m = match_piece(content, border, fill, edge, hint, search=search)
                    if not m:
                        continue
                    score, x, y = m
                    if best is None or score > best[0]:
                        best = (score, x, y, sc, ang, fill, edge, local_off)
            if best is None:
                print("FAIL", name)
                continue
            score, rx, ry, sc, ang, fill, edge, local_off = best
            x = rx - local_off[0]
            y = ry - local_off[1]
            print(f"{name:8} score={score:.3f} origin=({x:.1f},{y:.1f}) sc={sc:.2f} rot={ang}")

        fill, edge, local_off = raster(d, sc, ang)
        # place: origin (x,y) means path(0,0) maps there; raster top-left = origin+local_off
        rx = int(round(x + local_off[0]))
        ry = int(round(y + local_off[1]))
        fys, fxs = np.where(fill > 0.5)
        cx = float(rx + fxs.mean()) if fxs.size else x + sw * sc / 2
        cy = float(ry + fys.mean()) if fys.size else y + sh * sc / 2

        shapes.append(
            {
                "name": name,
                "names": [name],
                "file": fname,
                "x": round(float(x), 2),
                "y": round(float(y), 2),
                "w": round(float(sw * sc), 2),
                "h": round(float(sh * sc), 2),
                "scale": sc,
                "rotate": ang,
                "cx": round(cx, 2),
                "cy": round(cy, 2),
                "d": d,
                "nativeW": sw,
                "nativeH": sh,
                "shared": False,
                "score": round(float(score), 4),
            }
        )

        color = palette[idx % len(palette)]
        for yy, xx in zip(fys[::1], fxs[::1]):
            px, py = rx + int(xx), ry + int(yy)
            if 0 <= px < W and 0 <= py < H:
                ov_w.putpixel((px, py), color)
                ov_t.putpixel((px, py), color)

    # draw names on white composite
    draw = ImageDraw.Draw(ov_w)
    for s in shapes:
        draw.text((s["cx"] - 18, s["cy"] - 6), s["name"], fill=(30, 40, 60, 230))

    Image.alpha_composite(white, ov_w).save(CHECK / "assembled_white.png")
    Image.alpha_composite(qa, ov_t).save(CHECK / "assembled_on_template.png")
    print("QA ->", CHECK / "assembled_white.png")
    print("QA ->", CHECK / "assembled_on_template.png")

    payload = {"width": W, "height": H, "shapes": shapes}
    (CHECK / "layout.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    OUT_JS.write_text("export default " + json.dumps(payload, ensure_ascii=False) + "\n", encoding="utf-8")
    print("wrote", OUT_JS, "n=", len(shapes))


if __name__ == "__main__":
    main()
