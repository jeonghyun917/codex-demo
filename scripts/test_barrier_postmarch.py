import unittest
from barrier_postmarch import month_status


class CoverageTests(unittest.TestCase):
    def test_missing_month_is_not_cash_return(self):
        self.assertEqual(month_status('2026-04',None,0,0),'NO_SAVED_SIGNAL')

    def test_missing_prediction_never_falls_back(self):
        self.assertEqual(month_status('2026-08','2026-08-24',500,0),'MISSING_RESEARCH_CONTEXT')

    def test_incomplete_month_is_not_validation(self):
        self.assertEqual(month_status('2026-10','2026-10-18',500,500),'INCOMPLETE_MONTH_OR_IMMATURE')

    def test_supported_completed_month(self):
        self.assertEqual(month_status('2026-06','2026-06-28',500,500),'AVAILABLE_FOR_PRICE_CHECK')


if __name__=='__main__':
    unittest.main()
