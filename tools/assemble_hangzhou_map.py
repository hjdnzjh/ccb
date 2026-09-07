# -*- coding: utf-8 -*-
"""
Assemble district SVGs onto 杭州.png coordinates (template, not shown in UI).
Stretch all.svg outline onto the same land bbox as the vector frame.
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
CHECK = ROOT / "tools" / "map_align_out"
CHECK.mkdir(parents=True, exist_ok=True)
OUT_JS = ROOT / "web-admin" / "src" / "data" / "hangzhouShapes.js"
PUBLIC = ROOT / "web-admin" / "public" / "map"

FILES = [
    ("linan.svg", "临安区", "blue"),
    ("yuhang.svg", "余杭区", "blue"),
    ("linping.svg", "临平区", "blue"),
    ("qiantang.svg", "钱塘区", "blue"),
    ("gongshu.svg", "拱墅区", "blue"),
    ("xihu.svg", "西湖区", "blue"),
    ("shangche.svg", "上城区", "blue"),
    ("binjiang.svg", "滨江区", "blue"),
    ("xiaoshan.svg", "萧山区", "blue"),
    ("fuyang.svg", "富阳区", "blue"),
    ("jiandeshi.svg", "建德市", "teal"),
    ("chunan.svg", "淳安县", "pale"),
    ("tonglu.svg", "桐庐县", "pale"),
]

PINS = {
    "临安区": (340, 195),
    "余杭区": (620, 155),
    "临平区": (760, 105),
    "钱塘区": (925, 240),
    "拱墅区": (655, 245),
    "西湖区": (595, 305),
    "上城区": (680, 305),
    "滨江区": (655, 360),
    "萧山区": (820, 430),
    "富阳区": (555, 430),
    "建德市": (515, 625),
    "淳安县": (230, 530),
    "桐庐县": (430, 490),
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


def pts_to_path(pts: np.ndarray) -> str:
    if len(pts) == 0:
        return ""
    parts = [f"M{pts[0,0]:.2f} {pts[0,1]:.2f}"]
    for x, y in pts[1:]:
        parts.append(f"L{x:.2f} {y:.2f}")
    parts.append("Z")
    return " ".join(parts)


def apply_tf(pts, x, y, scale, angle, nw, nh):
    p = pts * scale
    rcx, rcy = nw * scale / 2, nh * scale / 2
    rad = math.radians(angle)
    c, s = math.cos(rad), math.sin(rad)
    out = np.empty_like(p)
    out[:, 0] = c * (p[:, 0] - rcx) - s * (p[:, 1] - rcy) + rcx + x
    out[:, 1] = s * (p[:, 0] - rcx) + c * (p[:, 1] - rcy) + rcy + y
    return out


def raster_shape(d, scale=1.0, angle=0.0, pad=2):
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


def color_masks(rgb: np.ndarray):
    r, g, b = rgb[:, :, 0], rgb[:, :, 1], rgb[:, :, 2]
    black = (r < 40) & (g < 40) & (b < 40)
    # exclude near-black text? keep labels out of pale by requiring large connected regions later
    teal = (~black) & (g > 140) & (b > 120) & (r < 120) & (g > r + 30)
    # pale districts: very light but not pure white text-only — use high brightness + low saturation
    pale = (~black) & (~teal) & (r > 210) & (g > 210) & (b > 210)
    blue = (~black) & (~teal) & (~pale) & (b >= r - 10) & (b > 70)
    other = (~black) & (~teal) & (~pale) & (~blue)
    blue = blue | other
    land = (~black).astype(np.float32)
    return {
        "blue": blue.astype(np.float32),
        "teal": teal.astype(np.float32),
        "pale": pale.astype(np.float32),
        "land": land,
    }


def downsample(a, f):
    if f <= 1:
        return a
    h, w = a.shape
    nh, nw = h // f, w // f
    a = a[: nh * f, : nw * f]
    return a.reshape(nh, f, nw, f).mean((1, 3))


def match_region(target, target_edge, fill, edge, hint, search=90):
    H, W = target.shape
    ph, pw = fill.shape
    if ph >= H or pw >= W:
        return None
    f = 4 if max(ph, pw) > 120 else 3
    tr, te = downsample(target, f), downsample(target_edge, f)
    fs, es = downsample(fill, f), downsample(edge, f)
    psh, psw = fs.shape
    hx = hint[0] / f - psw / 2
    hy = hint[1] / f - psh / 2
    win = search / f
    x0 = int(max(0, hx - win))
    y0 = int(max(0, hy - win))
    x1 = int(min(tr.shape[1] - psw, hx + win))
    y1 = int(min(tr.shape[0] - psh, hy + win))
    if x1 < x0 or y1 < y0:
        return None

    best = None
    step = max(1, min(psw, psh) // 12)
    esum = es.sum() + 1e-6
    fsum = fs.sum() + 1e-6
    for y in range(y0, y1 + 1, step):
        for x in range(x0, x1 + 1, step):
            cp = tr[y : y + psh, x : x + psw]
            ep = te[y : y + psh, x : x + psw]
            cover = (cp * fs).sum() / fsum
            spill = ((1 - cp) * fs).sum() / fsum
            ehit = (ep * es).sum() / esum
            score = 1.3 * cover + 1.5 * ehit - 1.8 * spill
            if best is None or score > best[0]:
                best = (score, x, y)

    if not best:
        return None
    _, sx, sy = best
    bx, by = int(sx * f), int(sy * f)
    margin = f * step + 8
    best2 = None
    esum = edge.sum() + 1e-6
    fsum = fill.sum() + 1e-6
    for y in range(max(0, by - margin), min(H - ph, by + margin) + 1, 1):
        for x in range(max(0, bx - margin), min(W - pw, bx + margin) + 1, 1):
            cp = target[y : y + ph, x : x + pw]
            ep = target_edge[y : y + ph, x : x + pw]
            cover = (cp * fill).sum() / fsum
            spill = ((1 - cp) * fill).sum() / fsum
            ehit = (ep * edge).sum() / esum
            score = 1.3 * cover + 1.5 * ehit - 1.8 * spill
            if best2 is None or score > best2[0]:
                best2 = (score, x, y)
    return best2


def edge_of(m):
    img = Image.fromarray((m * 255).astype(np.uint8))
    er = np.asarray(img.filter(ImageFilter.MinFilter(3)), dtype=np.uint8)
    e = ((np.asarray(img) > 0) & (er == 0)).astype(np.float32)
    return np.asarray(
        Image.fromarray((e * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(3)),
        dtype=np.float32,
    ) / 255.0


def main():
    for f in MAP.glob("*.svg"):
        (PUBLIC / f.name).write_bytes(f.read_bytes())

    png = Image.open(sorted(MAP.glob("*.png"), key=lambda p: p.stat().st_size, reverse=True)[0]).convert("RGB")
    W, H = png.size
    rgb = np.asarray(png)
    masks = color_masks(rgb)
    edges = {k: edge_of(v) for k, v in masks.items()}

    ys, xs = np.where(masks["land"] > 0.5)
    px0, py0, px1, py1 = int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())
    print("land", px0, py0, px1, py1)

    # Stretch all.svg onto land bbox → frame path in PNG coords
    aw, ah, ad = parse_svg(MAP / "all.svg")
    fp = path_pts(ad)
    ax0, ay0 = fp[:, 0].min(), fp[:, 1].min()
    awb, ahb = fp[:, 0].max() - ax0, fp[:, 1].max() - ay0
    frame_pts = np.empty_like(fp)
    frame_pts[:, 0] = (fp[:, 0] - ax0) / awb * (px1 - px0) + px0
    frame_pts[:, 1] = (fp[:, 1] - ay0) / ahb * (py1 - py0) + py0
    frame_d = pts_to_path(frame_pts)

    occupied = np.zeros((H, W), dtype=np.float32)
    results = []

    for fname, name, kind in FILES:
        nw, nh, d = parse_svg(MAP / fname)
        hint = PINS[name]
        target = masks[kind] * (1.0 - occupied * 0.9)
        target_edge = edges[kind]

        if max(nw, nh) > 300:
            scales = [0.88, 0.95, 1.0, 1.06, 1.12]
            angles = [0, -2, 2, -4, 4]
            search = 95
        elif max(nw, nh) < 90:
            scales = [0.95, 1.05, 1.15, 1.3, 1.45]
            angles = [0, -3, 3, -5, 5]
            search = 50
        else:
            scales = [0.9, 0.98, 1.05, 1.12, 1.22]
            angles = [0, -3, 3, -5, 5]
            search = 75

        best = None
        for ang in angles:
            for sc in scales:
                fill, edge, local_off = raster_shape(d, scale=sc, angle=ang)
                if fill.shape[0] >= H - 2 or fill.shape[1] >= W - 2:
                    continue
                m = match_region(target, target_edge, fill, edge, hint, search=search)
                if not m:
                    continue
                score, rx, ry = m
                if best is None or score > best[0]:
                    best = (score, rx, ry, sc, ang, fill, local_off)

        if best is None or best[0] < 0.4:
            for ang in [0, -2, 2]:
                for sc in scales:
                    fill, edge, local_off = raster_shape(d, scale=sc, angle=ang)
                    if fill.shape[0] >= H - 2 or fill.shape[1] >= W - 2:
                        continue
                    m = match_region(masks["land"], edges["land"], fill, edge, hint, search=search + 25)
                    if not m:
                        continue
                    score, rx, ry = m
                    if best is None or score > best[0]:
                        best = (score, rx, ry, sc, ang, fill, local_off)

        if best is None:
            print("FAIL", name)
            continue

        score, rx, ry, sc, ang, fill, local_off = best
        ox = rx - local_off[0]
        oy = ry - local_off[1]
        fys, fxs = np.where(fill > 0.5)
        for yy, xx in zip(fys, fxs):
            px, py = int(rx + xx), int(ry + yy)
            if 0 <= px < W and 0 <= py < H:
                occupied[py, px] = 1.0
        cx = float(rx + fxs.mean())
        cy = float(ry + fys.mean())
        print(f"{name:8} score={score:.3f} ({ox:.1f},{oy:.1f}) sc={sc:.2f} rot={ang}")
        results.append(
            {
                "name": name,
                "file": fname,
                "nw": nw,
                "nh": nh,
                "d": d,
                "x": ox,
                "y": oy,
                "sc": sc,
                "ang": ang,
                "cx": cx,
                "cy": cy,
                "score": score,
            }
        )

    # QA: white bg assembly + overlay on png
    white = Image.new("RGBA", (W, H), (255, 255, 255, 255))
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    shapes = []
    for r in results:
        pts = path_pts(r["d"])
        tp = apply_tf(pts, r["x"], r["y"], r["sc"], r["ang"], r["nw"], r["nh"])
        rgb = COLORS[r["name"]]
        ld.polygon([(float(a), float(b)) for a, b in tp], fill=rgb + (220,), outline=(255, 255, 255, 255))
        shapes.append(
            {
                "name": r["name"],
                "names": [r["name"]],
                "file": r["file"],
                "x": round(float(r["x"]), 2),
                "y": round(float(r["y"]), 2),
                "w": round(float(r["nw"] * r["sc"]), 2),
                "h": round(float(r["nh"] * r["sc"]), 2),
                "scale": round(float(r["sc"]), 4),
                "rotate": r["ang"],
                "cx": round(float(r["cx"]), 2),
                "cy": round(float(r["cy"]), 2),
                "d": r["d"],
                "nativeW": r["nw"],
                "nativeH": r["nh"],
                "shared": False,
                "score": round(float(r["score"]), 3),
            }
        )

    white = Image.alpha_composite(white, layer)
    # draw frame
    ImageDraw.Draw(white).polygon([(float(x), float(y)) for x, y in frame_pts], outline=(30, 40, 60, 200))
    white.save(CHECK / "assembled_white.png")

    over = Image.alpha_composite(png.convert("RGBA"), layer)
    ImageDraw.Draw(over).polygon([(float(x), float(y)) for x, y in frame_pts], outline=(255, 60, 60, 220))
    over.save(CHECK / "matched_on_png.png")
    over.save(CHECK / "assembled_on_all.png")

    payload = {
        "width": W,
        "height": H,
        "frame": {
            "file": "all.svg",
            "d": frame_d,
            "nativeW": aw,
            "nativeH": ah,
            "x": 0,
            "y": 0,
        },
        "shapes": shapes,
    }
    OUT_JS.write_text("export default " + json.dumps(payload, ensure_ascii=False) + "\n", encoding="utf-8")
    (CHECK / "layout_positions.json").write_text(
        json.dumps(
            {s["name"]: {k: s[k] for k in ("x", "y", "w", "h", "scale", "rotate", "cx", "cy", "score")} for s in shapes},
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    print("wrote", OUT_JS, "n=", len(shapes))


if __name__ == "__main__":
    main()
