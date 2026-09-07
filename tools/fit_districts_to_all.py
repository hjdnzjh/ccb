# -*- coding: utf-8 -*-
"""
Fit district SVGs into all.svg framework (overall Hangzhou outline).
PNG used only as optional QA; final export is SVG-only.
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
PUBLIC = ROOT / "web-admin" / "public" / "map"
CHECK = ROOT / "tools" / "map_align_out"
CHECK.mkdir(parents=True, exist_ok=True)
PUBLIC.mkdir(parents=True, exist_ok=True)

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

# Hints as ratios of all.svg canvas (from Hangzhou geography on the outline)
HINTS = {
    "临安区": (0.38, 0.28),
    "余杭区": (0.62, 0.22),
    "临平区": (0.74, 0.14),
    "钱塘区": (0.90, 0.32),
    "拱墅区": (0.66, 0.34),
    "西湖区": (0.58, 0.40),
    "上城区": (0.68, 0.42),
    "滨江区": (0.64, 0.50),
    "萧山区": (0.80, 0.55),
    "富阳区": (0.55, 0.55),
    "建德市": (0.48, 0.82),
    "淳安县": (0.22, 0.62),
    "桐庐县": (0.40, 0.62),
}

COLORS = {
    "临安区": (100, 149, 237),
    "余杭区": (100, 149, 237),
    "临平区": (100, 149, 237),
    "钱塘区": (100, 149, 237),
    "拱墅区": (100, 149, 237),
    "西湖区": (100, 149, 237),
    "上城区": (100, 149, 237),
    "滨江区": (100, 149, 237),
    "萧山区": (100, 149, 237),
    "富阳区": (100, 149, 237),
    "淳安县": (237, 242, 255),
    "桐庐县": (237, 242, 255),
    "建德市": (0, 197, 185),
}


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


def apply_tf(pts, x, y, scale, angle, nw, nh):
    p = pts * scale
    rcx, rcy = nw * scale / 2, nh * scale / 2
    rad = math.radians(angle)
    c, s = math.cos(rad), math.sin(rad)
    out = np.empty_like(p)
    out[:, 0] = c * (p[:, 0] - rcx) - s * (p[:, 1] - rcy) + rcx + x
    out[:, 1] = s * (p[:, 0] - rcx) + c * (p[:, 1] - rcy) + rcy + y
    return out


def raster_path(d, scale=1.0, angle=0.0, pad=2):
    pts = path_pts(d)
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
    er = np.asarray(img.filter(ImageFilter.MinFilter(3)), dtype=np.uint8)
    edge = ((np.asarray(img) > 0) & (er == 0)).astype(np.float32)
    edge = np.asarray(
        Image.fromarray((edge * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(3)),
        dtype=np.float32,
    ) / 255.0
    return fill, edge, (minx - pad, miny - pad)


def downsample(a, f):
    if f <= 1:
        return a
    h, w = a.shape
    nh, nw = h // f, w // f
    a = a[: nh * f, : nw * f]
    return a.reshape(nh, f, nw, f).mean((1, 3))


def match_in_frame(frame, frame_edge, fill, edge, hint, search=80):
    H, W = frame.shape
    ph, pw = fill.shape
    if ph >= H or pw >= W:
        return None
    f = 3 if max(ph, pw) > 70 else 2
    fr, fe = downsample(frame, f), downsample(frame_edge, f)
    fs, es = downsample(fill, f), downsample(edge, f)
    psh, psw = fs.shape
    hx = hint[0] / f - psw / 2
    hy = hint[1] / f - psh / 2
    win = search / f
    x0 = int(max(0, hx - win))
    y0 = int(max(0, hy - win))
    x1 = int(min(fr.shape[1] - psw, hx + win))
    y1 = int(min(fr.shape[0] - psh, hy + win))
    if x1 < x0 or y1 < y0:
        return None

    best = None
    step = max(1, min(psw, psh) // 14)
    esum = es.sum() + 1e-6
    fsum = fs.sum() + 1e-6
    for y in range(y0, y1 + 1, step):
        for x in range(x0, x1 + 1, step):
            cp = fr[y : y + psh, x : x + psw]
            ep = fe[y : y + psh, x : x + psw]
            cover = (cp * fs).sum() / fsum
            spill = ((1 - cp) * fs).sum() / fsum
            ehit = (ep * es).sum() / esum
            score = 1.2 * cover + 1.4 * ehit - 1.5 * spill
            if best is None or score > best[0]:
                best = (score, x, y)

    if not best:
        return None
    _, sx, sy = best
    bx, by = int(sx * f), int(sy * f)
    margin = f * step + 6
    best2 = None
    esum = edge.sum() + 1e-6
    fsum = fill.sum() + 1e-6
    for y in range(max(0, by - margin), min(H - ph, by + margin) + 1, 2):
        for x in range(max(0, bx - margin), min(W - pw, bx + margin) + 1, 2):
            cp = frame[y : y + ph, x : x + pw]
            ep = frame_edge[y : y + ph, x : x + pw]
            cover = (cp * fill).sum() / fsum
            spill = ((1 - cp) * fill).sum() / fsum
            ehit = (ep * edge).sum() / esum
            score = 1.2 * cover + 1.4 * ehit - 1.5 * spill
            if best2 is None or score > best2[0]:
                best2 = (score, x, y)
    return best2


def main():
    # copy all assets
    for f in MAP.glob("*.svg"):
        (PUBLIC / f.name).write_bytes(f.read_bytes())

    aw, ah, ad = parse_svg(MAP / "all.svg")
    # Work at 2x for cleaner matching then store in native all.svg coords
    SCALE = 2.0
    W, H = int(aw * SCALE), int(ah * SCALE)
    print("frame", aw, ah, "work", W, H)

    # raster frame
    fill_a, edge_a, off_a = raster_path(ad, scale=SCALE, angle=0)
    # place frame at origin of its own bbox -> canvas
    # Better: draw all path into full canvas with translate so path(0,0) at 0,0 scaled
    canvas = Image.new("L", (W, H), 0)
    pts = path_pts(ad) * SCALE
    ImageDraw.Draw(canvas).polygon([(float(x), float(y)) for x, y in pts], fill=255)
    frame = np.asarray(canvas, dtype=np.float32) / 255.0
    er = np.asarray(canvas.filter(ImageFilter.MinFilter(3)), dtype=np.uint8)
    frame_edge = ((np.asarray(canvas) > 0) & (er == 0)).astype(np.float32)
    frame_edge = np.asarray(
        Image.fromarray((frame_edge * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(3)),
        dtype=np.float32,
    ) / 255.0

    shapes = []
    white = Image.new("RGBA", (W, H), (255, 255, 255, 255))
    draw = ImageDraw.Draw(white)
    # draw frame outline
    draw.polygon([(float(x), float(y)) for x, y in pts], outline=(30, 40, 60, 255))

    for fname, name in FILES:
        nw, nh, d = parse_svg(MAP / fname)
        hint = (HINTS[name][0] * W, HINTS[name][1] * H)

        # District SVGs likely same export scale as all.svg — prefer near 1.0 * SCALE
        if max(nw, nh) > 300:
            scales = [0.85 * SCALE, 0.95 * SCALE, 1.0 * SCALE, 1.08 * SCALE, 1.15 * SCALE]
            angles = [0, -2, 2, -4, 4]
            search = 70
        elif max(nw, nh) < 90:
            scales = [0.95 * SCALE, 1.1 * SCALE, 1.25 * SCALE, 1.4 * SCALE, 1.6 * SCALE]
            angles = [0, -3, 3, -6, 6]
            search = 50
        else:
            scales = [0.9 * SCALE, 1.0 * SCALE, 1.1 * SCALE, 1.2 * SCALE, 1.3 * SCALE]
            angles = [0, -3, 3, -5, 5]
            search = 60

        best = None
        for ang in angles:
            for sc in scales:
                # sc here is absolute raster scale; Map uses scale relative to native
                fill, edge, local_off = raster_path(d, scale=sc, angle=ang)
                if fill.shape[0] >= H - 2 or fill.shape[1] >= W - 2:
                    continue
                m = match_in_frame(frame, frame_edge, fill, edge, hint, search=search)
                if not m:
                    continue
                score, rx, ry = m
                if best is None or score > best[0]:
                    best = (score, rx, ry, sc, ang, fill, local_off)

        if best is None:
            print("FAIL", name)
            continue

        score, rx, ry, sc, ang, fill, local_off = best
        # origin in work coords
        ox = rx - local_off[0]
        oy = ry - local_off[1]
        # convert to all.svg native coords
        x = ox / SCALE
        y = oy / SCALE
        scale_native = sc / SCALE
        fys, fxs = np.where(fill > 0.5)
        cx = (rx + fxs.mean()) / SCALE
        cy = (ry + fys.mean()) / SCALE
        print(f"{name:8} score={score:.3f} ({x:.1f},{y:.1f}) sc={scale_native:.2f} rot={ang}")

        shapes.append(
            {
                "name": name,
                "names": [name],
                "file": fname,
                "x": round(float(x), 2),
                "y": round(float(y), 2),
                "w": round(float(nw * scale_native), 2),
                "h": round(float(nh * scale_native), 2),
                "scale": round(float(scale_native), 4),
                "rotate": ang,
                "cx": round(float(cx), 2),
                "cy": round(float(cy), 2),
                "d": d,
                "nativeW": nw,
                "nativeH": nh,
                "shared": False,
            }
        )

        rgb = COLORS[name]
        for yy, xx in zip(fys[::1], fxs[::1]):
            px, py = int(rx + xx), int(ry + yy)
            if 0 <= px < W and 0 <= py < H:
                white.putpixel((px, py), rgb + (230,))

    # labels
    d2 = ImageDraw.Draw(white)
    for s in shapes:
        d2.text((s["cx"] * SCALE - 16, s["cy"] * SCALE - 5), s["name"], fill=(25, 35, 55, 255))

    white.save(CHECK / "assembled_on_all.png")
    # also save scaled-down preview at native size
    white.resize((int(aw), int(ah)), Image.Resampling.LANCZOS).save(CHECK / "assembled_white.png")

    payload = {
        "width": aw,
        "height": ah,
        "frame": {"file": "all.svg", "d": ad, "nativeW": aw, "nativeH": ah},
        "shapes": shapes,
    }
    OUT_JS.write_text("export default " + json.dumps(payload, ensure_ascii=False) + "\n", encoding="utf-8")
    (CHECK / "layout.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print("wrote", OUT_JS, "n=", len(shapes))


if __name__ == "__main__":
    main()
