import unittest
import quant_long_history_corrected_study as q

class OlderActionCorrectionTest(unittest.TestCase):
    def test_separation_is_price_only_not_an_ordinary_share_split(self):
        result={'meta':{'symbol':'HON'},'events':{'splits':{'a':{'date':1538400600,'numerator':1011,'denominator':1000}}}}
        rows=q.older_hon_events(result,'source','hash')
        self.assertEqual(rows[0]['day'],'2018-10-01')
        self.assertEqual(rows[0]['kind'],'SEPARATION_RESEARCH')
    def test_wrong_issuer_cannot_supply_adjustment(self):
        with self.assertRaises(ValueError):q.older_hon_events({'meta':{'symbol':'OTHER'}},'source','hash')

if __name__=='__main__':unittest.main()
