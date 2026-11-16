import copy
import importlib
import unittest

try:q=importlib.import_module('quant_full_financial_complete')
except ModuleNotFoundError:q=None

class CompleteStudyTest(unittest.TestCase):
    def setUp(self):self.assertIsNotNone(q,'Prespecified balance-complete study adapter missing')

    def test_only_four_balance_fields_and_evidence_can_change(self):
        original={'A':[dict(symbol='A',cik='1',accessionNumber='x',filedAt='2019-01-01',form='10-K',startDate='2018-01-01',endDate='2018-12-31',periodDays=365,currency='USD',revenue=10.,assets=None,rawJson='{}')]}
        updated=copy.deepcopy(original);updated['A'][0]['assets']=20.;updated['A'][0]['rawJson']='{"balanceCompletion":{}}'
        q.assert_completion_only(original,updated)
        updated['A'][0]['revenue']=11.
        with self.assertRaises(ValueError):q.assert_completion_only(original,updated)

    def test_price_and_target_schedule_cannot_change_for_second_replay(self):
        original=dict(namespace='research',testDecisions=[],pricesBySymbol={'A':[1]},financialsBySymbol={},inputHashes={})
        updated=copy.deepcopy(original);updated['financialsBySymbol']={'A':[1]};updated['inputHashes']={'new':'x'}
        q.assert_input_parity(original,updated)
        updated['pricesBySymbol']['A']=[2]
        with self.assertRaises(ValueError):q.assert_input_parity(original,updated)

if __name__=='__main__':unittest.main()
