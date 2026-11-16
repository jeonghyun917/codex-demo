import copy
import json
import unittest
from quant_balance_completion import complete_financials


def row():
    return dict(symbol='X',cik='0000000001',accessionNumber='A',filedAt='2020-05-01',form='10-Q',currency='USD',startDate='2020-01-01',endDate='2020-03-31',periodDays=91,id=7,mappedAt='unchanged',revenue=15,epsDiluted=2,assets=None,liabilities=None,equity=None,operatingCashFlow=None,rawJson=json.dumps({'primaryProof':{'keep':True},'selectedFieldEvidence':{'revenue':{'concept':'Revenues'}}}))


def observation(value=100,**kw):
    return dict(dict(accn='A',filed='2020-05-01',form='10-Q',end='2020-03-31',val=value),**kw)


def archives(concepts,cik=1):
    return {('X','1'):{'cik':cik,'facts':{'us-gaap':{name:{'units':{'USD':obs}} for name,obs in concepts.items()}}}}


class CompletionTests(unittest.TestCase):
    def complete(self,r,concepts):
        return complete_financials({'X':[r]},archives(concepts))[0]['X'][0]

    def test_instant_only_and_all_other_content_preserved(self):
        r=row();before=copy.deepcopy(r)
        result=self.complete(r,{'Assets':[observation(100),observation(200,start='2020-03-31')]})
        self.assertEqual(result['assets'],100)
        self.assertEqual(r,before)
        for field,value in r.items():
            if field not in ('assets','rawJson'):self.assertEqual(result[field],value)
        raw=json.loads(result['rawJson'])
        self.assertEqual(raw['primaryProof'],{'keep':True})
        self.assertEqual(raw['selectedFieldEvidence']['revenue'],{'concept':'Revenues'})
        self.assertIsNone(raw['selectedFieldEvidence']['assets']['startDate'])

    def test_rejects_duration_balance_and_cfo_ytd(self):
        r=row();r['startDate']='2020-07-01';r['endDate']='2020-09-30'
        result=self.complete(r,{'Assets':[observation(100,end='2020-09-30',start='2020-09-30')],'NetCashProvidedByUsedInOperatingActivities':[observation(8,start='2020-01-01',end='2020-09-30')]})
        self.assertIsNone(result['assets']);self.assertIsNone(result['operatingCashFlow'])

    def test_exact_cfo_and_identity_filters(self):
        facts=[observation(3,start='2020-01-01'),observation(20,start='2020-01-01',accn='B'),observation(30,start='2020-01-01',filed='2020-05-02'),observation(40,start='2020-01-01',form='10-K')]
        self.assertEqual(self.complete(row(),{'NetCashProvidedByUsedInOperatingActivities':facts})['operatingCashFlow'],3)
        with self.assertRaises(ValueError):complete_financials({'X':[row()]},archives({'Assets':[observation()]},cik=2))

    def test_same_source_conflicts_and_old_same_concept_conflicts_fail(self):
        with self.assertRaises(ValueError):self.complete(row(),{'Assets':[observation(1),observation(2)]})
        r=row();r['assets']=9;r['rawJson']=json.dumps({'selectedFieldEvidence':{'assets':{'concept':'Assets'}}})
        with self.assertRaises(ValueError):self.complete(r,{'Assets':[observation(10)]})

    def test_priority_upgrade_and_absent_proof_preserve(self):
        low='StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest'
        r=row();r['equity']=15;r['rawJson']=json.dumps({'selectedFieldEvidence':{'equity':{'concept':low}}})
        result,audit=complete_financials({'X':[r]},archives({'StockholdersEquity':[observation(12)],low:[observation(15)]}))
        self.assertEqual(result['X'][0]['equity'],12);self.assertEqual(audit['priorityUpgrades'],1)
        self.assertEqual(self.complete(r,{})['equity'],15)

    def test_lower_or_unknown_basis_conflict_rejected(self):
        r=row();r['equity']=15
        with self.assertRaises(ValueError):self.complete(r,{'StockholdersEquity':[observation(12)]})

    def test_currency_end_and_cfo_priority(self):
        r=row()
        result=self.complete(r,{'Liabilities':[observation(11),observation(99,end='2019-12-31')],
            'NetCashProvidedByUsedInOperatingActivities':[observation(3,start='2020-01-01')],
            'NetCashProvidedByUsedInOperatingActivitiesContinuingOperations':[observation(4,start='2020-01-01')]})
        self.assertEqual(result['liabilities'],11)
        self.assertEqual(result['operatingCashFlow'],3)
        r['currency']='EUR'
        self.assertIsNone(self.complete(r,{'Assets':[observation(100)]})['assets'])

    def test_conflict_in_lower_priority_exact_concept_still_fails(self):
        low='StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest'
        with self.assertRaises(ValueError):
            self.complete(row(),{'StockholdersEquity':[observation(12)],low:[observation(14),observation(15)]})


if __name__=='__main__':unittest.main()
