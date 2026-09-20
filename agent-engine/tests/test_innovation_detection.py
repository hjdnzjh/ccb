import os
import sys
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from api.routes import create_app


def history(days=16, flow=0.02):
    start = datetime(2026, 1, 1, tzinfo=timezone(timedelta(hours=8)))
    return [dict(id=i, reportedAt=(start + timedelta(minutes=15*i)).isoformat(),
                 flow=flow, total=round(100 + flow*i/4, 6), temperature=20,
                 valve=1, alarm=0) for i in range(days*96 + 1)]


class DiagnosisTest(unittest.TestCase):
    def score(self, rows, **kwargs):
        from innovation.model import diagnose
        return diagnose(dict(meterId=1, observations=rows, **kwargs))

    def test_cold_start_has_no_fabricated_score(self):
        result = self.score(history(2))
        self.assertEqual(result['decision'], 'insufficient_data')
        self.assertIsNone(result['anomalyScore'])
        self.assertIn('cold_start', result['reasonCodes'])

    def test_verification_snapshot_contains_all_time_groups(self):
        result=self.score(history())
        self.assertEqual(len(result['baseline']['groups']),12)
        self.assertTrue(all(g['ready'] for g in result['baseline']['groups'].values()))
        self.assertTrue(all(abs(g['upper']-.07)<.001 for g in result['baseline']['groups'].values()))

    def test_normal_night_business_uses_own_baseline(self):
        result = self.score(history(flow=0.7))
        self.assertEqual(result['decision'], 'normal')
        self.assertAlmostEqual(result['baseline']['median'], 0.7)

    def test_three_distinct_windows_required(self):
        rows = history()
        for j in range(1, 4):
            rows[-j]['flow'] = 0.15
            rows[-j]['total'] += 0.13*(4-j)/4
        self.assertEqual(self.score(rows[:-1])['decision'], 'normal')
        result = self.score(rows)
        self.assertEqual(result['decision'], 'needs_review')
        self.assertIn('persistent_flow_above_personal_baseline', result['reasonCodes'])

    def test_missing_window_is_not_zero_or_persistence(self):
        rows = history()
        rows = rows[:-4] + rows[-1:]
        result = self.score(rows)
        self.assertEqual(result['decision'], 'insufficient_data')
        self.assertIsNone(result['features']['intervalFlow'])

    def test_future_observations_do_not_affect_asof_features(self):
        from innovation.features import prepare_observations
        rows = history(2)
        self.assertEqual(prepare_observations(rows, rows[30]['reportedAt']),
                         prepare_observations(rows[:31], rows[30]['reportedAt']))

    def test_duplicate_cannot_increase_valid_samples(self):
        rows = history()
        self.assertEqual(self.score(rows)['validSamples'], self.score(rows + rows[-2:])['validSamples'])

    def test_alarm_overrides_cold_start(self):
        rows = history(1)
        rows[-1]['alarm'] = 1
        self.assertEqual(self.score(rows)['decision'], 'needs_review')

    def test_contract_and_gateway_auth(self):
        with patch.dict(os.environ, {'AGENT_INTERNAL_TOKEN': 'test-secret'}):
            client = create_app().test_client()
            url = '/api/internal/diagnosis/score'
            self.assertEqual(client.post(url, json={}).status_code, 401)
            response = client.post(url, json={'meterId': 1, 'observations': history(1)},
                                   headers={'X-Agent-Token': 'test-secret'})
            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.json['scoreMeaning'], 'anomaly_score_not_probability')
            self.assertEqual(client.post(url, json={'meterId': 1, 'observations': history(30)},
                             headers={'X-Agent-Token': 'test-secret'}).status_code, 400)

    def test_rejects_naive_time_nonfinite_and_conflicts(self):
        for change in ({'reportedAt': '2026-01-01T00:00:00'}, {'flow': float('nan')}, {'total': -1}):
            rows = history(1)
            rows[-1].update(change)
            with self.assertRaises(ValueError):
                self.score(rows)
        rows = history(1)
        with self.assertRaises(ValueError):
            self.score(rows + [dict(rows[-1], flow=1)])


if __name__ == '__main__':
    unittest.main()
