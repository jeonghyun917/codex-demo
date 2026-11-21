import copy
import importlib
import unittest

from test_quant_full_financial_study import old_row

try: study=importlib.import_module('quant_v9_followup_replay')
except ModuleNotFoundError: study=None


class ReplayParityTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(study,'Follow-up replay preparation missing')
        self.before={'prices':[1,2], 'financialsBySymbol':{'A':[old_row(revenue=None)]}}

    def test_only_null_revenue_and_its_evidence_may_change(self):
        after=copy.deepcopy(self.before)
        after['financialsBySymbol']['A'][0].update(revenue=120.,rawJson='{"primary":true}')
        study.assert_overlay_parity(self.before,after)

    def test_prices_eps_identity_existing_revenue_or_currency_change_is_rejected(self):
        for name,value in [('epsDiluted',9.),('filedAt','2020-01-01'),('currency','EUR')]:
            bad=copy.deepcopy(self.before);bad['financialsBySymbol']['A'][0][name]=value
            with self.assertRaises(ValueError):study.assert_overlay_parity(self.before,bad)
        bad=copy.deepcopy(self.before);bad['prices']=[9]
        with self.assertRaises(ValueError):study.assert_overlay_parity(self.before,bad)
        bad=copy.deepcopy(self.before);bad['financialsBySymbol']['A']=[]
        with self.assertRaises(ValueError):study.assert_overlay_parity(self.before,bad)
        self.before['financialsBySymbol']['A'][0]['revenue']=100.
        bad=copy.deepcopy(self.before);bad['financialsBySymbol']['A'][0]['revenue']=120.
        with self.assertRaises(ValueError):study.assert_overlay_parity(self.before,bad)


if __name__=='__main__':unittest.main()
