import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


class ScenarioTest(unittest.TestCase):
    def test_seed_and_frequency_independent_integral(self):
        from innovation.scenarios import Trajectory
        a, b = Trajectory(0, 7, days=3), Trajectory(0, 7, days=3)
        self.assertEqual(a.sample(15), b.sample(15))
        self.assertEqual(a.observe(1440)['total'], a.sample(5)[288]['total'])
        self.assertEqual(a.sample(15)[96]['total'], a.sample(5)[288]['total'])

    def test_truth_never_appears_in_observations(self):
        from innovation.scenarios import Trajectory, SCENARIOS
        self.assertEqual(len(SCENARIOS), 8)
        rows = Trajectory(2, 11).sample(15)
        self.assertEqual(set(rows[0]), {'id', 'reportedAt', 'flow', 'total', 'temperature', 'valve', 'alarm'})

    def test_lab_database_guard(self):
        from innovation.scenarios import validate_lab_database
        self.assertEqual(validate_lab_database('water_meter_innovation_lab'), 'water_meter_innovation_lab')
        with self.assertRaises(ValueError):
            validate_lab_database('water_meter_db')


if __name__ == '__main__':
    unittest.main()
