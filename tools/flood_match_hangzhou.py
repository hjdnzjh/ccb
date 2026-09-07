# -*- coding: utf-8 -*-
"""
Flood-fill each district region from label pins on 杭州.png,
match SVG piece into that region, export hangzhouShapes.js.
all.svg outline is stretched onto land bbox as vector frame (no PNG in UI).
"""
from __future__ import annotations

import json
import math
import re
from collections import deque
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

# Label centers on 杭州.png
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


def land_mask(rgb: np.ndarray) -> np.ndarray:
    r, g, b = rgb[:, :, 0].astype(np.int16), rgb[:, :, 1].astype(np.int16), rgb[:, :, 2].astype(np.int16)
    black = (r < 40) & (g < 40) & (b < 40)
    return (~black).astype(np.uint8)


def similar(c0, c1, tol=38):
    return abs(int(c0[0]) - int(c1[0])) <= tol and abs(int(c0[1]) - int(c1[1])) <= tol and abs(int(c0[2]) - int(c1[2])) <= tol


def flood_region(rgb, land, seed, tol=38, max_pixels=500000):
    H, W = land.shape
    sx, sy = int(seed[0]), int(seed[1])
    # seek nearest land pixel if seed on text/border
    if not land[sy, sx]:
        found = None
        for r in range(1, 40):
            for dy in range(-r, r + 1):
                for dx in range(-r, r + 1):
                    x, y = sx + dx, sy + dy
                    if 0 <= x < W and 0 <= y < H and land[y, x]:
                        found = (x, y)
                        break
                if found:
                    break
            if found:
                break
        if not found:
            return None
        sx, sy = found

    c0 = rgb[sy, sx]
    # skip if seed color is near black text
    if c0[0] < 50 and c0[1] < 50 and c0[2] < 50:
        return None

    visited = np.zeros((H, W), dtype=np.uint8)
    q = deque([(sx, sy)])
    visited[sy, sx] = 1
    count = 0
    while q and count < max_pixels:
        x, y = q.popleft()
        count += 1
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if nx < 0 or ny < 0 or nx >= W or ny >= H:
                continue
            if visited[ny, nx]:
                continue
            if not land[ny, nx]:
                continue
            if not similar(c0, rgb[ny, nx], tol):
                continue
            visited[ny, nx] = 1
            q.append((nx, ny))
    return visited.astype(np.float32)


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


