import unittest
from datetime import date, timedelta

import numpy as np
from barrier_probability import causal_features, split_history, ranked_entries, target_label, fit_predict, FEATURES


def observation(day, maturity, **kwargs):
    return dict(date=day, maturity=maturity, **kwargs)


class ProbabilityResearchTests(unittest.TestCase):
    def test_time_expiry_profit_is_not_target_hit(self):
        self.assertEqual(target_label({'reason': 'TIME', 'net': 10}), 0)
        self.assertEqual(target_label({'reason': 'TP', 'net': 14.8}), 1)
        self.assertEqual(target_label({'reason': 'SL_GAP', 'net': -12}), 0)

    def test_maturity_purged_before_fit_and_calibration(self):
        rows = [observation('2024-01-01', '2024-01-30'),
                observation('2024-02-01', '2024-03-01'),
                observation('2024-03-01', '2024-03-29'),
                observation('2024-04-01', '2024-04-30'),
                observation('2024-05-01', '2024-06-01')]
        fit, cal = split_history(rows, '2024-06-01', calibration_dates=2)
        self.assertEqual([r['date'] for r in fit], ['2024-01-01'])
        self.assertEqual([r['date'] for r in cal], ['2024-03-01', '2024-04-01'])

    def test_features_ignore_future_bars(self):
        bars = []
        for i in range(30):
            close = 100+i
            bars.append(dict(date=str(date(2024, 1, 1)+timedelta(days=i)),
                             open=close, high=close+1, low=close-1, close=close, source='x'))
        cutoff = bars[24]['date']
        original = causal_features(bars, cutoff)
        bars[25]['close'] = 999999
        self.assertEqual(original, causal_features(bars, cutoff))

    def test_rank_ignores_future_outcomes_and_prevents_overlap(self):
        rows = [dict(date='2025-01-01', symbol='A', entry='2025-01-02', exit='2025-02-10', score=9),
                dict(date='2025-01-01', symbol='B', entry='2025-01-02', exit='2025-01-20', score=5),
                dict(date='2025-02-01', symbol='A', entry='2025-02-03', exit='2025-02-10', score=10),
                dict(date='2025-02-01', symbol='B', entry='2025-02-03', exit='2025-02-20', score=3)]
        selected = ranked_entries(rows, 'score', fraction=.5)
        self.assertEqual([(r['date'],r['symbol']) for r in selected],
                         [('2025-01-01','A'),('2025-02-01','B')])

    def test_prediction_does_not_read_test_labels(self):
        rows = [{**{k:float((i+j)%7) for j,k in enumerate(FEATURES)}, 'label':i%2}
                for i in range(100)]
        fit, cal, current = rows[:60], rows[60:90], rows[90:]
        before, _ = fit_predict(fit, cal, current)
        for row in current:
            row['label'] = 1-row['label']
            row['net'] = 99999
        after, _ = fit_predict(fit, cal, current)
        np.testing.assert_array_equal(before, after)


if __name__ == '__main__':
    unittest.main()
