# -*- coding: utf-8 -*-
import re, json, pathlib, shutil

src = pathlib.Path(r'D:\ccb\map')
pub = pathlib.Path(r'D:\ccb\web-admin\public\map')
out = pathlib.Path(r'D:\ccb\web-admin\src\data')
pub.mkdir(parents=True, exist_ok=True)
out.mkdir(parents=True, exist_ok=True)

for f in src.glob('*.svg'):
    shutil.copy2(f, pub / f.name)

FILES = {
    'linan.svg': ['临安区'],
    'yuhang.svg': ['余杭区'],
    'linping.svg': ['临平区'],
    'qiantang.svg': ['钱塘区'],
    'gongshu.svg': ['拱墅区'],
    'xihu.svg': ['西湖区'],
    'shangche.svg': ['上城区'],
    'binjiang.svg': ['滨江区'],
    'xiaoshan.svg': ['萧山区'],
    'fuyang.svg': ['富阳区'],
    'jiandeshi.svg': ['建德市'],
    'cunanxian&tongluxian.svg': ['淳安县', '桐庐县'],
}

# top-left on 1100x780 canvas
LAYOUT = {
    '临安区': (55, 55),
    '余杭区': (470, 45),
    '临平区': (650, 35),
    '钱塘区': (820, 160),
    '拱墅区': (560, 195),
    '西湖区': (490, 230),
    '上城区': (575, 250),
    '滨江区': (555, 340),
    '萧山区': (680, 320),
    '富阳区': (380, 360),
    '桐庐县': (300, 480),
    '淳安县': (40, 430),
    '建德市': (260, 580),
}

# For shared SVG: place once, both districts reference same geometry via sharedGroup
shapes = []
for fn, names in FILES.items():
    text = (src / fn).read_text(encoding='utf-8')
    wm = re.search(r'width="([0-9.]+)px"', text)
    hm = re.search(r'height="([0-9.]+)px"', text)
    pm = re.search(r'<path[^>]*\sd="([^"]+)"', text)
    if not (wm and hm and pm):
        print('skip', fn)
        continue
    w, h, d = float(wm.group(1)), float(hm.group(1)), pm.group(1)
    if len(names) > 1:
        # place combined shape once under first name position (淳安)
        x, y = LAYOUT[names[0]]
        shapes.append({
            'name': '淳安县·桐庐县',
            'names': names,
            'file': fn,
            'x': x, 'y': y, 'w': w, 'h': h,
            'd': d,
            'shared': True,
        })
    else:
        name = names[0]
        x, y = LAYOUT[name]
        shapes.append({
            'name': name,
            'names': [name],
            'file': fn,
            'x': x, 'y': y, 'w': w, 'h': h,
            'd': d,
            'shared': False,
        })

(out / 'hangzhouShapes.js').write_text(
    'export default ' + json.dumps({'width': 1100, 'height': 780, 'shapes': shapes}, ensure_ascii=False) + '\n',
    encoding='utf-8'
)
print('ok', len(shapes))
for s in shapes:
    print(s['name'], round(s['w']), round(s['h']), s['x'], s['y'])
