import copy
import unittest
import quant_eps_training_recovery as q

class EpsTrainingRecoveryTest(unittest.TestCase):
    def test_only_null_eps_and_evidence_can_change(self):
        old={'A':[dict(id=1,symbol='A',epsDiluted=None,epsUnit=None,rawJson='old',revenue=10)]}
        new=copy.deepcopy(old);new['A'][0].update(epsDiluted=2,epsUnit='USD/shares',rawJson='new')
        self.assertEqual(q.validate_repairs(old,new),1)
        new['A'][0]['revenue']=20
        with self.assertRaises(ValueError):q.validate_repairs(old,new)

    def test_existing_eps_is_not_rewritten(self):
        old={'A':[dict(id=1,symbol='A',epsDiluted=2,epsUnit='USD/shares',rawJson='old')]};new=copy.deepcopy(old);new['A'][0]['epsDiluted']=3
        with self.assertRaises(ValueError):q.validate_repairs(old,new)

    def test_changed_return_labels_cannot_be_compared_as_data_only_repair(self):
        t=dict(signalDate='2020-08-28',symbol='A',horizonDays=20,forwardReturnPct=1,benchmarkReturnPct=.5)
        p=dict(signalDate='2020-08-28',symbol='A',horizonDays=20,expectedReturnPct=2,expectedExcessReturnPct=1)
        self.assertEqual(q.join_pairs([t],[t],[p],[p])[0]['y'],[1.,.5])
        changed=dict(t,forwardReturnPct=2)
        with self.assertRaises(ValueError):q.join_pairs([t],[changed],[p],[p])

    def test_old_test_decisions_cannot_enter_comparison(self):
        t=dict(signalDate='2020-09-30',symbol='A',horizonDays=20,forwardReturnPct=1,benchmarkReturnPct=.5)
        p=dict(signalDate='2020-09-30',symbol='A',horizonDays=20,expectedReturnPct=2,expectedExcessReturnPct=1)
        with self.assertRaises(ValueError):q.join_pairs([t],[t],[p],[p])

if __name__=='__main__':unittest.main()
