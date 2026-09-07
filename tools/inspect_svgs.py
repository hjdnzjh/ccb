# -*- coding: utf-8 -*-
from pathlib import Path
import re
import json

MAP = Path(r"D:\ccb\map")

def parse(path):
    t = path.read_text(encoding="utf-8")
    w = float(re.search(r'width="([\d.]+)', t).group(1))
    h = float(re.search(r'height="([\d.]+)', t).group(1))
    d = re.search(r'\bd="([^"]+)"', t).group(1)
    nums = [float(x) for x in re.findall(r"-?\d*\.?\d+(?:e[-+]?\d+)?", d)]
    xs = nums[0::2]
    ys = nums[1::2]
    # rough bbox from all numbers (includes control points)
    return {
        "file": path.name,
        "w": w,
        "h": h,
        "xmin": min(xs) if xs else None,
        "xmax": max(xs) if xs else None,
        "ymin": min(ys) if ys else None,
        "ymax": max(ys) if ys else None,
        "n": len(nums) // 2,
    }

rows = [parse(p) for p in sorted(MAP.glob("*.svg"))]
print(json.dumps(rows, ensure_ascii=False, indent=2))
print("pngs:", [p.name for p in MAP.glob("*.png")])
