# -*- coding: utf-8 -*-
"""Centroid-at-pin layout + clear QA (SVG-only vs PNG underlay difference)."""
from __future__ import annotations

import json
import math
import re
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageChops

ROOT = Path(r"D:\ccb")
MAP = ROOT / "map"
CHECK = ROOT / "tools" / "map_align_out"
CHECK.mkdir(parents=True, exist_ok=True)
OUT_JS = ROOT / "web-admin" / "src" / "data" / "hangzhouShapes.js"
PUBLIC = ROOT / "web-admin" / "public" / "map"

W, H = 1078, 750

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

# Visual size on PNG (approx max dimension in px) — tuned by eye from 杭州.png
TARGET_MAX = {
    "临安区": 420,
    "余杭区": 210,
    "临平区": 110,
    "钱塘区": 200,
    "拱墅区": 75,
    "西湖区": 130,
    "上城区": 100,
    "滨江区": 60,
    "萧山区": 250,
    "富阳区": 290,
    "建德市": 360,
    "淳安县": 290,
    "桐庐县": 220,
}

# Fine nudge after centroid placement (dx, dy, dscale, drot)
NUDGE = {
    "临安区": (0, 0, 0, 0),
    "余杭区": (0, 0, 0, 0),
    "临平区": (0, 0, 0, 0),
    "钱塘区": (0, -20, 0, 0),
    "拱墅区": (0, 0, 0, 0),
    "西湖区": (0, 0, 0, 0),
    "上城区": (0, 0, 0, 0),
    "滨江区": (0, 0, 0, 0),
    "萧山区": (10, 0, 0, 0),
    "富阳区": (0, 0, 0, 0),
    "建德市": (0, 0, 0, 0),
    "淳安县": (0, 0, 0, 0),
    "桐庐县": (0, 0, 0, 0),
}

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


def main():
    for f in MAP.glob("*.svg"):
        (PUBLIC / f.name).write_bytes(f.read_bytes())

    png = Image.open(sorted(MAP.glob("*.png"), key=lambda p: p.stat().st_size, reverse=True)[0]).convert("RGBA")

    # all.svg frame stretched
    aw_t = (MAP / "all.svg").read_text(encoding="utf-8")
    aw = float(re.search(r'width="([\d.]+)', aw_t).group(1))
    ah = float(re.search(r'height="([\d.]+)', aw_t).group(1))
    ad = re.search(r'\bd="([^"]+)"', aw_t).group(1)
    fp = path_pts(ad)
    rgb = np.asarray(png.convert("RGB"))
    land = ~((rgb[:, :, 0] < 40) & (rgb[:, :, 1] < 40) & (rgb[:, :, 2] < 40))
    ys, xs = np.where(land)
    px0, py0, px1, py1 = int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())
    ax0, ay0 = fp[:, 0].min(), fp[:, 1].min()
    awb, ahb = fp[:, 0].max() - ax0, fp[:, 1].max() - ay0
    all_pts = np.empty_like(fp)
    all_pts[:, 0] = (fp[:, 0] - ax0) / awb * (px1 - px0) + px0 - 20
    all_pts[:, 1] = (fp[:, 1] - ay0) / ahb * (py1 - py0) + py0 - 10
    all_frame = " ".join(
        [f"M{all_pts[0,0]:.2f} {all_pts[0,1]:.2f}"]
        + [f"L{x:.2f} {y:.2f}" for x, y in all_pts[1:]]
        + ["Z"]
    )

    white = Image.new("RGBA", (W, H), (255, 255, 255, 255))
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    shapes = []
    layout = {}

    for name, fname in FILES.items():
        nw, nh, d = parse_svg(MAP / fname)
        pts = path_pts(d)
        # scale so max native dim -> TARGET_MAX
        sc = TARGET_MAX[name] / max(nw, nh)
        dx, dy, dsc, drot = NUDGE[name]
        sc *= 1 + dsc
        rot = drot
        # place so path centroid lands on pin (account for transform)
        # solve: mean(apply_tf(pts, x, y, sc, rot, nw, nh)) == pin + nudge
        # apply_tf = R(scale(pts)) + (x,y) with rotation about scaled native center
        pin = PINS[name]
        target = (pin[0] + dx, pin[1] + dy)
        # compute centroid at x=y=0
        tp0 = apply_tf(pts, 0, 0, sc, rot, nw, nh)
        c0 = tp0.mean(axis=0)
        x = target[0] - c0[0]
        y = target[1] - c0[1]
        layout[name] = (round(x, 2), round(y, 2), round(sc, 4), rot)

        tp = apply_tf(pts, x, y, sc, rot, nw, nh)
        rgb_c = COLORS[name]
        ld.polygon([(float(a), float(b)) for a, b in tp], fill=rgb_c + (255,), outline=(255, 255, 255, 255))
        cx, cy = float(tp[:, 0].mean()), float(tp[:, 1].mean())
        print(f"{name:8} xy=({x:.1f},{y:.1f}) sc={sc:.3f} c=({cx:.0f},{cy:.0f}) pin={pin}")
        shapes.append(
            {
                "name": name,
                "names": [name],
                "file": fname,
                "x": round(x, 2),
                "y": round(y, 2),
                "w": round(nw * sc, 2),
                "h": round(nh * sc, 2),
                "scale": round(sc, 4),
                "rotate": rot,
                "cx": round(cx, 2),
                "cy": round(cy, 2),
                "d": d,
                "nativeW": nw,
                "nativeH": nh,
                "shared": False,
            }
        )

    white = Image.alpha_composite(white, layer)
    ImageDraw.Draw(white).polygon([(float(a), float(b)) for a, b in all_pts], outline=(20, 30, 50, 180))
    white.save(CHECK / "assembled_white.png")

    # Clear QA: yellow SVG edges on PNG
    edge_only = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ed = ImageDraw.Draw(edge_only)
    for s in shapes:
        pts = path_pts(s["d"])
        tp = apply_tf(pts, s["x"], s["y"], s["scale"], s["rotate"], s["nativeW"], s["nativeH"])
        ed.polygon([(float(a), float(b)) for a, b in tp], outline=(255, 220, 0, 255))
    qa = Image.alpha_composite(png, edge_only)
    ImageDraw.Draw(qa).polygon([(float(a), float(b)) for a, b in all_pts], outline=(255, 0, 0, 200))
    qa.save(CHECK / "edges_on_png.png")

    # Side-by-side
    side = Image.new("RGB", (W * 2 + 20, H), (255, 255, 255))
    side.paste(png.convert("RGB"), (0, 0))
    side.paste(white.convert("RGB"), (W + 20, 0))
    side.save(CHECK / "side_by_side.png")

    payload = {
        "width": W,
        "height": H,
        "frame": {"file": "all.svg", "d": all_frame, "nativeW": aw, "nativeH": ah},
        "shapes": shapes,
    }
    OUT_JS.write_text("export default " + json.dumps(payload, ensure_ascii=False) + "\n", encoding="utf-8")
    (CHECK / "layout_positions.json").write_text(json.dumps(layout, ensure_ascii=False, indent=2), encoding="utf-8")
    print("wrote", OUT_JS)


if __name__ == "__main__":
    main()
