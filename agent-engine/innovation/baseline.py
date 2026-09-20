"""Personal weekday/weekend x four-hour robust baseline snapshots."""
from datetime import timedelta
from hashlib import sha256
from statistics import median
from collections import defaultdict
from .features import group

POLICY_VERSION = 'personal-mad-v1'


def group_snapshots(windows, at):
    """Freeze every comparable time group for post-work-order verification."""
    past=[w for w in windows if w['valid'] and at-timedelta(days=28)<=w['at']<at]
    buckets=defaultdict(list)
    for w in past:buckets[group(w['at'])].append(w['intervalFlow'])
    ready=len({w['at'].date() for w in past})>=14 and len(past)>=200
    result={}
    for (weekend,slot),values in sorted(buckets.items()):
        center=median(values);mad=median(abs(v-center) for v in values)
        result[f'{weekend}:{slot}']=dict(median=center,mad=mad,upper=center+max(.05,6*1.4826*mad),count=len(values),ready=ready and len(values)>=8)
    return result


def build_baseline(windows, at):
    past = [w for w in windows if w['valid'] and at - timedelta(days=28) <= w['at'] < at]
    days = len({w['at'].date() for w in past})
    same = [w for w in past if group(w['at']) == group(at)]
    values = sorted(w['intervalFlow'] for w in same)
    center = median(values) if values else None
    mad = median([abs(v-center) for v in values]) if values else None
    token = '|'.join(f"{w['at'].isoformat()}:{w['intervalFlow']:.8f}" for w in same)
    result = dict(median=center, mad=mad, count=len(values), validDays=days,
                  validIntervals=len(past), version=POLICY_VERSION+'-'+sha256(token.encode()).hexdigest()[:12],
                  trainingEnd=past[-1]['at'].isoformat() if past else None,
                  p95=values[min(len(values)-1, int(len(values)*.95))] if values else None,
                  upper=center + max(0.05, 6*1.4826*mad) if values else None,
                  ready=days >= 14 and len(past) >= 200 and len(values) >= 8)
    return result


def high(value, baseline):
    return baseline['ready'] and value is not None and value > baseline['median'] + max(0.05, 6*1.4826*baseline['mad'])


def deviation(value, baseline):
    if value is None or not baseline['ready']:
        return None
    return (value-baseline['median'])/max(0.01, 1.4826*baseline['mad'])
