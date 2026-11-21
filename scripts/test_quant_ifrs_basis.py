"""Independent period/date/basis regressions for the offline IFRS registry."""
import unittest
import tempfile
from pathlib import Path

import quant_ifrs_basis as basis


class IfrsBasisTests(unittest.TestCase):
    def test_comparative_year_uses_observation_dates_not_filing_fy(self):
        got = basis.normalized_period({'start': '2022-01-01', 'end': '2022-12-31',
                                       'fy': 2024, 'fp': 'FY', 'form': '20-F'})
        self.assertEqual(got, {'kind': 'ANNUAL', 'calendarYear': 2022, 'period': 'FY',
                              'fiscalYear': 2022, 'fiscalPeriod': 'FY', 'days': 365})

    def test_six_k_with_fy_label_can_be_standalone_q3(self):
        got = basis.normalized_period({'start': '2025-07-01', 'end': '2025-09-30',
                                       'fy': 2025, 'fp': 'FY', 'form': '6-K'})
        self.assertEqual(got['fiscalPeriod'], 'Q3')
        self.assertEqual(got['kind'], 'STANDALONE_QUARTER')

    def test_half_year_and_nine_month_not_quarter_even_when_fp_is_q2(self):
        for end, kind, period in [('2025-06-30', 'HALF_YEAR', 'H1'),
                                  ('2025-09-30', 'NINE_MONTH', '9M')]:
            got = basis.normalized_period({'start': '2025-01-01', 'end': end, 'fp': 'Q2'})
            self.assertEqual((got['kind'], got['period']), (kind, period))

    def test_instant_does_not_manufacture_income_statement_period(self):
        self.assertEqual(basis.normalized_period({'end': '2024-12-31'})['period'], None)

    def test_odd_range_is_not_approved_fiscal_quarter(self):
        got = basis.normalized_period({'start': '2025-02-01', 'end': '2025-04-30'})
        self.assertIsNone(got['fiscalPeriod'])
        self.assertIsNone(got['fiscalYear'])

    def test_reverse_dates_and_future_periods_rejected(self):
        for row in [{'start': '2025-10-01', 'end': '2025-09-30'},
                    {'end': '2025-09-30', 'filed': '2025-08-30'}]:
            with self.assertRaises(ValueError):
                basis.normalized_period(row)

    def test_filing_must_be_strictly_before_decision(self):
        self.assertFalse(basis.strictly_prior('2025-03-20', '2025-03-20'))
        self.assertFalse(basis.strictly_prior('2025-03-21', '2025-03-20'))
        self.assertTrue(basis.strictly_prior('2025-03-20', '2025-03-21'))

    def test_no_price_eps_before_share_proof_or_for_ads_or_fx(self):
        proof = {'GFS': {'kind': 'ORDINARY', 'quotedCurrency': 'USD',
                         'availableFrom': '2025-03-20'}}
        self.assertFalse(basis.price_eps_compatible('GFS', 'USD/shares', '2024-12-31', proof))
        self.assertTrue(basis.price_eps_compatible('GFS', 'USD/shares', '2025-03-21', proof))
        self.assertFalse(basis.price_eps_compatible('GFS', 'EUR/shares', '2025-03-21', proof))
        self.assertFalse(basis.price_eps_compatible('AZN', 'USD/shares', '2025-03-21', proof))

    def test_exact_quarter_requires_matching_frame_not_just_dates(self):
        row = {'start': '2025-07-01', 'end': '2025-09-30', 'frame': 'CY2025Q3'}
        period = basis.normalized_period(row)
        self.assertTrue(basis.quarter_frame_proven(row, period))
        for frame in (None, 'CY2024Q3', 'CY2025Q3I', 'CY2025Q2'):
            self.assertFalse(basis.quarter_frame_proven(dict(row, frame=frame), period))

    def test_half_year_with_quarter_frame_still_cannot_pass(self):
        row = {'start': '2025-01-01', 'end': '2025-06-30', 'frame': 'CY2025Q2'}
        self.assertFalse(basis.quarter_frame_proven(row, basis.normalized_period(row)))

    def test_calendar_leap_year_and_q4_dates(self):
        annual = basis.normalized_period({'start': '2024-01-01', 'end': '2024-12-31'})
        self.assertEqual(annual['days'], 366)
        q4 = basis.normalized_period({'start': '2024-10-01', 'end': '2024-12-31'})
        self.assertEqual((q4['kind'], q4['fiscalPeriod']), ('STANDALONE_QUARTER', 'Q4'))

    def test_existing_output_refused_before_input_read(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(FileExistsError):
                basis.build_registry(Path('nonexistent-input'), Path(directory))

    def test_actual_archive_registry_retains_507_rows_and_all_blocks(self):
        root = Path(__file__).resolve().parents[1] / 'tmp/quant-free-history-queue-primary-approved-20261031'
        with tempfile.TemporaryDirectory() as directory:
            result = basis.build_registry(root, Path(directory)/'new-output')
            self.assertEqual(result['observations'], 507)
            self.assertEqual(result['uniqueAccessions'], 16)
            self.assertEqual(result['historicalReferences'], 75)
            self.assertEqual(result['primaryInlineMatched'], 67)
            self.assertEqual(result['acceptedModelRows'], 0)
            self.assertEqual(result['priceEpsApprovedRows'], 0)
            self.assertEqual(result['issuers']['GFS']['exactQuarterFrameProven'], 62)
            self.assertEqual(result['issuers']['AZN']['exactQuarterFrameProven'], 15)
            self.assertEqual(result['issuers']['TRI']['exactQuarterFrameProven'], 12)


if __name__ == '__main__':
    unittest.main()
