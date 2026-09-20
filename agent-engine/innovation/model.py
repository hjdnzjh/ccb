"""Rules remain authoritative; a configured forest only adds a shadow score."""
import os
import json
from hashlib import sha256
from pathlib import Path
from .baseline import POLICY_VERSION, build_baseline, group_snapshots, high, deviation
from .features import FEATURE_SCHEMA, FEATURE_KEYS, prepare_observations, intervals, feature_vector

HIGH_FLOW = 10.0


def diagnose(payload, scorer=None):
    if not isinstance(payload, dict) or not isinstance(payload.get('meterId'), (str, int)):
        raise ValueError('meterId is required')
    rows = prepare_observations(payload.get('observations'))
    if not rows:
        return dict(schemaVersion='diagnosis-v1', decision='insufficient_data', anomalyScore=None,
                    scoreMeaning='anomaly_score_not_probability', reasonCodes=['no_observations'],
                    baseline=dict(median=None, mad=None, count=0, version=POLICY_VERSION),
                    validSamples=0, expectedSamples=3, features={}, modelVersion='rules-only',
                    fallbackReason='no_observations', windowEnd=None)
    windows = intervals(rows)
    latest = rows[-1]
    # Exclude the current three windows from their comparison history.
    at = windows[-3]['at'] if len(windows) >= 3 else latest['at']
    training = [w for w in windows if w['at'] < at]
    baseline = build_baseline(training, latest['at'])
    baseline['groups'] = group_snapshots(training,latest['at'])
    baseline['profileVersion'] = sha256(json.dumps(baseline['groups'],sort_keys=True).encode()).hexdigest()[:16]
    recent = windows[-3:]
    flags = [high(w['intervalFlow'], build_baseline(training, w['at'])) for w in recent]
    complete = len(recent) == 3 and all(w['valid'] for w in recent)
    valid = sum(w['valid'] for w in recent)
    persistent = complete and all(flags)
    current = windows[-1]['intervalFlow'] if windows else None
    reasons = []
    if latest['alarm']:
        reasons.append('device_alarm')
    if latest['flow'] > HIGH_FLOW:
        reasons.append('high_flow')
    if latest['valve'] == 0 and latest['flow'] > 0:
        reasons.append('flow_while_valve_closed')
    if latest['temperature'] < 0 or latest['temperature'] > 60:
        reasons.append('temperature_abnormal')
    if persistent:
        reasons.append('persistent_flow_above_personal_baseline')
    urgent = bool(reasons)
    if not baseline['ready']:
        reasons.append('cold_start' if baseline['validDays'] < 14 or baseline['validIntervals'] < 200 else 'insufficient_group_history')
    if not complete:
        reasons.append('missing_window')
    previous = windows[-2]['intervalFlow'] if len(windows) > 1 else None
    consecutive = 0
    for flag in reversed(flags):
        if not flag:
            break
        consecutive += 1
    features = dict(intervalFlow=current, instantaneousFlow=latest['flow'],
                    intervalDeviation=deviation(current, baseline),
                    instantDeviation=deviation(latest['flow'], baseline),
                    changeRate=(current-previous)/max(.01, abs(previous)) if current is not None and previous is not None else None,
                    consecutiveHigh=consecutive, deviceAlarm=latest['alarm'],
                    valveClosed=int(latest['valve'] == 0), coverage=valid/3,
                    featureSchema=FEATURE_SCHEMA)
    decision = 'needs_review' if urgent else ('normal' if complete and baseline['ready'] else 'insufficient_data')
    fallback = 'model_not_configured'
    score, version = None, 'rules-only'
    vector = feature_vector(features)
    if scorer is not None and vector is not None:
        if payload.get('modelVersion') not in (None, scorer.version):
            fallback = 'model_version_mismatch'
        else:
            try:
                score = float(scorer.score([vector])[0])
                version, fallback = scorer.version, None
            except Exception:
                fallback = 'model_unavailable'
    elif vector is None:
        fallback = 'insufficient_features'
    elif payload.get('modelVersion') not in (None, 'rules-only') or os.environ.get('INNOVATION_MODEL_VERSION'):
        fallback = 'model_unavailable'
    return dict(schemaVersion='diagnosis-v1', decision=decision, anomalyScore=score,
                scoreMeaning='anomaly_score_not_probability', reasonCodes=reasons,
                baseline=baseline, baselineVersion=baseline['version'], validSamples=valid,
                expectedSamples=3, features=features, modelVersion=version,
                policyVersion=POLICY_VERSION, fallbackReason=fallback,
                windowEnd=latest['at'].isoformat())


class CandidateForest:
    def __init__(self, seed=20260920, version='isolation-shadow-v1'):
        from sklearn.ensemble import IsolationForest
        self.version = version
        self.forest = IsolationForest(n_estimators=200, max_samples=256,
                                      contamination='auto', random_state=seed, n_jobs=1)

    def fit(self, vectors):
        if len(vectors) < 256:
            raise ValueError('model requires at least 256 training windows')
        self.forest.fit(vectors)
        return self

    def score(self, vectors):
        # sklearn score_samples is lower for anomalies; negate, never sigmoid.
        return -self.forest.score_samples(vectors)

    def save(self, path):
        import joblib
        import sklearn
        joblib.dump(dict(version=self.version, schema=FEATURE_SCHEMA, keys=FEATURE_KEYS,
                         sklearn=sklearn.__version__, forest=self.forest), path)

    @classmethod
    def load(cls, path):
        import joblib
        import sklearn
        data = joblib.load(path)  # Only trusted server-selected local paths.
        if data['schema'] != FEATURE_SCHEMA or data['keys'] != FEATURE_KEYS or data['sklearn'] != sklearn.__version__:
            raise ValueError('model schema or dependency version mismatch')
        result = cls(version=data['version'])
        result.forest = data['forest']
        return result


def configured_scorer():
    # No model path can be selected by an HTTP caller.
    version = os.environ.get('INNOVATION_MODEL_VERSION', '')
    if not version or not version.replace('-', '').replace('_', '').isalnum():
        return None
    directory = Path(__file__).resolve().parents[2] / 'logs' / 'innovation' / 'models'
    try:
        return CandidateForest.load(directory / (version + '.joblib'))
    except (ImportError, OSError, ValueError, KeyError):
        return None
