#!/usr/bin/env python3
"""生成并导入杭州 CSV 到 MySQL（经 docker water-mysql）。命名：有名用名，无名用编号；编号始终保留。"""
from __future__ import annotations

import csv
import hashlib
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CSV = Path(
    r"C:\Users\34254\Documents\Codex\2026-08-03\1000-ai-https-z-hangzhou-com\outputs\hangzhou_water_meter_10000.csv"
)
SQL_OUT = ROOT / "database" / "_import_hangzhou_10000.sql"

AREA_CODE = {
    "西湖区": "HZ-XH",
    "拱墅区": "HZ-GS",
    "上城区": "HZ-SC",
    "滨江区": "HZ-BJ",
    "萧山区": "HZ-XS",
    "余杭区": "HZ-YH",
    "钱塘区": "HZ-QT",
    "临平区": "HZ-LP",
    "富阳区": "HZ-FY",
    "临安区": "HZ-LA",
    "桐庐县": "HZ-TL",
    "淳安县": "HZ-CA",
    "建德市": "HZ-JD",
}


def esc(s: str) -> str:
    return (s or "").replace("\\", "\\\\").replace("'", "''")


def display_name(raw: str, fallback_id: str) -> str:
    name = (raw or "").strip()
    return name if name else fallback_id


def reading_base(meter_id: str) -> float:
    seed = int(hashlib.md5(meter_id.encode("utf-8")).hexdigest()[:8], 16)
    return 800 + (seed % 9000) + (seed % 100) / 10.0


def main():
    src = DEFAULT_CSV if DEFAULT_CSV.exists() else ROOT / "database" / "hangzhou_water_meter_10000.csv"
    with src.open("r", encoding="utf-8-sig", newline="") as f:
        rows = [r for r in csv.DictReader(f) if (r.get("meter_id") or "").strip()]

    districts = sorted({(r.get("district") or "").strip() for r in rows if (r.get("district") or "").strip()})
    lines = [
        "USE water_meter_db;",
        "SET NAMES utf8mb4;",
        "SET FOREIGN_KEY_CHECKS=0;",
    ]
    for d in districts:
        code = AREA_CODE.get(d, f"HZ-{abs(hash(d)) % 10000:04d}")
        lines.append(
            "INSERT INTO area (area_code, area_name, level, deleted) "
            f"SELECT '{esc(code)}', '{esc(d)}', 2, 0 FROM DUAL "
            f"WHERE NOT EXISTS (SELECT 1 FROM area WHERE area_code='{esc(code)}' AND deleted=0);"
        )

    # users then meters — use INSERT ... ON DUPLICATE via unique username / meter_no
    batch_u = []
    batch_m = []
    for raw in rows:
        meter_id = raw["meter_id"].strip()
        district = (raw.get("district") or "").strip() or "杭州"
        community = display_name(raw.get("community") or "", meter_id)
        username = meter_id  # 编号
        real_name = community  # 有名字写名字，否则上面已回退编号
        phone = f"138{int(hashlib.md5(meter_id.encode()).hexdigest()[:8], 16) % 100000000:08d}"
        usage = float(raw.get("water_usage") or 0)
        alarm = int(float(raw.get("alarm") or 0))
        status_txt = (raw.get("status") or "").strip()
        meter_status = 1 if alarm or (status_txt and status_txt not in ("正常", "normal", "ok")) else 0
        base = reading_base(meter_id)
        current = round(base + usage, 2)
        address = f"杭州市{district}{community}"
        code = AREA_CODE.get(district, f"HZ-{abs(hash(district)) % 10000:04d}")

        batch_u.append(
            f"('{esc(username)}','123456','{esc(real_name)}','{phone}','residential',0,"
            f"(SELECT id FROM area WHERE area_code='{esc(code)}' AND deleted=0 LIMIT 1),"
            f"'{esc(address)}',0.00,'A',0)"
        )
        batch_m.append(
            f"('{esc(meter_id)}','digital','NB-IoT',"
            f"(SELECT id FROM sys_user WHERE username='{esc(username)}' AND deleted=0 LIMIT 1),"
            f"(SELECT id FROM area WHERE area_code='{esc(code)}' AND deleted=0 LIMIT 1),"
            f"'{esc(address)}',{meter_status},{current},{base},90,85,0)"
        )

    def flush_users(chunk):
        lines.append(
            "INSERT INTO sys_user (username, password, real_name, phone, user_type, status, area_id, address, balance, credit_level, deleted) VALUES\n"
            + ",\n".join(chunk)
            + "\nON DUPLICATE KEY UPDATE real_name=VALUES(real_name), phone=VALUES(phone), "
            "area_id=VALUES(area_id), address=VALUES(address), deleted=0;"
        )

    def flush_meters(chunk):
        lines.append(
            "INSERT INTO water_meter (meter_no, meter_type, comm_type, user_id, area_id, install_address, status, current_reading, last_reading, signal_strength, battery_level, deleted) VALUES\n"
            + ",\n".join(chunk)
            + "\nON DUPLICATE KEY UPDATE user_id=VALUES(user_id), area_id=VALUES(area_id), "
            "install_address=VALUES(install_address), status=VALUES(status), "
            "current_reading=VALUES(current_reading), last_reading=VALUES(last_reading), deleted=0;"
        )

    # MySQL may not have UNIQUE on username if only uk_username - check init.sql - yes UNIQUE KEY uk_username
    # meter_no should be unique too
    step = 200
    for i in range(0, len(batch_u), step):
        flush_users(batch_u[i : i + step])
    for i in range(0, len(batch_m), step):
        flush_meters(batch_m[i : i + step])

    lines.append("SET FOREIGN_KEY_CHECKS=1;")
    SQL_OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("wrote", SQL_OUT, "bytes", SQL_OUT.stat().st_size)

    # copy into container and execute
    subprocess.check_call(["docker", "cp", str(SQL_OUT), "water-mysql:/tmp/_import_hangzhou_10000.sql"])
    subprocess.check_call(
        [
            "docker",
            "exec",
            "water-mysql",
            "mysql",
            "-uroot",
            "-p123456",
            "--default-character-set=utf8mb4",
            "-e",
            "source /tmp/_import_hangzhou_10000.sql",
        ]
    )
    print("import finished")


if __name__ == "__main__":
    main()
