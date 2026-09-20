"""Event-time features. Missing observations are never replaced by zero."""
from datetime import datetime, timedelta, timezone
import math

TZ = timezone(timedelta(hours=8))
FEATURE_SCHEMA = 'water-window-v1'
FEATURE_KEYS = ('intervalDeviation', 'instantDeviation', 'changeRate',
                'consecutiveHigh', 'deviceAlarm', 'valveClosed', 'coverage')


def parse_time(value):
    try:
        dt = datetime.fromisoformat(str(value).replace('Z', '+00:00'))
    except (TypeError, ValueError) as exc:
        raise ValueError('reportedAt must be offset ISO-8601') from exc
    if dt.tzinfo is None:
        raise ValueError('reportedAt must include timezone offset')
    return dt.astimezone(TZ)


def prepare_observations(observations, as_of=None):
    if not isinstance(observations, list) or len(observations) > 2800:
        raise ValueError('observations must be an array with at most 2800 entries')
    cutoff = parse_time(as_of) if as_of else None
    rows = {}
    for raw in observations:
        if not isinstance(raw, dict):
            raise ValueError('observation must be an object')
        at = parse_time(raw.get('reportedAt'))
        if cutoff and at > cutoff:
            continue
        row = {'id': raw.get('id'), 'at': at}
        for key in ('flow', 'total', 'temperature'):
            value = raw.get(key)
            if isinstance(value, bool) or not isinstance(value, (float, int)) or not math.isfinite(value):
                raise ValueError(f'{key} must be finite numeric')
            if key != 'temperature' and value < 0:
                raise ValueError(f'{key} must be nonnegative')
            row[key] = float(value)
        for key in ('valve', 'alarm'):
            if type(raw.get(key)) is not int or raw[key] not in (0, 1):
                raise ValueError(f'{key} must be 0 or 1')
            row[key] = raw[key]
        if at in rows:
            if any(rows[at][key] != row[key] for key in ('flow', 'total', 'temperature', 'valve', 'alarm')):
                raise ValueError('conflicting observations at same event time')
            continue
        rows[at] = row
    result = sorted(rows.values(), key=lambda x: x['at'])
    for previous, current in zip(result, result[1:]):
        if current['total'] < previous['total']:
            raise ValueError('cumulative total moved backwards')
    if result:
        earliest = result[-1]['at'] - timedelta(days=28)
        result = [r for r in result if r['at'] >= earliest]
    return result


def group(at):
    return (int(at.weekday() >= 5), at.hour // 4)


def intervals(rows):
    # At most one endpoint per 15-minute grid cell; repeated/5-minute probes
    # cannot manufacture three independent 15-minute windows.
    cells = {}
    for row in rows:
        cells[int(row['at'].timestamp()) // 900] = row
    selected = sorted(cells.items())
    result = []
    for (old_slot, prev), (slot, row) in zip(selected, selected[1:]):
        minutes = (row['at'] - prev['at']).total_seconds()/60
        valid = 0 < minutes <= 30 and slot == old_slot + 1
        result.append(dict(at=row['at'], slot=slot, flow=row['flow'],
                           intervalFlow=(row['total']-prev['total'])*60/minutes if valid else None,
                           valid=valid, minutes=minutes, alarm=row['alarm'], valve=row['valve']))
    return result


def feature_vector(features):
    values = [features.get(key) for key in FEATURE_KEYS]
    if any(v is None or not math.isfinite(v) for v in values):
        return None
    return values
