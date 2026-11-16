import unittest
from sp500_forward_snapshot import mature_decisions, chart_url


class ForwardSnapshotTest(unittest.TestCase):
    def test_twenty_future_sessions_required(self):
        days=[f'2026-11-{i:02}' for i in range(1,23)]
        self.assertEqual(mature_decisions(days[:20],days[0]),[])
        self.assertEqual(mature_decisions(days[:21],days[0]),[days[0]])
        self.assertEqual(mature_decisions(days,days[1]),[days[1]])

    def test_url_has_exclusive_cutoff(self):
        self.assertIn('period2=1793232000',chart_url('^GSPC'))
        self.assertIn('%5EGSPC',chart_url('^GSPC'))


if __name__=='__main__':unittest.main()
