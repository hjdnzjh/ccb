#!/usr/bin/env python3
"""从 hangzhou_water_meter_*.csv 重建孪生地图层级数据。"""
from __future__ import annotations

import csv
import json
from collections import defaultdict
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CSV = Path(
    r"C:\Users\34254\Documents\Codex\2026-08-03\1000-ai-https-z-hangzhou-com\outputs\hangzhou_water_meter_10000.csv"
)
OUT_JS = ROOT / "web-admin" / "src" / "data" / "hangzhouHierarchy.js"
OUT_JSON = ROOT / "web-admin" / "public" / "data" / "hangzhou_hierarchy.json"
COPY_TARGETS = [
    ROOT / "agent-engine" / "data" / "hangzhou_water_meter_10000.csv",
    ROOT / "database" / "hangzhou_water_meter_10000.csv",
    ROOT / "web-admin" / "public" / "data" / "hangzhou_water_meter_10000.csv",
]

# 全市区县（含无样本），布局沿用现有地图锚点
DISTRICT_LAYOUT = {
    "临安区": {"x": 8, "y": 10, "w": 30, "h": 36, "tone": "teal"},
    "余杭区": {"x": 40, "y": 6, "w": 20, "h": 16, "tone": "blue"},
    "临平区": {"x": 62, "y": 8, "w": 16, "h": 14, "tone": "blue"},
    "钱塘区": {"x": 70, "y": 22, "w": 18, "h": 14, "tone": "cyan"},
    "拱墅区": {"x": 48, "y": 22, "w": 14, "h": 12, "tone": "blue"},
    "西湖区": {"x": 36, "y": 24, "w": 14, "h": 18, "tone": "teal"},
    "上城区": {"x": 52, "y": 30, "w": 12, "h": 12, "tone": "blue"},
    "滨江区": {"x": 54, "y": 40, "w": 12, "h": 10, "tone": "cyan"},
    "萧山区": {"x": 62, "y": 40, "w": 22, "h": 22, "tone": "blue"},
    "富阳区": {"x": 28, "y": 42, "w": 22, "h": 20, "tone": "teal"},
    "桐庐县": {"x": 18, "y": 58, "w": 22, "h": 18, "tone": "muted"},
    "淳安县": {"x": 4, "y": 62, "w": 26, "h": 28, "tone": "muted"},
    "建德市": {"x": 28, "y": 70, "w": 22, "h": 20, "tone": "muted"},
}
ZONE_NAMES = ("一号片区", "二号片区", "三号片区", "四号片区")


def display_name(raw: str, fallback_id: str) -> str:
    """有名字写名字，没有名字写编号。"""
    name = (raw or "").strip()
    return name if name else fallback_id


def zone_for(community: str) -> str:
    h = 0
    for ch in community:
        h = (h * 131 + ord(ch)) & 0xFFFFFFFF
    return ZONE_NAMES[h % len(ZONE_NAMES)]


def fnum(v, nd=3):
    try:
        return round(float(v or 0), nd)
    except (TypeError, ValueError):
        return 0.0


