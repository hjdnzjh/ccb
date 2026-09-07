# -*- coding: utf-8 -*-
"""Compare all.svg silhouette vs PNG template; design layout in all.svg coords."""
from __future__ import annotations

import json
import math
import re
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageOps

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

# Manual layout on all.svg canvas (600.5 x 481.5)
# Values: x, y, scale, rotate — tuned so pieces nest like Hangzhou
# Relative sizes from native SVG dims; overall scale ~0.55–0.72 so union fits frame
LAYOUT = {
    # NW large
    "临安区": (28, 18, 0.58, 0),
    # N / NE
    "余杭区": (250, 8, 0.62, 0),
    "临平区": (365, 12, 0.72, 0),
    # urban core (small)
    "拱墅区": (318, 105, 0.78, 0),
    "西湖区": (278, 125, 0.70, 0),
    "上城区": (335, 138, 0.72, 0),
    "滨江区": (318, 185, 0.78, 0),
    # E
    "钱塘区": (430, 95, 0.58, -2),
    # SE
    "萧山区": (355, 175, 0.62, 0),
    # S-central
    "富阳区": (195, 155, 0.58, 0),
    # SW triangle
    "淳安县": (8, 165, 0.72, 0),
    "桐庐县": (140, 210, 0.68, 0),
    "建德市": (145, 265, 0.52, 0),
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


def main():
    for f in MAP.glob("*.svg"):
        (PUBLIC / f.name).write_bytes(f.read_bytes())

    aw, ah, ad = parse_svg(MAP / "all.svg")
    frame_pts = path_pts(ad)

    # PNG silhouette for QA overlay
    pngs = sorted(MAP.glob("*.png"), key=lambda p: p.stat().st_size, reverse=True)
    ref = Image.open(pngs[0]).convert("RGBA")
    # silhouette mask of all.svg at native size
    frame_img = Image.new("RGBA", (int(aw), int(ah)), (255, 255, 255, 255))
    draw = ImageDraw.Draw(frame_img)
    draw.polygon([(float(x), float(y)) for x, y in frame_pts], outline=(20, 30, 50, 255), fill=(240, 244, 250, 255))

    # also compare aspects
    print("all.svg", aw, ah, "aspect", aw / ah)
    print("png", ref.size, "aspect", ref.size[0] / ref.size[1])

    # Fit PNG into all.svg box for QA (contain)
    rw, rh = ref.size
    scale = min(aw / rw, ah / rh)
    nw, nh = int(rw * scale), int(rh * scale)
    png_fit = ref.resize((nw, nh), Image.Resampling.LANCZOS)
    qa = Image.new("RGBA", (int(aw), int(ah)), (255, 255, 255, 255))
    ox, oy = int((aw - nw) / 2), int((ah - nh) / 2)
    qa.paste(png_fit, (ox, oy), png_fit if png_fit.mode == "RGBA" else None)
    # draw frame on top
    ImageDraw.Draw(qa).polygon([(float(x), float(y)) for x, y in frame_pts], outline=(220, 40, 40, 255))
    qa.save(CHECK / "frame_vs_png.png")

    # Build district assembly
    white = Image.new("RGBA", (int(math.ceil(aw)), int(math.ceil(ah))), (255, 255, 255, 255))
    overlay = Image.new("RGBA", white.size, (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    shapes = []

    for fname, name in FILES:
        nw0, nh0, d = parse_svg(MAP / fname)
        x, y, sc, rot = LAYOUT[name]
        pts = path_pts(d)
        tp = apply_tf(pts, x, y, sc, rot, nw0, nh0)
        poly = [(float(a), float(b)) for a, b in tp]
        rgb = COLORS[name]
        od.polygon(poly, fill=rgb + (200,), outline=(255, 255, 255, 255))
        cx = float(tp[:, 0].mean())
        cy = float(tp[:, 1].mean())
        shapes.append(
            {
                "name": name,
                "names": [name],
                "file": fname,
                "x": round(x, 2),
                "y": round(y, 2),
                "w": round(nw0 * sc, 2),
                "h": round(nh0 * sc, 2),
                "scale": round(sc, 4),
                "rotate": rot,
                "cx": round(cx, 2),
                "cy": round(cy, 2),
                "d": d,
                "nativeW": nw0,
                "nativeH": nh0,
                "shared": False,
            }
        )
        print(f"{name} bbox=({tp[:,0].min():.0f},{tp[:,1].min():.0f})-({tp[:,0].max():.0f},{tp[:,1].max():.0f})")

    white = Image.alpha_composite(white, overlay)
    # frame outline
    ImageDraw.Draw(white).polygon([(float(x), float(y)) for x, y in frame_pts], outline=(30, 40, 60, 220))
    # labels
    ld = ImageDraw.Draw(white)
    for s in shapes:
        ld.text((s["cx"] - 12, s["cy"] - 4), s["name"][:2], fill=(25, 35, 55, 255))

    white.save(CHECK / "assembled_on_all.png")
    white.save(CHECK / "assembled_white.png")

    # dual QA: districts translucent over framed png
    dual = Image.new("RGBA", white.size, (255, 255, 255, 255))
    dual.paste(qa.resize(white.size, Image.Resampling.LANCZOS), (0, 0))
    dual = Image.alpha_composite(dual, overlay)
    ImageDraw.Draw(dual).polygon([(float(x), float(y)) for x, y in frame_pts], outline=(220, 40, 40, 255))
    dual.save(CHECK / "assembled_over_png.png")

    payload = {
        "width": aw,
        "height": ah,
        "frame": {"file": "all.svg", "d": ad, "nativeW": aw, "nativeH": ah},
        "shapes": shapes,
    }
    OUT_JS.write_text("export default " + json.dumps(payload, ensure_ascii=False) + "\n", encoding="utf-8")
    (CHECK / "layout.json").write_text(json.dumps({k: v for k, v in payload.items() if k != "shapes"}, ensure_ascii=False, indent=2), encoding="utf-8")
    # also write layout positions only
    (CHECK / "layout_positions.json").write_text(
        json.dumps({s["name"]: {"x": s["x"], "y": s["y"], "scale": s["scale"], "rotate": s["rotate"], "cx": s["cx"], "cy": s["cy"]} for s in shapes}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print("wrote", OUT_JS)


if __name__ == "__main__":
    main()
