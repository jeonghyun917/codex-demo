import unittest
from build_sp500_extension import same_ohlc,apply_change
from verify_sp500_daily_dataset import expected_next_session


class ExtensionTests(unittest.TestCase):
    def test_last_calendar_day_has_no_invented_next_session(self):
        days=['2026-10-22','2026-10-23']
        self.assertEqual(expected_next_session(days,days[0]),days[1])
        self.assertEqual(expected_next_session(days,days[1]),'')

    def test_volume_revision_is_not_price_revision(self):
        a=dict(open_price=10,high_price=11,low_price=9,close_price=10,volume=100)
        self.assertTrue(same_ohlc(a,dict(a,volume=101)))
        self.assertFalse(same_ohlc(a,dict(a,close_price=10.1)))

    def test_change_requires_existing_removal_and_new_addition(self):
        s={'A','B'}
        self.assertEqual(apply_change(s,['C'],['B']),{'A','C'})
        self.assertEqual(s,{'A','B'})
        with self.assertRaises(ValueError):apply_change(s,['A'],[])
        with self.assertRaises(ValueError):apply_change(s,[],['C'])


if __name__=='__main__':unittest.main()
