import importlib.util
import unittest
from datetime import date
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[3] / 'scripts/import_quant_research.py'


class ResearchImportTest(unittest.TestCase):
    def setUp(self):
        self.assertTrue(SCRIPT.exists(), 'Research importer is not implemented')
        spec = importlib.util.spec_from_file_location('research_import', SCRIPT)
        self.m = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.m)

    def test_namespace_cannot_replace_operating_index(self):
        for value in ('SP500', 'NASDAQ100', "SP500_FREE_20261018';DELETE"):
            with self.assertRaises(ValueError):
                self.m.validate_namespace(value)
        self.m.validate_namespace('SP500_FREE_20261018')

    def test_interval_end_is_inclusive_and_no_carry_forward(self):
        rows = [dict(symbol='A', start_date='2024-01-02', end_date='2024-01-04',
                     is_member='true', source='COMMUNITY_RECONSTRUCTION_CORRECTED')]
        result = self.m.expand_memberships(rows, [date(2024, 1, 2), date(2024, 1, 4), date(2024, 1, 5)])
        self.assertEqual([(date(2024, 1, 2), 'A', rows[0]['source']),
                          (date(2024, 1, 4), 'A', rows[0]['source'])], result)

    def test_overlapping_intervals_are_rejected_not_double_counted(self):
        row = dict(symbol='A', start_date='2024-01-02', end_date='2024-01-04',
                   is_member='true', source='COMMUNITY_RECONSTRUCTION_CORRECTED')
        with self.assertRaises(ValueError):
            self.m.expand_memberships([row, row], [date(2024, 1, 3)])

    def test_missing_market_session_not_zero_filled_or_bridged(self):
        days = [date(2024, 1, d) for d in (2, 3, 4, 5)]
        rows = self.m.benchmark_rows({days[0]: 100, days[2]: 110, days[3]: 121}, days)
        self.assertEqual([None, None, None, 10.0], [None if r[2] is None else round(r[2], 8) for r in rows])

    def test_returns_are_percent_not_fraction(self):
        days = [date(2024, 1, 2), date(2024, 1, 3)]
        self.assertAlmostEqual(5.0, self.m.benchmark_rows(dict(zip(days, [100, 105])), days)[1][2])

    def test_nonpositive_or_nonfinite_index_level_rejected(self):
        for value in (0, -1, float('nan'), float('inf')):
            with self.assertRaises(ValueError):
                self.m.benchmark_rows({date(2024, 1, 2): value}, [date(2024, 1, 2)])

    def test_sql_literals_escape_without_backslash_ambiguity(self):
        value = "O'Reilly\\x"
        self.assertEqual("CONVERT(0x" + value.encode().hex() + " USING utf8mb4)", self.m.sql_text(value))

    def test_context_copy_is_membership_bound_and_keeps_existing_sources(self):
        sql = self.m.market_context_sql('SP500_FREE_20261018', 'SP500')
        self.assertIn('JOIN stock_index_membership_snapshot m', sql)
        self.assertIn('m.snapshot_date=ms.snapshot_date', sql)
        self.assertIn('ms.shares_source,ms.market_cap_source,ms.calculated_at', sql)
        with self.assertRaises(ValueError):
            self.m.market_context_sql('SP500', 'SP500')


if __name__ == '__main__':
    unittest.main()
