import unittest
from sp500_v9_recovery_coverage import select_cover


class CoverageTest(unittest.TestCase):
    def test_only_strictly_prior_complete_measurements(self):
        old = dict(status='PARSED', filedAt='2025-01-01', facts=[dict(measurementDate='2024-12-28', value=10)])
        same = dict(status='PARSED', filedAt='2025-01-02', facts=[dict(measurementDate='2024-12-30', value=12)])
        future = dict(status='PARSED', filedAt='2024-12-31', facts=[dict(measurementDate='2025-01-03', value=99)])
        self.assertIs(select_cover([old, same, future], '2025-01-02'), old)

    def test_failed_or_empty_or_all_zero_is_not_coverage(self):
        rows = [dict(status='REVIEW_REQUIRED', filedAt='2024-01-01', facts=[dict(measurementDate='2023-01-01', value=10)]),
                dict(status='PARSED', filedAt='2024-01-01', facts=[]),
                dict(status='PARSED', filedAt='2024-01-01', facts=[dict(measurementDate='2023-01-01', value=0)])]
        self.assertIsNone(select_cover(rows, '2025-01-01'))

    def test_latest_filing_selected_without_class_summing(self):
        a = dict(status='PARSED', filedAt='2024-12-01', facts=[dict(measurementDate='2024-11-01', value=10)])
        b = dict(status='PARSED', filedAt='2024-12-31', facts=[dict(measurementDate='2024-12-28', value=10), dict(measurementDate='2024-12-28', value=50)])
        self.assertIs(select_cover([b, a], '2025-01-01'), b)


if __name__ == '__main__':
    unittest.main()
