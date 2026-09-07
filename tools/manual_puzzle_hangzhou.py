# -*- coding: utf-8 -*-
"""
Manual puzzle layout tuned to 杭州.png (template only, not shown in UI).
Canvas = PNG size. Frame = land contour from PNG (looks like original Hangzhou).
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

W, H = 1078, 750

# (x, y, scale, rotate) — translate top-left after Map.vue transform convention
# Tuned so pieces nest like 杭州.png
LAYOUT = {
    # Northern belt
    "临安区": (95, 70, 0.92, 0),
    "余杭区": (500, 55, 0.95, 0),
    "临平区": (720, 55, 1.05, 0),
    # East arm
    "钱塘区": (870, 165, 1.0, 0),
    # Urban core
    "拱墅区": (620, 200, 1.15, 0),
    "西湖区": (555, 230, 1.05, 0),
    "上城区": (640, 245, 1.1, 0),
    "滨江区": (615, 330, 1.2, 0),
    # SE / central
    "萧山区": (700, 310, 0.98, 0),
    "富阳区": (430, 300, 0.95, 0),
    # SW
    "淳安县": (70, 340, 1.05, 0),
    "桐庐县": (320, 390, 1.0, 0),
    "建德市": (300, 480, 0.95, 0),
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

ORDER = [
    "临安区", "余杭区", "临平区", "钱塘区", "拱墅区", "西湖区", "上城区",
    "滨江区", "萧山区", "富阳区", "淳安县", "桐庐县", "建德市",
]


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


def contour_path(mask: np.ndarray) -> str:
    img = Image.fromarray((mask.astype(np.uint8) * 255))
    er = img.filter(ImageFilter.MinFilter(3))
    edge = (np.asarray(img) > 0) & (np.asarray(er) == 0)
    ys, xs = np.where(edge)
    cx, cy = xs.mean(), ys.mean()
    ang = np.arctan2(ys - cy, xs - cx)
    order = np.argsort(ang)
    step = max(1, len(order) // 500)
    pts = list(zip(xs[order][::step].tolist(), ys[order][::step].tolist()))
    parts = [f"M{pts[0][0]} {pts[0][1]}"]
    for x, y in pts[1:]:
        parts.append(f"L{x} {y}")
    parts.append("Z")
    return " ".join(parts)


def main():
    for f in MAP.glob("*.svg"):
        (PUBLIC / f.name).write_bytes(f.read_bytes())

    png = Image.open(sorted(MAP.glob("*.png"), key=lambda p: p.stat().st_size, reverse=True)[0]).convert("RGBA")
    rgb = np.asarray(png.convert("RGB"))
    land = ~((rgb[:, :, 0] < 40) & (rgb[:, :, 1] < 40) & (rgb[:, :, 2] < 40))
    frame_d = contour_path(land)

    # Also map all.svg onto land bbox for secondary frame option
    aw, ah, ad = parse_svg(MAP / "all.svg")
    fp = path_pts(ad)
    ys, xs = np.where(land)
    px0, py0, px1, py1 = int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())
    ax0, ay0 = fp[:, 0].min(), fp[:, 1].min()
    awb, ahb = fp[:, 0].max() - ax0, fp[:, 1].max() - ay0
    # best IoU offsets from earlier: sx~1.86, sy~1.42 relative to awb→pw — use bbox stretch + small nudge
    all_pts = np.empty_like(fp)
    all_pts[:, 0] = (fp[:, 0] - ax0) / awb * (px1 - px0) + px0 - 20
    all_pts[:, 1] = (fp[:, 1] - ay0) / ahb * (py1 - py0) + py0 - 10
    all_d_parts = [f"M{all_pts[0,0]:.2f} {all_pts[0,1]:.2f}"]
    for x, y in all_pts[1:]:
        all_d_parts.append(f"L{x:.2f} {y:.2f}")
    all_d_parts.append("Z")
    all_frame_d = " ".join(all_d_parts)

    white = Image.new("RGBA", (W, H), (255, 255, 255, 255))
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    shapes = []

    for name in ORDER:
        fname = FILES[name]
        nw, nh, d = parse_svg(MAP / fname)
        x, y, sc, rot = LAYOUT[name]
        pts = path_pts(d)
        tp = apply_tf(pts, x, y, sc, rot, nw, nh)
        rgb_c = COLORS[name]
        ld.polygon([(float(a), float(b)) for a, b in tp], fill=rgb_c + (220,), outline=(255, 255, 255, 255))
        cx = float(tp[:, 0].mean())
        cy = float(tp[:, 1].mean())
        print(f"{name} bbox=({tp[:,0].min():.0f},{tp[:,1].min():.0f})-({tp[:,0].max():.0f},{tp[:,1].max():.0f}) c=({cx:.0f},{cy:.0f})")
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
    # draw all.svg frame (user-provided framework)
    ImageDraw.Draw(white).polygon([(float(x), float(y)) for x, y in all_pts], outline=(30, 40, 60, 200))
    white.save(CHECK / "assembled_white.png")

    over = Image.alpha_composite(png, layer)
    ImageDraw.Draw(over).polygon([(float(x), float(y)) for x, y in all_pts], outline=(255, 40, 40, 220))
    over.save(CHECK / "matched_on_png.png")

    payload = {
        "width": W,
        "height": H,
        "frame": {"file": "all.svg", "d": all_frame_d, "nativeW": aw, "nativeH": ah},
        "shapes": shapes,
    }
    OUT_JS.write_text("export default " + json.dumps(payload, ensure_ascii=False) + "\n", encoding="utf-8")
    (CHECK / "layout_positions.json").write_text(
        json.dumps({s["name"]: {k: s[k] for k in ("x", "y", "w", "h", "scale", "rotate", "cx", "cy")} for s in shapes}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print("wrote", OUT_JS)


if __name__ == "__main__":
    main()
