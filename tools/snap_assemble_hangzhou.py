# -*- coding: utf-8 -*-
"""
Assemble Hangzhou district SVGs by pairwise edge snapping.
PNG template used only for final silhouette QA (not exported to UI).
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

W, H = 1078, 750

FILES = {
    "临安区": "linan.svg",
    "余杭区": "yuhang.svg",
    "临平区": "linping.svg",
    "钱塘区": "qiantang.svg",
    "拱墅区": "gongshu.svg",
    "西湖区": "xihu.svg",
    "上城区": "shangche.svg",
    "滨江区": "binjiang.svg",
    "萧山区": "xiaoshan.svg",
    "富阳区": "fuyang.svg",
    "建德市": "jiandeshi.svg",
    "淳安县": "chunan.svg",
    "桐庐县": "tonglu.svg",
}

# Assembly order: each attaches to an already-placed neighbor
ATTACH = [
    # (name, neighbor, hint_offset_from_neighbor_center, scale_guess)
    ("临安区", None, (200, 160), 1.0),
    ("余杭区", "临安区", (280, -20), 1.1),
    ("临平区", "余杭区", (180, -40), 1.2),
    ("西湖区", "余杭区", (-40, 140), 1.15),
    ("拱墅区", "余杭区", (100, 100), 1.3),
    ("上城区", "拱墅区", (50, 40), 1.2),
    ("滨江区", "上城区", (-20, 80), 1.3),
    ("富阳区", "临安区", (120, 220), 1.0),
    ("萧山区", "上城区", (160, 120), 1.05),
    ("钱塘区", "临平区", (160, 120), 1.0),
    ("桐庐县", "富阳区", (-120, 140), 1.25),
    ("淳安县", "临安区", (-80, 280), 1.45),
    ("建德市", "桐庐县", (40, 160), 0.95),
]

COLORS = {
    "临安区": (100, 160, 230),
    "余杭区": (100, 160, 230),
    "临平区": (100, 160, 230),
    "钱塘区": (100, 160, 230),
    "拱墅区": (100, 160, 230),
    "西湖区": (100, 160, 230),
    "上城区": (100, 160, 230),
    "滨江区": (100, 160, 230),
    "萧山区": (100, 160, 230),
    "富阳区": (100, 160, 230),
    "淳安县": (235, 240, 250),
    "桐庐县": (235, 240, 250),
    "建德市": (0, 195, 185),
}


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


def raster_world(pts, pad=2):
    minx, miny = pts.min(0)
    maxx, maxy = pts.max(0)
    ow = max(2, int(math.ceil(maxx - minx)) + pad * 2)
    oh = max(2, int(math.ceil(maxy - miny)) + pad * 2)
    shifted = [(p[0] - minx + pad, p[1] - miny + pad) for p in pts]
    img = Image.new("L", (ow, oh), 0)
    ImageDraw.Draw(img).polygon(shifted, fill=255)
    fill = np.asarray(img, dtype=np.uint8)
    er = np.asarray(img.filter(ImageFilter.MinFilter(3)), dtype=np.uint8)
    edge = ((fill > 0) & (er == 0)).astype(np.uint8) * 255
    edge = np.asarray(Image.fromarray(edge).filter(ImageFilter.MaxFilter(3)))
    return fill, edge, (minx - pad, miny - pad)


def edge_score(canvas_edge, piece_edge, ox, oy):
    """How well piece edge sits on existing canvas edge (and not deep inside fills)."""
    H, W = canvas_edge.shape
    ph, pw = piece_edge.shape
    if ox < 0 or oy < 0 or ox + pw > W or oy + ph > H:
        return -1e9
    patch = canvas_edge[oy : oy + ph, ox : ox + pw]
    pe = piece_edge > 0
    hit = (patch[pe] > 0).sum()
    total = pe.sum() + 1e-6
    return hit / total


def place_first(name, meta, hint, scale):
    nw, nh, pts = meta
    # center roughly at hint
    x = hint[0] - nw * scale / 2
    y = hint[1] - nh * scale / 2
    return {"name": name, "x": x, "y": y, "scale": scale, "rotate": 0, "nw": nw, "nh": nh, "pts": pts}


def snap_to_neighbor(name, meta, placed, neighbor, hint_off, scale0):
    nw, nh, pts = meta
    nb = placed[neighbor]
    nb_world = apply_tf(nb["pts"], nb["x"], nb["y"], nb["scale"], nb["rotate"], nb["nw"], nb["nh"])
    # build neighbor edge on local canvas around search
    all_pts = [nb_world]
    for p in placed.values():
        all_pts.append(apply_tf(p["pts"], p["x"], p["y"], p["scale"], p["rotate"], p["nw"], p["nh"]))
    # canvas for existing puzzle
    canvas = Image.new("L", (W, H), 0)
    draw = ImageDraw.Draw(canvas)
    for wp in all_pts:
        draw.polygon([(float(a), float(b)) for a, b in wp], fill=255)
    er = np.asarray(canvas.filter(ImageFilter.MinFilter(3)), dtype=np.uint8)
    fill_c = np.asarray(canvas, dtype=np.uint8)
    edge_c = ((fill_c > 0) & (er == 0)).astype(np.uint8) * 255
    edge_c = np.asarray(Image.fromarray(edge_c).filter(ImageFilter.MaxFilter(5)))

    ncx, ncy = nb_world[:, 0].mean(), nb_world[:, 1].mean()
    base_x = ncx + hint_off[0] - nw * scale0 / 2
    base_y = ncy + hint_off[1] - nh * scale0 / 2

    best = None
    scales = [scale0 * s for s in (0.9, 0.95, 1.0, 1.05, 1.1, 1.15)]
    angles = [0, -3, 3, -6, 6, -9, 9]
    for ang in angles:
        for sc in scales:
            # search translation around base
            for dy in range(-60, 61, 4):
                for dx in range(-60, 61, 4):
                    x = base_x + dx
                    y = base_y + dy
                    world = apply_tf(pts, x, y, sc, ang, nw, nh)
                    # reject if mostly outside canvas
                    if world[:, 0].min() < -20 or world[:, 1].min() < -20:
                        continue
                    if world[:, 0].max() > W + 20 or world[:, 1].max() > H + 20:
                        continue
                    fill, edge, origin = raster_world(world)
                    ox, oy = int(round(origin[0])), int(round(origin[1]))
                    # edge alignment with existing
                    es = edge_score(edge_c, edge, ox, oy)
                    # prefer slight overlap of edges, discourage deep overlap of fills
                    ph, pw = fill.shape
                    if ox < 0 or oy < 0 or ox + pw > W or oy + ph > H:
                        continue
                    exist = fill_c[oy : oy + ph, ox : ox + pw] > 0
                    pie = fill > 0
                    overlap = (exist & pie).sum() / (pie.sum() + 1e-6)
                    # want edge contact, modest overlap (~5-20%)
                    score = es * 2.5 - abs(overlap - 0.08) * 3.0
                    if overlap > 0.45:
                        score -= 2.0
                    if overlap < 0.02:
                        score -= 1.0
                    if best is None or score > best[0]:
                        best = (score, x, y, sc, ang, overlap, es)

    if best is None:
        return place_first(name, meta, (ncx + hint_off[0], ncy + hint_off[1]), scale0)
    score, x, y, sc, ang, ov, es = best
    print(f"  snap {name} <- {neighbor}: score={score:.3f} edge={es:.3f} ov={ov:.3f} sc={sc:.2f} rot={ang}")
    return {"name": name, "x": x, "y": y, "scale": sc, "rotate": ang, "nw": nw, "nh": nh, "pts": pts}


def main():
    meta = {}
    for name, fname in FILES.items():
        nw, nh, d = parse_svg(MAP / fname)
        meta[name] = (nw, nh, path_pts(d), d)

    placed = {}
    for name, neighbor, hint, sc in ATTACH:
        m = meta[name]
        mm = (m[0], m[1], m[2])
        if neighbor is None:
            placed[name] = place_first(name, mm, hint, sc)
            print(f"anchor {name} at hint {hint}")
        else:
            placed[name] = snap_to_neighbor(name, mm, placed, neighbor, hint, sc)

    # export + QA
    shapes = []
    white = Image.new("RGBA", (W, H), (255, 255, 255, 255))
    draw = ImageDraw.Draw(white)
    ref = Image.open(REF).convert("RGBA")
    qa = Image.blend(ref, Image.new("RGBA", (W, H), (255, 255, 255, 255)), 0.5)
    qd = ImageDraw.Draw(qa)

    for name, p in placed.items():
        world = apply_tf(p["pts"], p["x"], p["y"], p["scale"], p["rotate"], p["nw"], p["nh"])
        poly = [(float(a), float(b)) for a, b in world]
        rgb = COLORS[name]
        draw.polygon(poly, fill=rgb + (230,), outline=(255, 255, 255, 255))
        qd.polygon(poly, fill=rgb + (150,), outline=(30, 100, 200, 220))
        cx, cy = float(world[:, 0].mean()), float(world[:, 1].mean())
        draw.text((cx - 20, cy - 6), name, fill=(30, 40, 60, 255))
        d = meta[name][3]
        shapes.append(
            {
                "name": name,
                "names": [name],
                "file": FILES[name],
                "x": round(float(p["x"]), 2),
                "y": round(float(p["y"]), 2),
                "w": round(float(p["nw"] * p["scale"]), 2),
                "h": round(float(p["nh"] * p["scale"]), 2),
                "scale": round(float(p["scale"]), 4),
                "rotate": p["rotate"],
                "cx": round(cx, 2),
                "cy": round(cy, 2),
                "d": d,
                "nativeW": p["nw"],
                "nativeH": p["nh"],
                "shared": False,
            }
        )

    white.save(CHECK / "assembled_white.png")
    qa.save(CHECK / "assembled_on_template.png")
    payload = {"width": W, "height": H, "shapes": shapes}
    (CHECK / "layout.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    OUT_JS.write_text("export default " + json.dumps(payload, ensure_ascii=False) + "\n", encoding="utf-8")
    print("wrote", OUT_JS, "QA", CHECK / "assembled_white.png")


if __name__ == "__main__":
    main()
