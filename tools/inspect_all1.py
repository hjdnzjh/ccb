# -*- coding: utf-8 -*-
from pathlib import Path
import re

p = Path(r"D:\ccb\map\all-1.svg")
t = p.read_text(encoding="utf-8")
print("size bytes", p.stat().st_size)
print("len chars", len(t))
print("--- head ---")
print(t[:2000])
print("---")
w = re.search(r'width="([^"]+)"', t)
h = re.search(r'height="([^"]+)"', t)
vb = re.search(r'viewBox="([^"]+)"', t)
print("width", w.group(1) if w else None)
print("height", h.group(1) if h else None)
print("viewBox", vb.group(1) if vb else None)
paths = re.findall(r'<path\b[^>]*>', t)
print("path count", len(paths))
groups = re.findall(r'<g\b[^>]*>', t)
print("group count", len(groups))
# ids / classes
ids = re.findall(r'\bid="([^"]+)"', t)
print("ids", ids[:40], "n=", len(ids))
classes = re.findall(r'\bclass="([^"]+)"', t)
print("classes sample", classes[:20])
# fill colors
fills = re.findall(r'\bfill="([^"]+)"', t)
from collections import Counter
print("fills", Counter(fills).most_common(15))
# path d lengths
ds = re.findall(r'\bd="([^"]*)"', t)
print("d count", len(ds), "lengths", [len(d) for d in ds[:20]])
