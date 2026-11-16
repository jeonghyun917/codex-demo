import unittest
import quant_long_history_extended_study as q


class ExtendedHistoryTest(unittest.TestCase):
    def test_disney_old_issuer_is_not_carried_across_merger_share_issue(self):
        self.assertTrue(q.issuer_admitted('DIS','2019-03-19'))
        self.assertFalse(q.issuer_admitted('DIS','2019-03-20'))
        self.assertFalse(q.issuer_admitted('DIS','2019-05-08'))
        self.assertTrue(q.issuer_admitted('DIS','2019-05-09'))

    def test_only_dow_can_be_absent_for_pre_ipo_price_history(self):
        prices=[str(x) for x in range(80)]
        self.assertTrue(q.price_admitted('DOW',80))
        self.assertFalse(q.price_admitted('DOW',79))
        with self.assertRaises(ValueError):q.price_admitted('AAPL',79)

    def test_original_minimum_calibration_threshold_is_preserved(self):
        self.assertFalse(q.bucket_ready(199))
        self.assertTrue(q.bucket_ready(200))


if __name__=='__main__':unittest.main()
