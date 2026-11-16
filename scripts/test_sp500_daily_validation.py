import unittest
from sp500_daily_validation import exit_event, run_account


def bar(o=100,h=101,l=99,c=100):
    return dict(open=o,high=h,low=l,close=c)


class AccountTests(unittest.TestCase):
    def test_unresolved_corporate_action_blocks_execution(self):
        days=['2023-01-03','2023-01-04']
        prices={('A',days[1]):dict(bar(),execution_blocked=True)}
        r=run_account(days,prices,{days[0]:[dict(symbol='A',score=1)]})
        self.assertEqual(r['status'],'BLOCKED_MISSING_PRICE')
        self.assertIsNone(r['total_return_pct'])

    def test_barrier_priority_and_gaps(self):
        self.assertEqual(exit_event(bar(h=120,l=90),100,1),(95.,'SL'))
        self.assertEqual(exit_event(bar(o=80,h=90,l=75,c=85),100,1),(80,'SL_GAP'))
        self.assertAlmostEqual(exit_event(bar(o=120,h=125,l=119,c=121),100,1)[0],115)
        self.assertEqual(exit_event(bar(c=102,h=103),100,20),(102,'TIME'))
        self.assertIsNone(exit_event(bar(),100,19))

    def test_next_day_and_whole_shares(self):
        days=['2023-01-03','2023-01-04','2023-01-05']
        prices={('A',d):bar() for d in days}
        prices['A',days[1]]=bar(h=120)
        r=run_account(days,prices,{days[0]:[dict(symbol='A',score=1)]},fee=.001)
        self.assertEqual(r['fills'][0]['entry'],days[1])
        self.assertEqual(r['fills'][0]['shares'],99)
        self.assertEqual(r['fills'][0]['reason'],'TP')
        for event in r['events']:
            self.assertIn('fee_amount',dict(fee=.001,**event))
        self.assertTrue(all(e['cash']>=0 for e in r['equity']))

    def test_missing_order_is_not_silently_removed(self):
        days=['2023-01-03','2023-01-04']
        r=run_account(days,{}, {days[0]:[dict(symbol='A',score=1)]})
        self.assertEqual(r['status'],'BLOCKED_MISSING_PRICE')
        self.assertIsNone(r['total_return_pct'])

    def test_membership_removal_does_not_drop_holding(self):
        days=['2023-01-03','2023-01-04','2023-01-05']
        p={('A',d):bar() for d in days}
        r=run_account(days,p,{days[0]:[dict(symbol='A',score=1)]})
        self.assertEqual(r['equity'][-1]['positions'],1)
        self.assertEqual(r['status'],'OPEN_POSITIONS_AT_END')

    def test_zero_price_rejected(self):
        d=['2023-01-03','2023-01-04']
        r=run_account(d,{('A',d[1]):bar(o=0)}, {d[0]:[dict(symbol='A',score=1)]})
        self.assertEqual(r['status'],'BLOCKED_MISSING_PRICE')

    def test_zero_volume_not_assumed_executable(self):
        d=['2023-01-03','2023-01-04']
        r=run_account(d,{('A',d[1]):dict(bar(),volume=0)}, {d[0]:[dict(symbol='A',score=1)]})
        self.assertEqual(r['status'],'BLOCKED_MISSING_PRICE')


if __name__=='__main__':
    unittest.main()
