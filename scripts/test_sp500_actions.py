import unittest
from build_sp500_actions import approved_rows


class ActionBoundaryTest(unittest.TestCase):
    def test_boundary_and_confirmation(self):
        bar=dict(open_price=10,high_price=11,low_price=9,close_price=10,volume=100)
        incoming={d:bar for d in ('2026-09-30','2026-10-01','2026-10-02')}
        selected,audit=approved_rows(incoming,{'2026-09-30':bar,'2026-10-01':bar},'2026-10-01',set(incoming))
        self.assertEqual(list(selected),['2026-10-01'])
        self.assertEqual(audit['unconfirmed'],1)

    def test_mismatch_invalid_and_rounding(self):
        bar=dict(open_price=10,high_price=11,low_price=9,close_price=10,volume=100)
        incoming={'2026-10-01':bar}
        for close,expected in [(10.004,1),(10.02,0)]:
            selected,_=approved_rows(incoming,{'2026-10-01':dict(bar,close_price=close)},'2026-10-01',set(incoming))
            self.assertEqual(len(selected),expected)
        selected,_=approved_rows({'2026-10-01':dict(bar,low_price=12)},incoming,'2026-10-01',set(incoming))
        self.assertFalse(selected)


if __name__=='__main__':unittest.main()
