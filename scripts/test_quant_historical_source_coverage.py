import unittest
from quant_historical_source_coverage import stage_missing, natural_key


def fact(**updates):
    row = dict(symbol='TEST', cik='123', taxonomy='us-gaap', concept='Revenues', unit='USD',
               fiscalYear=2024, fiscalPeriod='FY', form='10-K', filedAt='2025-02-01',
               startDate='2024-01-01', endDate='2024-12-31', accessionNumber='a',
               value='100', rawJson='{}')
    return row | updates


class HistoricalCoverageTest(unittest.TestCase):
    # Removing strict filing checks, collision detection, unit/start identity,
    # or missing-only filtering must break these hand-derived expectations.
    def test_only_missing_keys_are_staged_and_receipt_is_now(self):
        rows = [fact(), fact(concept='Assets', startDate='2024-12-31')]
        staged, conflicts = stage_missing(rows, [fact()], '2025-03-01', '2026-10-31T01:00:00Z')
        self.assertEqual(len(staged), 1)
        self.assertEqual(staged[0]['concept'], 'Assets')
        self.assertEqual(staged[0]['fetchedAt'], '2026-10-31T01:00:00Z')
        self.assertEqual(conflicts, [])

    def test_same_day_or_future_filing_is_not_historical_input(self):
        staged, _ = stage_missing([fact(filedAt='2025-03-01'), fact(filedAt='2025-03-02')], [],
                                  '2025-03-01', 'NOW')
        self.assertEqual(staged, [])

    def test_existing_value_conflict_is_quarantined_not_overwritten(self):
        staged, conflicts = stage_missing([fact(value='101')], [fact()], '2025-03-01', 'NOW')
        self.assertEqual(staged, [])
        self.assertEqual(len(conflicts), 1)
        self.assertEqual(conflicts[0]['existing']['value'], '100')

    def test_unit_and_duration_remain_distinct(self):
        self.assertNotEqual(natural_key(fact()), natural_key(fact(unit='EUR')))
        self.assertNotEqual(natural_key(fact()), natural_key(fact(startDate='2024-10-01')))
        rows, _ = stage_missing([fact(), fact(unit='EUR')], [], '2025-03-01', 'NOW')
        self.assertEqual(len(rows), 2)

    def test_duplicate_source_conflict_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'conflicting archived'):
            stage_missing([fact(), fact(value='9')], [], '2025-03-01', 'NOW')

    def test_nonfinite_or_wrong_period_is_rejected(self):
        with self.assertRaises(ValueError):
            stage_missing([fact(value='NaN')], [], '2025-03-01', 'NOW')
        with self.assertRaises(ValueError):
            stage_missing([fact(startDate='2025-01-01')], [], '2025-03-01', 'NOW')

    def test_cik_padding_is_not_a_source_conflict(self):
        rows, conflicts = stage_missing([fact(cik='0000000123')], [fact()], '2025-03-01', 'NOW')
        self.assertEqual(rows, [])
        self.assertEqual(conflicts, [])


if __name__ == '__main__':
    unittest.main()
