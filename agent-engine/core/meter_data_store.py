"""
杭州水表 CSV 数据源
字段: meter_id,city,district,community,timestamp,water_usage,flow_rate,pressure,temperature,status,alarm
"""
from __future__ import annotations

import csv
import hashlib
from pathlib import Path
from typing import Dict, List, Optional


class MeterDataStore:
    """从 CSV 加载真实传感/抄表样本，供抄表智能体使用。"""

    def __init__(self, csv_path: Optional[str] = None):
        root = Path(__file__).resolve().parent.parent
        self.csv_path = Path(csv_path) if csv_path else root / 'data' / 'hangzhou_water_meter_10000.csv'
        if not self.csv_path.exists():
            fallback = root / 'data' / 'hangzhou_water_meter_1000.csv'
            if fallback.exists():
                self.csv_path = fallback
        self.rows: List[Dict] = []
        self.by_id: Dict[str, Dict] = {}
        self._load()

    def _load(self) -> None:
        if not self.csv_path.exists():
            raise FileNotFoundError(f'抄表数据文件不存在: {self.csv_path}')

        with self.csv_path.open('r', encoding='utf-8-sig', newline='') as f:
            reader = csv.DictReader(f)
            for raw in reader:
                meter_id = (raw.get('meter_id') or '').strip()
                if not meter_id:
                    continue
                usage = float(raw.get('water_usage') or 0)
                # 累计读数：用表号派生基值 + 本期用量，便于计费/校验
                seed = int(hashlib.md5(meter_id.encode('utf-8')).hexdigest()[:8], 16)
                base = 800 + (seed % 9000) + (seed % 100) / 10.0
                alarm = int(float(raw.get('alarm') or 0))
                status = (raw.get('status') or '').strip()
                pressure = float(raw.get('pressure') or 0)
                flow = float(raw.get('flow_rate') or 0)
                row = {
                    'meter_id': meter_id,
                    'city': (raw.get('city') or '').strip(),
                    'district': (raw.get('district') or '').strip(),
                    'community': (raw.get('community') or '').strip(),
                    'timestamp': (raw.get('timestamp') or '').strip(),
                    'water_usage': usage,
                    'flow_rate': flow,
                    'pressure': pressure,
                    'temperature': float(raw.get('temperature') or 0),
                    'status': status,
                    'alarm': alarm,
                    'reading': round(base + usage, 1),
                    'last_reading': round(base, 1),
                    'signal_strength': self._signal_from_sensors(pressure, flow, alarm),
                    'confidence': self._confidence(status, alarm, pressure),
                }
                self.rows.append(row)
                self.by_id[meter_id] = row

    @staticmethod
    def _signal_from_sensors(pressure: float, flow: float, alarm: int) -> int:
        score = 92
        if pressure < 0.25 or pressure > 0.45:
            score -= 8
        if flow > 15:
            score -= 5
        if alarm:
            score -= 15
        return max(55, min(100, score))

    @staticmethod
    def _confidence(status: str, alarm: int, pressure: float) -> float:
        if alarm:
            return 0.78
        if status and status not in ('正常', 'normal', 'ok'):
            return 0.86
        if pressure < 0.25 or pressure > 0.45:
            return 0.90
        return 0.98

    def get(self, meter_id: str) -> Optional[Dict]:
        return self.by_id.get(meter_id)

    def sample(self, n: int = 1) -> List[Dict]:
        if not self.rows:
            return []
        n = max(1, min(n, len(self.rows)))
        # 取前 n 个稳定样本，便于演示可复现
        return self.rows[:n]

    def by_district(self, district: str, limit: int = 20) -> List[Dict]:
        district = (district or '').strip()
        rows = [r for r in self.rows if (not district or district == 'all' or r['district'] == district)]
        return rows[:limit]

    def stats(self) -> Dict:
        return {
            'total': len(self.rows),
            'alarms': sum(1 for r in self.rows if r['alarm']),
            'districts': sorted({r['district'] for r in self.rows if r['district']}),
            'source': str(self.csv_path),
        }


_STORE: Optional[MeterDataStore] = None


def get_meter_store() -> MeterDataStore:
    global _STORE
    if _STORE is None:
        _STORE = MeterDataStore()
    return _STORE
