# -*- coding: utf-8 -*-
"""
Geographic manual layout for Hangzhou SVGs (scale/pos/rot adjustable).
Restores puzzle-like placement; PNG only for QA overlay.
"""
from __future__ import annotations

import json
import math
import re
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(r"D:\ccb")
MAP = ROOT / "map"
OUT_JS = ROOT / "web-admin" / "src" / "data" / "hangzhouShapes.js"
CHECK = ROOT / "tools" / "map_align_out"
CHECK.mkdir(parents=True, exist_ok=True)
REF = sorted(MAP.glob("*.png"), key=lambda p: p.stat().st_size, reverse=True)[0]

W, H = 1078, 750

# Restore original puzzle coords (from first hand layout), canvas 1078x750
# 淳安/桐庐：由合并 SVG 内相对位置还原（cover≈1 匹配）
LAYOUT = {
    "临安区": (55, 40, 1.0, 0),
    "余杭区": (470, 35, 1.0, 0),
    "临平区": (650, 30, 1.05, 0),
    "钱塘区": (820, 145, 1.0, 0),
    "拱墅区": (560, 180, 1.25, 0),
    "西湖区": (490, 215, 1.1, 0),
    "上城区": (575, 235, 1.15, 0),
    "滨江区": (555, 325, 1.25, 0),
    "萧山区": (680, 300, 1.0, 0),
    "富阳区": (380, 340, 1.0, 0),
    "建德市": (300, 480, 0.9, 0),
    # combined was ~ (30, 350) size 696x443; parts located at (92,36)@1.15 and (184,72)@1.0
    "淳安县": (30 + 92, 340 + 36, 1.15, 0),
    "桐庐县": (30 + 184, 340 + 72, 1.0, 0),
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
    shapes = []
    white = Image.new("RGBA", (W, H), (255, 255, 255, 255))
    draw = ImageDraw.Draw(white)
    ref = Image.open(REF).convert("RGBA")
    qa = Image.blend(ref, Image.new("RGBA", (W, H), (255, 255, 255, 255)), 0.45)
    qd = ImageDraw.Draw(qa)

    for name, fname in FILES.items():
        nw, nh, d = parse_svg(MAP / fname)
        x, y, sc, ang = LAYOUT[name]
        pts = apply_tf(path_pts(d), x, y, sc, ang, nw, nh)
        poly = [(float(a), float(b)) for a, b in pts]
        rgb = COLORS[name]
        draw.polygon(poly, fill=rgb + (240,), outline=(255, 255, 255, 255))
        qd.polygon(poly, fill=rgb + (150,), outline=(40, 110, 200, 220))
        cx, cy = float(pts[:, 0].mean()), float(pts[:, 1].mean())
        draw.text((cx - 18, cy - 6), name, fill=(25, 35, 55, 255))
        shapes.append(
            {
                "name": name,
                "names": [name],
                "file": fname,
                "x": round(x, 2),
                "y": round(y, 2),
                "w": round(nw * sc, 2),
                "h": round(nh * sc, 2),
                "scale": sc,
                "rotate": ang,
                "cx": round(cx, 2),
                "cy": round(cy, 2),
                "d": d,
                "nativeW": nw,
                "nativeH": nh,
                "shared": False,
            }
        )
        print(f"{name:8} ({x},{y}) sc={sc}")

    white.save(CHECK / "assembled_white.png")
    qa.save(CHECK / "assembled_on_template.png")
    payload = {"width": W, "height": H, "shapes": shapes}
    OUT_JS.write_text("export default " + json.dumps(payload, ensure_ascii=False) + "\n", encoding="utf-8")
    (CHECK / "layout.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print("OK", OUT_JS)


if __name__ == "__main__":
    main()