def build(csv_path: Path) -> dict:
    rows = []
    with csv_path.open("r", encoding="utf-8-sig", newline="") as f:
        for raw in csv.DictReader(f):
            meter_id = (raw.get("meter_id") or "").strip()
            if not meter_id:
                continue
            district = (raw.get("district") or "").strip()
            community = display_name(raw.get("community") or "", meter_id)
            alarm = int(float(raw.get("alarm") or 0))
            status = (raw.get("status") or "").strip() or ("异常" if alarm else "正常")
            rows.append(
                {
                    "meterId": meter_id,
                    "city": (raw.get("city") or "杭州").strip() or "杭州",
                    "district": district,
                    "community": community,
                    "timestamp": (raw.get("timestamp") or "").strip(),
                    "waterUsage": fnum(raw.get("water_usage"), 3),
                    "flowRate": fnum(raw.get("flow_rate"), 1),
                    "pressure": fnum(raw.get("pressure"), 3),
                    "temperature": fnum(raw.get("temperature"), 1),
                    "status": status,
                    "alarm": 1 if alarm else 0,
                }
            )

    # district -> community -> meters
    by_dc = defaultdict(lambda: defaultdict(list))
    for r in rows:
        if not r["district"]:
            continue
        by_dc[r["district"]][r["community"]].append(r)

    total_meters = len(rows)
    total_alarms = sum(1 for r in rows if r["alarm"])
    districts = []

    for name, layout in DISTRICT_LAYOUT.items():
        communities_map = by_dc.get(name, {})
        meters = sum(len(v) for v in communities_map.values())
        alarms = sum(1 for ms in communities_map.values() for m in ms if m["alarm"])
        usage = sum(m["waterUsage"] for ms in communities_map.values() for m in ms)
        has_data = meters > 0

        # zone -> community -> meters
        zones_map = defaultdict(lambda: defaultdict(list))
        for cname, ms in communities_map.items():
            zname = zone_for(cname)
            for m in ms:
                m2 = dict(m)
                m2["zone"] = zname
                zones_map[zname][cname].append(m2)

        zones = []
        for zname in ZONE_NAMES:
            comms = zones_map.get(zname)
            if not comms:
                continue
            z_meters = sum(len(v) for v in comms.values())
            z_alarms = sum(1 for ms in comms.values() for m in ms if m["alarm"])
            z_usage = sum(m["waterUsage"] for ms in comms.values() for m in ms)
            communities = []
            for cname, ms in sorted(comms.items(), key=lambda x: -len(x[1])):
                c_alarms = sum(1 for m in ms if m["alarm"])
                c_usage = sum(m["waterUsage"] for m in ms)
                communities.append(
                    {
                        "name": cname,
                        "meters": len(ms),
                        "alarmCount": c_alarms,
                        "alarmRate": round(c_alarms / len(ms) * 100, 1) if ms else 0.0,
                        "share": round(len(ms) / z_meters * 100, 1) if z_meters else 0.0,
                        "usage": round(c_usage, 3),
                        "avgPressure": round(sum(m["pressure"] for m in ms) / len(ms), 3),
                        "avgFlow": round(sum(m["flowRate"] for m in ms) / len(ms), 2),
                        "metersDetail": ms,
                    }
                )
            zones.append(
                {
                    "name": zname,
                    "meters": z_meters,
                    "alarmCount": z_alarms,
                    "alarmRate": round(z_alarms / z_meters * 100, 1) if z_meters else 0.0,
                    "share": round(z_meters / meters * 100, 1) if meters else 0.0,
                    "usage": round(z_usage, 3),
                    "communities": communities,
                }
            )

        districts.append(
            {
                "name": name,
                "meters": meters,
                "alarmCount": alarms,
                "alarmRate": round(alarms / meters * 100, 1) if meters else 0.0,
                "share": round(meters / total_meters * 100, 1) if total_meters else 0.0,
                "usage": round(usage, 3),
                "hasData": has_data,
                "layout": layout,
                "zones": zones,
            }
        )

    latest = max((r["timestamp"] for r in rows if r["timestamp"]), default="")
    return {
        "city": "杭州",
        "totalMeters": total_meters,
        "totalAlarms": total_alarms,
        "updatedAt": latest or datetime.now().strftime("%Y-%m-%d %H:%M"),
        "metric": "alarmRate",
        "metricLabel": "异常占比",
        "districts": districts,
    }


def main():
    src = DEFAULT_CSV
    if not src.exists():
        alt = ROOT / "database" / "hangzhou_water_meter_10000.csv"
        src = alt if alt.exists() else ROOT / "agent-engine" / "data" / "hangzhou_water_meter_1000.csv"
    print("source", src)
    data = build(src)
    text = json.dumps(data, ensure_ascii=False, separators=(",", ": "))
    OUT_JS.write_text(f"export default {text}\n", encoding="utf-8")
    OUT_JSON.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    raw = src.read_bytes()
    for t in COPY_TARGETS:
        t.parent.mkdir(parents=True, exist_ok=True)
        t.write_bytes(raw)
        print("copied", t)
    print("wrote", OUT_JS, "meters", data["totalMeters"], "alarms", data["totalAlarms"])


if __name__ == "__main__":
    main()
