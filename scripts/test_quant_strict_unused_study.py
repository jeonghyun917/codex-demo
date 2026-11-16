import unittest
from decimal import Decimal
import quant_strict_unused_study as q


class StrictPreparationTest(unittest.TestCase):
    def test_provider_adjustment_is_undone_only_before_ex_date(self):
        events = [dict(day='2024-02-26',numerator='3',denominator='1')]
        self.assertEqual(q.nominal_close('50.80','2022-11-30',events), Decimal('152.40'))
        self.assertEqual(q.nominal_close('50.80','2024-02-26',events), Decimal('50.80'))

    def test_nonpositive_adjustments_are_rejected(self):
        with self.assertRaises(ValueError):
            q.nominal_close('10','2022-01-01',[dict(day='2023-01-01',numerator=0,denominator=1)])

    def test_same_day_filing_and_measurement_are_not_prior_information(self):
        row=dict(decision='2026-04-30',filedDate='2026-04-30',measurementDate='2026-01-01',
                 totalEconomicCommonShares=10,sharesPerQuotedUnit=1,
                 classes=[dict(value=10,unit='shares')])
        with self.assertRaises(ValueError):
            q.share_arithmetic(row)

    def test_ads_and_noneconomic_class_exclusion_are_explicit(self):
        row=dict(decision='2026-04-30',filedDate='2026-04-29',measurementDate='2025-12-31',
                 totalEconomicCommonShares=100,sharesPerQuotedUnit=4,
                 classes=[dict(value=100,unit='shares'),dict(value=1,unit='shares',economicSharesPerShare=0)])
        self.assertEqual(q.share_arithmetic(row),Decimal(25))

    def test_issued_or_weighted_share_substitution_cannot_match_a_false_sum(self):
        row=dict(decision='2026-04-30',filedDate='2026-04-29',measurementDate='2025-12-31',
                 totalEconomicCommonShares=120,sharesPerQuotedUnit=1,
                 classes=[dict(value=100,unit='shares')])
        with self.assertRaises(ValueError):
            q.share_arithmetic(row)

    def test_missing_holding_member_day_fails_without_subset_or_fill(self):
        with self.assertRaises(ValueError):
            q.check_complete_prices({'AAPL':[dict(day='2022-01-03',close='10')]},
                                    ['AAPL'],['2022-01-03','2022-01-04'])


if __name__=='__main__':
    unittest.main()
