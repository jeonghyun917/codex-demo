import copy
import hashlib
import importlib
import json
from pathlib import Path
import tempfile
import unittest

try:q=importlib.import_module('quant_full_financial_study')
except ModuleNotFoundError:q=None

def old_row(revenue=100.,concept='SalesRevenueNet'):
    return dict(id=7,symbol='A',cik='0000000001',fiscalYear=2019,fiscalPeriod='Q2',form='10-Q',filedAt='2019-07-31',startDate='2019-04-01',endDate='2019-06-30',accessionNumber='0000000001-19-000001',periodDays=91,currency='USD',revenue=revenue,operatingIncome=None,netIncome=None,epsDiluted=None,epsUnit=None,assets=500.,liabilities=300.,equity=200.,operatingCashFlow=30.,mappedAt='2020-01-01T00:00:00',rawJson=json.dumps({'selectedFieldEvidence':{'revenue':{'concept':concept,'taxonomy':'us-gaap','unit':'USD'}}}))

def fresh_row():
    f=old_row();f.update(id=None,revenue=120.,netIncome=20.,operatingIncome=30.,epsDiluted=2.,epsUnit='USD/shares',_epsConcept='EarningsPerShareDiluted',_fieldConcepts={},_proofs={},_phaseEvidence={'originalFP':['Q2']});f.pop('rawJson');return f

def archive(**fields):
    tags={}
    for concept,value in fields.items():
        unit='USD/shares' if concept=='EarningsPerShareDiluted' else 'USD'
        observation=dict(start='2019-04-01',end='2019-06-30',filed='2019-07-31',accn='0000000001-19-000001',fy=2019,fp='Q2',form='10-Q',val=value)
        tags[concept]={'units':{unit:[observation]}}
    return {'cik':1,'facts':{'us-gaap':tags}}

class FullFinancialTest(unittest.TestCase):
    def setUp(self):self.assertIsNotNone(q,'Whole-model financial-study adapter missing')

    def test_product_revenue_priority_preserves_total_instead_of_contract_subset(self):
        value,proof=q.canonical_field(fresh_row(),'revenue',archive(Revenues=120.,RevenueFromContractWithCustomerExcludingAssessedTax=110.),None)
        self.assertEqual(value,120.);self.assertEqual(proof['concept'],'Revenues')

    def test_merge_retains_balance_fields_and_logs_source_priority_upgrade(self):
        original={'A':[old_row()]};source={'A':[fresh_row()]};raw={('A','1'):archive(Revenues=120.,SalesRevenueNet=100.,NetIncomeLoss=20.,OperatingIncomeLoss=30.,EarningsPerShareDiluted=2.)}
        merged,audit=q.merge_financials(original,source,raw,[])
        row=merged['A'][0]
        self.assertEqual(row['assets'],500.);self.assertEqual(row['operatingCashFlow'],30.)
        self.assertEqual(row['id'],7);self.assertEqual(row['revenue'],120.)
        self.assertEqual(audit['priorityUpgrades'],1)
        self.assertFalse(any(k.startswith('_') for k in row))

    def test_same_concept_value_conflict_cannot_silently_overwrite(self):
        with self.assertRaises(ValueError):q.merge_financials({'A':[old_row(concept='Revenues')]},{'A':[fresh_row()]},{('A','1'):archive(Revenues=120.)},[])

    def test_common_income_is_not_replaced_by_unsupported_consolidated_profit(self):
        before=old_row();before['netIncome']=10.;before['rawJson']=json.dumps({'selectedFieldEvidence':{'revenue':{'concept':'Revenues'},'netIncome':{'concept':'NetIncomeLossAvailableToCommonStockholdersBasic'}}})
        merged,_=q.merge_financials({'A':[before]},{'A':[fresh_row()]},{('A','1'):archive(Revenues=100.,NetIncomeLossAvailableToCommonStockholdersBasic=10.,ProfitLoss=15.)},[])
        self.assertEqual(merged['A'][0]['netIncome'],10.)

    def test_fiscal_year_metadata_difference_does_not_drop_same_period_balance(self):
        new=fresh_row();new['fiscalYear']=2018
        merged,_=q.merge_financials({'A':[old_row()]},{'A':[new]},{('A','1'):archive(Revenues=100.,SalesRevenueNet=100.)},[])
        self.assertEqual(len(merged['A']),1);self.assertEqual(merged['A'][0]['assets'],500.)

    def test_unmatched_original_rows_preserved(self):
        original=old_row();original['accessionNumber']='older'
        merged,audit=q.merge_financials({'A':[original]},{'A':[fresh_row()]},{('A','1'):archive(Revenues=120.)},[])
        self.assertEqual(len(merged['A']),2);self.assertIn(original,merged['A']);self.assertEqual(audit['retainedUnmatchedOriginal'],1)

    def test_unsupported_profit_does_not_become_parent_income(self):
        value,_=q.canonical_field(fresh_row(),'netIncome',archive(ProfitLoss=50.),None)
        self.assertIsNone(value)

    def test_primary_override_requires_exact_period_publication_accession_hash_and_scale(self):
        with tempfile.TemporaryDirectory() as directory:
            p=Path(directory)/'original.html';p.write_text('Consolidated total revenue 120 million',encoding='utf-8')
            identity={k:old_row()[k] for k in q.OVERRIDE_IDENTITY}
            override=dict(identity=identity,revenueUsd='120000000',proof=dict(sourceURL='https://www.sec.gov/Archives/example.html',localDocumentPath=str(p),sourceSha256=hashlib.sha256(p.read_bytes()).hexdigest(),sourceDocumentAccession=identity['accessionNumber'],sourcePublicationDate='2019-07-31',periodStart='2019-04-01',periodEnd='2019-06-30',currency='USD',unitsScale=1000000,printedValue='120',tableLabel='Consolidated total revenue',excerpt='Consolidated total revenue 120 million',pageOrLocator='table1',totalScopeConfirmed=True))
            self.assertEqual(q.validate_override(override,old_row()),120000000.)
            for field,value in [('sourcePublicationDate','2020-01-01'),('periodEnd','2019-03-31'),('sourceDocumentAccession','another'),('sourceSha256','0'*64),('printedValue','999'),('totalScopeConfirmed',False)]:
                bad=copy.deepcopy(override);bad['proof'][field]=value
                with self.assertRaises(ValueError):q.validate_override(bad,old_row())

    def test_changed_return_labels_or_old60_cannot_enter_comparison(self):
        r=dict(signalDate='2020-08-28',symbol='A',horizonDays=20,forwardReturnPct=1.,benchmarkReturnPct=.5)
        p=dict(signalDate='2020-08-28',symbol='A',horizonDays=20,expectedReturnPct=1.,expectedExcessReturnPct=.5)
        changed=dict(r,forwardReturnPct=2.)
        with self.assertRaises(ValueError):q.join_pairs([r],[changed],[p],[p])
        r['signalDate']='2020-09-30';p['signalDate']='2020-09-30'
        with self.assertRaises(ValueError):q.join_pairs([r],[r],[p],[p])

if __name__=='__main__':unittest.main()
