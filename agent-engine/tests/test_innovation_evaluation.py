import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


class EvaluationTest(unittest.TestCase):
    def test_event_metrics_merge_and_misses(self):
        from innovation.evaluate import event_metrics
        truth = [dict(id='a', meterId=1, start=60, end=120, kind='small_flow'),
                 dict(id='b', meterId=1, start=1500, end=1560, kind='small_flow')]
        alerts = [dict(meterId=1, time=t) for t in [75, 90, 3000]]
        result = event_metrics(truth, alerts, meter_ids=[1], start=0, end=4320)
        self.assertEqual((result['tp'], result['fp'], result['fn']), (1, 1, 1))
        self.assertEqual(result['recall'], 0.5)
        self.assertEqual(result['normalMeterDays'], 1)
        self.assertEqual(result['falsePositiveMeterDayRate'], 1)
        self.assertEqual(result['missedEventIds'], ['b'])


if __name__ == '__main__':
    unittest.main()
