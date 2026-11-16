import copy
import hashlib
import importlib
import sys
import unittest
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'scripts'))


class RobustResearchTest(unittest.TestCase):
    def module(self):
        try:
            return importlib.import_module('quant_20d_robust_research')
        except ModuleNotFoundError:
            self.fail('Robust research implementation is missing')

    def test_median_and_huber_resist_a_single_extreme_label(self):
        m = self.module()
        for method in ('quantile', 'huber'):
            fitted = m.fit_robust([[0]] * 5, [1, 1, 1, 1, 100], method)
            prediction = float(m.predict_robust(fitted, [[99]])[0])
            self.assertLess(abs(prediction - 1), 0.1)

    def test_scaling_is_fitted_only_from_supplied_training_rows(self):
        m = self.module()
        fitted = m.fit_robust([[0, 7], [2, 7], [4, 7]], [1, 3, 5], 'quantile')
        self.assertEqual(fitted['mean'], [2.0, 7.0])
        self.assertEqual(fitted['scale'][1], 1)
        self.assertTrue(np.isfinite(m.predict_robust(fitted, [[1000000, 7]])).all())

    def test_nonfinite_labels_and_mismatched_shapes_are_rejected(self):
        m = self.module()
        for x, y in [([[1]], [float('nan')]), ([[1], [2]], [1])]:
            with self.assertRaises(ValueError):
                m.fit_robust(x, y, 'quantile')

    def panel(self):
        m = self.module()
        rows = []
        for month in range(1, 11):
            for i, symbol in enumerate(('A', 'B')):
                rows.append(dict(day=f'2024-{month:02}-01', entry=f'2024-{month:02}-02',
                                 exit=f'2024-{month:02}-28', symbol=symbol,
                                 actual=month+i, excess=month/2+i, price=[month, i, 2, 3, 4],
                                 forecasts={m.V9: dict(absolute=month/3, excess=month/4)}))
        return rows

    def test_current_future_and_boundary_exit_labels_cannot_change_predictions(self):
        m = self.module()
        rows = self.panel()
        boundary = copy.deepcopy(rows[0])
        boundary.update(day='2024-01-15', exit='2024-09-01', symbol='C', actual=9999, excess=9999)
        rows.append(boundary)
        before = m.predict_fold(rows, '2024-09-01')
        for row in rows:
            if row['exit'] >= '2024-09-01':
                row['actual'] = -100000
                row['excess'] = 100000
        after = m.predict_fold(rows, '2024-09-01')
        self.assertEqual(before['fits'], after['fits'])
        self.assertEqual([p['predicted'] for p in before['predictions']],
                         [p['predicted'] for p in after['predictions']])
        for fit in before['fits']:
            self.assertEqual(len(fit['trainingDates']), 8)
            self.assertLess(fit['maximumTrainingExit'], '2024-09-01')

    def test_minimum_training_dates_are_not_replaced_by_row_count(self):
        m = self.module()
        rows = self.panel()
        self.assertIsNone(m.predict_fold(rows, '2024-08-01'))

    def test_nonoverlap_subset_is_chronological_and_outcome_blind(self):
        m = self.module()
        rows = [dict(day='2024-01-01', exit='2024-02-01'),
                dict(day='2024-02-01', exit='2024-03-01'),
                dict(day='2024-03-02', exit='2024-04-01')]
        self.assertEqual(m.nonoverlap_dates(rows), ['2024-01-01', '2024-03-02'])

    def test_historical_source_recovery_requires_exact_recorded_bytes(self):
        m = self.module()
        source = Path('tmp/quant-20d-robust-research-v2-20261031/historical-v9-source.txt').read_bytes()
        recovered = m.recover_historical_v9(source)
        self.assertEqual(hashlib.sha256(recovered).hexdigest(),
                         'ca0bfbb93248a4b71c82b4e6df3b8f06b9a67fd5fcdab09812712c32b6b79c63')
        with self.assertRaises(ValueError):
            m.recover_historical_v9(source + b'\n// unrelated change\n')

    def test_windows_report_paths_preserve_transitive_hashes_and_snapshot_resolution(self):
        m = self.module()
        hashes = m.transitive_hashes(
            {r'tmp\quant-v9-historical-market-final-20261031\result.json': 'report-hash'},
            Path('historical-source-fixture.txt'))
        self.assertEqual(hashes['historical-source-fixture.txt'], m.HISTORICAL_V9_SHA)
        self.assertNotIn(m.V9_SOURCE, hashes)
        self.assertGreater(len(hashes), 2)

    def test_constant_decimal_prediction_has_no_rank_not_nan(self):
        m = self.module()
        values = m.measures([dict(predicted=.1, actual=i) for i in range(101)])
        self.assertIsNone(values['spearman'])


if __name__ == '__main__':
    unittest.main()
