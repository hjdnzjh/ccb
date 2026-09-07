# -*- coding: utf-8 -*-
"""Rebuild hangzhouShapes from all-3 with corrected name mapping."""
from __future__ import annotations

import json
import math
import re
import shutil
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(r"D:\ccb")
MAP = ROOT / "map"
SRC = MAP / "all-3.svg"
CHECK = ROOT / "tools" / "map_align_out"
OUT_JS = ROOT / "web-admin" / "src" / "data" / "hangzhouShapes.js"
PUBLIC = ROOT / "web-admin" / "public" / "map"

# Manual mapping after inspecting centroids on Hangzhou geography
# path index -> district name
PATH_NAME = {
    7: "临安区",   # NW large (329,215)
    5: "余杭区",   # N (470,163)
    9: "临平区",   # NE (546,145)
    8: "钱塘区",   # E arm (601,182)
    1: "拱墅区",   # urban N (525,172)
    2: "西湖区",   # urban W (503,209)
    0: "上城区",   # urban E-ish (538,182) — may swap with 滨江
    3: "滨江区",   # smallest urban S (534,225)
    4: "萧山区",   # E (562,249)
    6: "富阳区",   # central (442,279) east of 桐庐
    10: "桐庐县",  # SW-central (377,335)
    11: "淳安县",  # far W (218,403)
    12: "建德市",  # S (330,457)
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


def path_pts(d: str):
    tok = re.findall(r"[MmLlHhVvCcSsQqTtAaZz]|-?\d*\.?\d+(?:e[-+]?\d+)?", d.replace(",", " "))
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
        elif cmd in "Hh":
            x = n()
            if cmd == "h":
                x += cx
            cx = x
            pts.append((cx, cy))
        elif cmd in "Vv":
            y = n()
            if cmd == "v":
                y += cy
            cy = y
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
    shutil.copy2(SRC, PUBLIC / "all-3.svg")
    t = SRC.read_text(encoding="utf-8")
    w = float(re.search(r'width="([\d.]+)"', t).group(1))
    h = float(re.search(r'height="([\d.]+)"', t).group(1))
    ds = re.findall(r'\bd="([^"]+)"', t)
    assert len(ds) == 13

    white = Image.new("RGBA", (int(w), int(h)), (255, 255, 255, 255))
    draw = ImageDraw.Draw(white)
    shapes = []

    for i, d in enumerate(ds):
        name = PATH_NAME[i]
        pts = path_pts(d)
        minx, miny = pts.min(0)
        maxx, maxy = pts.max(0)
        cx, cy = float(pts[:, 0].mean()), float(pts[:, 1].mean())
        nw, nh = float(maxx - minx), float(maxy - miny)
        rgb = COLORS[name]
        draw.polygon([(float(a), float(b)) for a, b in pts], fill=rgb + (235,), outline=(255, 255, 255, 255))
        draw.text((cx - 20, cy - 6), name, fill=(25, 35, 55, 255))
        print(f"{i:2} {name} c=({cx:.0f},{cy:.0f}) {nw:.0f}x{nh:.0f}")
        shapes.append(
            {
                "name": name,
                "names": [name],
                "file": "all-3.svg",
                "x": 0,
                "y": 0,
                "w": round(nw, 2),
                "h": round(nh, 2),
                "scale": 1,
                "rotate": 0,
                "cx": round(cx, 2),
                "cy": round(cy, 2),
                "d": d,
                "nativeW": round(nw, 2),
                "nativeH": round(nh, 2),
                "shared": False,
                "absolute": True,
            }
        )

    white.save(CHECK / "assembled_from_all3.png")
    # overlay on PNG if available for name check
    pngs = sorted(MAP.glob("*.png"), key=lambda p: p.stat().st_size, reverse=True)
    if pngs:
        png = Image.open(pngs[0]).convert("RGBA")
        # scale all-3 onto png
        overlay = white.resize(png.size, Image.Resampling.LANCZOS)
        # make white bg transparent-ish
        arr = np.asarray(overlay).copy()
        mask = (arr[:, :, 0] > 250) & (arr[:, :, 1] > 250) & (arr[:, :, 2] > 250)
        arr[mask, 3] = 0
        arr[~mask, 3] = 120
        over = Image.alpha_composite(png, Image.fromarray(arr))
        over.save(CHECK / "all3_over_png.png")

    payload = {
        "width": w,
        "height": h,
        "frame": {"file": "all-3.svg", "d": "", "nativeW": w, "nativeH": h},
        "shapes": shapes,
    }
    OUT_JS.write_text("export default " + json.dumps(payload, ensure_ascii=False) + "\n", encoding="utf-8")
    print("wrote", OUT_JS)


if __name__ == "__main__":
    main()