def edge_of(m):
    img = Image.fromarray((m * 255).astype(np.uint8))
    er = np.asarray(img.filter(ImageFilter.MinFilter(3)), dtype=np.uint8)
    e = ((np.asarray(img) > 0) & (er == 0)).astype(np.float32)
    return np.asarray(
        Image.fromarray((e * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(3)),
        dtype=np.float32,
    ) / 255.0


def downsample(a, f):
    if f <= 1:
        return a
    h, w = a.shape
    nh, nw = h // f, w // f
    a = a[: nh * f, : nw * f]
    return a.reshape(nh, f, nw, f).mean((1, 3))


def match_in_mask(mask, mask_edge, fill, edge, hint, search=60):
    H, W = mask.shape
    ph, pw = fill.shape
    if ph >= H or pw >= W:
        return None
    # Prefer placing so piece center near hint and mostly inside mask
    f = 3 if max(ph, pw) > 80 else 2
    mr, me = downsample(mask, f), downsample(mask_edge, f)
    fs, es = downsample(fill, f), downsample(edge, f)
    psh, psw = fs.shape
    hx = hint[0] / f - psw / 2
    hy = hint[1] / f - psh / 2
    win = search / f
    x0 = int(max(0, hx - win))
    y0 = int(max(0, hy - win))
    x1 = int(min(mr.shape[1] - psw, hx + win))
    y1 = int(min(mr.shape[0] - psh, hy + win))
    if x1 < x0 or y1 < y0:
        return None

    best = None
    step = max(1, min(psw, psh) // 10)
    fsum = fs.sum() + 1e-6
    esum = es.sum() + 1e-6
    for y in range(y0, y1 + 1, step):
        for x in range(x0, x1 + 1, step):
            cp = mr[y : y + psh, x : x + psw]
            ep = me[y : y + psh, x : x + psw]
            cover = (cp * fs).sum() / fsum
            spill = ((1 - cp) * fs).sum() / fsum
            ehit = (ep * es).sum() / esum
            score = 1.5 * cover + 1.2 * ehit - 2.0 * spill
            if best is None or score > best[0]:
                best = (score, x, y)
    if not best:
        return None
    _, sx, sy = best
    bx, by = int(sx * f), int(sy * f)
    margin = f * step + 6
    best2 = None
    fsum = fill.sum() + 1e-6
    esum = edge.sum() + 1e-6
    for y in range(max(0, by - margin), min(H - ph, by + margin) + 1, 1):
        for x in range(max(0, bx - margin), min(W - pw, bx + margin) + 1, 1):
            cp = mask[y : y + ph, x : x + pw]
            ep = mask_edge[y : y + ph, x : x + pw]
            cover = (cp * fill).sum() / fsum
            spill = ((1 - cp) * fill).sum() / fsum
            ehit = (ep * edge).sum() / esum
            score = 1.5 * cover + 1.2 * ehit - 2.0 * spill
            if best2 is None or score > best2[0]:
                best2 = (score, x, y)
    return best2


def main():
    for f in MAP.glob("*.svg"):
        (PUBLIC / f.name).write_bytes(f.read_bytes())

    png = Image.open(sorted(MAP.glob("*.png"), key=lambda p: p.stat().st_size, reverse=True)[0]).convert("RGB")
    W, H = png.size
    rgb = np.asarray(png)
    land = land_mask(rgb)

    # Frame from all.svg stretched to land bbox
    ys, xs = np.where(land > 0)
    px0, py0, px1, py1 = int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())
    aw, ah, ad = parse_svg(MAP / "all.svg")
    fp = path_pts(ad)
    ax0, ay0 = fp[:, 0].min(), fp[:, 1].min()
    awb, ahb = fp[:, 0].max() - ax0, fp[:, 1].max() - ay0
    frame_pts = np.empty_like(fp)
    frame_pts[:, 0] = (fp[:, 0] - ax0) / awb * (px1 - px0) + px0
    frame_pts[:, 1] = (fp[:, 1] - ay0) / ahb * (py1 - py0) + py0
    frame_d = pts_to_path(frame_pts)

    # Flood each district
    regions = {}
    for name, pin in PINS.items():
        # try a few tols — pale districts need higher because of labels
        best_mask = None
        for tol in (28, 36, 48, 60):
            m = flood_region(rgb, land, pin, tol=tol)
            if m is None:
                continue
            area = float(m.sum())
            if area < 200:
                continue
            # reject if swallowed half the map
            if area > land.sum() * 0.45 and name not in ("临安区", "淳安县", "萧山区", "建德市", "富阳区"):
                continue
            if best_mask is None or abs(area - (best_mask.sum())):
                # prefer medium-sized coherent region near expected
                if best_mask is None:
                    best_mask = m
                else:
                    # keep first good
                    pass
            if 500 < area < land.sum() * 0.4 or name in ("临安区", "淳安县", "萧山区", "建德市", "富阳区", "余杭区"):
                if name in ("临安区", "淳安县", "萧山区", "建德市", "富阳区", "余杭区"):
                    if area > 2000:
                        best_mask = m
                        break
                else:
                    if 400 < area < 80000:
                        best_mask = m
                        break
        if best_mask is None:
            print("NO REGION", name)
            continue
        regions[name] = best_mask
        Image.fromarray((best_mask * 255).astype(np.uint8)).save(CHECK / f"region_{name}.png")
        print(f"region {name} area={best_mask.sum():.0f}")

    results = []
    for fname, name in FILES:
        if name not in regions:
            print("SKIP", name)
            continue
        nw, nh, d = parse_svg(MAP / fname)
        mask = regions[name]
        mask_edge = edge_of(mask)
        hint = PINS[name]
        # estimate scale from region bbox vs svg
        ys2, xs2 = np.where(mask > 0.5)
        rw = xs2.max() - xs2.min() + 1
        rh = ys2.max() - ys2.min() + 1
        base = min(rw / nw, rh / nh)
        scales = sorted(set([round(base * k, 3) for k in (0.85, 0.92, 1.0, 1.08, 1.15) if 0.4 < base * k < 2.5]))
        if not scales:
            scales = [0.9, 1.0, 1.1]
        angles = [0, -2, 2, -4, 4] if max(nw, nh) > 100 else [0, -3, 3, -6, 6]
        search = max(40, int(min(rw, rh) * 0.35))

        best = None
        for ang in angles:
            for sc in scales:
                fill, edge, local_off = raster_shape(d, scale=sc, angle=ang)
                if fill.shape[0] >= H - 2 or fill.shape[1] >= W - 2:
                    continue
                m = match_in_mask(mask, mask_edge, fill, edge, hint, search=search)
                if not m:
                    continue
                score, rx, ry = m
                if best is None or score > best[0]:
                    best = (score, rx, ry, sc, ang, fill, local_off)

        if best is None:
            print("FAIL match", name)
            continue
        score, rx, ry, sc, ang, fill, local_off = best
        ox = rx - local_off[0]
        oy = ry - local_off[1]
        fys, fxs = np.where(fill > 0.5)
        cx = float(rx + fxs.mean())
        cy = float(ry + fys.mean())
        print(f"{name:8} score={score:.3f} ({ox:.1f},{oy:.1f}) sc={sc:.2f} rot={ang} base={base:.2f}")
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

    white = Image.new("RGBA", (W, H), (255, 255, 255, 255))
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    shapes = []
    for r in results:
        pts = path_pts(r["d"])
        tp = apply_tf(pts, r["x"], r["y"], r["sc"], r["ang"], r["nw"], r["nh"])
        rgb_c = COLORS[r["name"]]
        ld.polygon([(float(a), float(b)) for a, b in tp], fill=rgb_c + (230,), outline=(255, 255, 255, 255))
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
            }
        )

    white = Image.alpha_composite(white, layer)
    ImageDraw.Draw(white).polygon([(float(x), float(y)) for x, y in frame_pts], outline=(30, 40, 60, 200))
    white.save(CHECK / "assembled_white.png")

    over = Image.alpha_composite(png.convert("RGBA"), layer)
    ImageDraw.Draw(over).polygon([(float(x), float(y)) for x, y in frame_pts], outline=(255, 50, 50, 220))
    over.save(CHECK / "matched_on_png.png")

    payload = {
        "width": W,
        "height": H,
        "frame": {"file": "all.svg", "d": frame_d, "nativeW": aw, "nativeH": ah},
        "shapes": shapes,
    }
    OUT_JS.write_text("export default " + json.dumps(payload, ensure_ascii=False) + "\n", encoding="utf-8")
    (CHECK / "layout_positions.json").write_text(
        json.dumps({s["name"]: {k: s[k] for k in ("x", "y", "w", "h", "scale", "rotate", "cx", "cy")} for s in shapes}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print("wrote", OUT_JS, "n=", len(shapes))


if __name__ == "__main__":
    main()
