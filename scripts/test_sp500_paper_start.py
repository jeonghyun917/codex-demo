import unittest
from build_sp500_paper_start import approve_extension


class PaperStartTests(unittest.TestCase):
    def test_preserve_original_and_require_all_new_days(self):
        b=dict(open_price=10,high_price=11,low_price=9,close_price=10,volume=100)
        old={'2026-10-23':b};incoming={**old,'2026-10-26':b}
        self.assertEqual(list(approve_extension(old,incoming,['2026-10-26'])),['2026-10-26'])
        self.assertEqual(old,{'2026-10-23':b})
        with self.assertRaises(ValueError):approve_extension(old,incoming,['2026-10-27'])

    def test_overlap_revision_or_invalid_price_blocks(self):
        b=dict(open_price=10,high_price=11,low_price=9,close_price=10,volume=100)
        old={'2026-10-23':b}
        with self.assertRaises(ValueError):approve_extension(old,{'2026-10-23':dict(b,close_price=10.1)},[])
        with self.assertRaises(ValueError):approve_extension(old,{**old,'2026-10-26':dict(b,volume=0)},['2026-10-26'])


if __name__=='__main__':unittest.main()
