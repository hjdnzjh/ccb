# -*- coding: utf-8 -*-
"""Build hangzhouShapes.js from separate district SVGs + manual pin anchors on 杭州.png."""
from __future__ import annotations

import json
import re
from pathlib import Path

MAP = Path(r"D:\ccb\map")
OUT = Path(r"D:\ccb\web-admin\src\data\hangzhouShapes.js")

# Canvas = 杭州.png
W, H = 1078, 750

# Pin centers tuned to labels on 杭州.png (pixel coords)
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


def parse(path: Path):
    t = path.read_text(encoding="utf-8")
    w = float(re.search(r'width="([\d.]+)', t).group(1))
    h = float(re.search(r'height="([\d.]+)', t).group(1))
    d = re.search(r'\bd="([^"]+)"', t).group(1)
    return w, h, d


shapes = []
for name, fname in FILES.items():
    sw, sh, d = parse(MAP / fname)
    cx, cy = PINS[name]
    # provisional placement: center native svg on pin (for later overlay tuning)
    x = cx - sw / 2
    y = cy - sh / 2
    shapes.append(
        {
            "name": name,
            "names": [name],
            "file": fname,
            "x": round(x, 2),
            "y": round(y, 2),
            "w": sw,
            "h": sh,
            "scale": 1,
            "rotate": 0,
            "cx": cx,
            "cy": cy,
            "d": d,
            "nativeW": sw,
            "nativeH": sh,
            "shared": False,
        }
    )

payload = {
    "width": W,
    "height": H,
    "refImage": "/data/hangzhou-ref.png",
    "shapes": shapes,
}
OUT.write_text("export default " + json.dumps(payload, ensure_ascii=False) + "\n", encoding="utf-8")
print("wrote", OUT, "shapes", len(shapes))
