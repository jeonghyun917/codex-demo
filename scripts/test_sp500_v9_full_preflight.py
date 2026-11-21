import unittest
from sp500_v9_full_preflight import valid_share, prior_observation, require_full_coverage


class FullV9PreflightTest(unittest.TestCase):
    def row(self,**kwargs):
        r=dict(symbol='A',cik='1',filed_at='2024-01-05',end_date='2023-12-31',
            start_date=None,value=100,unit='shares',concept='EntityCommonStockSharesOutstanding',
            accession_number='acc',raw_json={'accn':'acc','filed':'2024-01-05','end':'2023-12-31','val':100})
        r.update(kwargs);return r

    def test_original_instant_required(self):
        self.assertTrue(valid_share(self.row()))
        self.assertFalse(valid_share(self.row(concept='WeightedAverageNumberOfDilutedSharesOutstanding')))
        self.assertFalse(valid_share(self.row(start_date='2023-01-01')))
        self.assertFalse(valid_share(self.row(value=101)))
        self.assertFalse(valid_share(self.row(raw_json={})))

    def test_strict_publication_boundary(self):
        self.assertIsNone(prior_observation([self.row()], '2024-01-05'))
        self.assertIsNotNone(prior_observation([self.row()], '2024-01-06'))
        self.assertIsNone(prior_observation([self.row(end_date='2025-01-01')], '2024-01-06'))

    def test_missing_predictions_cannot_silently_reduce_universe(self):
        expected={('2025-01-02','A'),('2025-01-02','B')}
        with self.assertRaises(ValueError):require_full_coverage(expected,{('2025-01-02','A')})
        require_full_coverage(expected,expected)
        with self.assertRaises(ValueError):require_full_coverage(expected,expected|{('2025-01-03','A')})


if __name__=='__main__':unittest.main()
