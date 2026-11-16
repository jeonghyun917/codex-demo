import unittest
from barrier_research import simulate, select


def bar(day, op, hi, lo, cl):
    return dict(date=day, open=op, high=hi, low=lo, close=cl, source='test')


class BarrierTests(unittest.TestCase):
    def test_take_profit(self):
        r = simulate([bar('2024-01-02', 100, 116, 99, 114)], 1)
        self.assertAlmostEqual(r['gross'], 15)
        self.assertEqual(r['reason'], 'TP')

    def test_same_day_ambiguous_stop_first(self):
        r = simulate([bar('2024-01-02', 100, 116, 94, 110)], 1)
        self.assertAlmostEqual(r['gross'], -5)
        self.assertTrue(r['ambiguous'])

    def test_gap_below_stop(self):
        r = simulate([bar('2024-01-02', 100, 102, 99, 101),
                      bar('2024-01-03', 90, 94, 88, 92)], 2)
        self.assertAlmostEqual(r['gross'], -10)

    def test_timeout_and_cost(self):
        r = simulate([bar('2024-01-02', 100, 104, 99, 103)], 1)
        self.assertAlmostEqual(r['net'], 2.8)
        self.assertEqual(r['reason'], 'TIME')

    def test_incomplete_window_excluded_even_if_early_hit(self):
        self.assertIsNone(simulate([bar('2024-01-02', 100, 120, 99, 118)], 2))

    def test_invalid_ohlc_excluded(self):
        self.assertIsNone(simulate([bar('2024-01-02', 100, 90, 99, 101)], 1))

    def test_favorable_gap_uses_limit_price(self):
        r = simulate([bar('2024-01-02', 100, 102, 99, 101),
                      bar('2024-01-03', 120, 125, 118, 122)], 2)
        self.assertAlmostEqual(r['gross'], 15)

    def test_no_duplicate_positions(self):
        rows = [dict(symbol='A', date='2025-01-01', entry='2025-01-02', exit='2025-02-10'),
                dict(symbol='A', date='2025-02-01', entry='2025-02-03', exit='2025-02-28')]
        self.assertEqual(len(select(rows, {}, 'test')), 1)

    def test_training_excludes_outcomes_crossing_cutoff(self):
        rows = [dict(symbol='A', date='2024-12-01', entry='2024-12-02', exit='2025-01-02')]
        self.assertEqual(select(rows, {}, 'train'), [])
        self.assertEqual(select(rows, {}, 'test'), [])


if __name__ == '__main__':
    unittest.main()
