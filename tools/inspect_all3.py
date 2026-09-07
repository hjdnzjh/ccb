# -*- coding: utf-8 -*-
from pathlib import Path
import re
from collections import Counter

MAP = Path(r"D:\ccb\map")

def inspect(name):
    p = MAP / name
    if not p.exists():
        # try glob
        hits = list(MAP.glob(name))
        if not hits:
            print("MISSING", name)
            return
        p = hits[0]
    t = p.read_text(encoding="utf-8", errors="replace")
    print("=" * 60)
    print(p.name, "bytes", p.stat().st_size)
    w = re.search(r'width="([^"]+)"', t)
    h = re.search(r'height="([^"]+)"', t)
    vb = re.search(r'viewBox="([^"]+)"', t)
    print("width", w.group(1) if w else None, "height", h.group(1) if h else None, "viewBox", vb.group(1) if vb else None)
    paths = re.findall(r"<path\b[^>]*>", t, flags=re.I)
    print("path tags", len(paths))
    polys = re.findall(r"<polygon\b[^>]*>", t, flags=re.I)
    print("polygon tags", len(polys))
    groups = re.findall(r"<g\b[^>]*>", t, flags=re.I)
    print("groups", len(groups))
    ids = re.findall(r'\bid="([^"]+)"', t)
    print("ids n=", len(ids), "sample", ids[:20])
    fills = Counter(re.findall(r'\bfill="([^"]+)"', t))
    print("fills", fills.most_common(12))
    ds = re.findall(r'\bd="([^"]*)"', t)
    print("d count", len(ds), "d lengths", sorted([len(d) for d in ds], reverse=True)[:15])
    # transforms
    tfs = re.findall(r'\btransform="([^"]+)"', t)
    print("transforms", len(tfs), "sample", tfs[:5])
    print("head:")
    print(t[:1200].replace("\n", " ")[:1200])

inspect("all-3.svg")
inspect("all-2*.svg")
inspect("all-1.svg")
inspect("all.svg")
