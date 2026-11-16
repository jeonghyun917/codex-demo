import copy
import importlib
import json
import tempfile
import unittest
from pathlib import Path

import quant_full_financial_study as source
from test_quant_full_financial_study import old_row

try:
    study = importlib.import_module('quant_v9_financial_followup')
except ModuleNotFoundError:
    study = None


class FollowupTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(study, 'Follow-up overlay and diagnostic implementation missing')

    def entry(self, path):
        row=old_row(revenue=None)
        return row, dict(identity={k:row[k] for k in source.OVERRIDE_IDENTITY}, revenueUsd='120000000',
          proof=dict(sourceURL='https://www.sec.gov/Archives/literal.html',localDocumentPath=str(path),
            sourceSha256=source.sha(path),sourceDocumentAccession=row['accessionNumber'],
            sourcePublicationDate=row['filedAt'],periodStart=row['startDate'],periodEnd=row['endDate'],
            currency='USD',unitsScale=1000000,printedValue='120',tableLabel='Consolidated total revenue',
            excerpt='Total revenues 120 million',pageOrLocator='table1',totalScopeConfirmed=True))

    def test_verified_null_only_overlay_keeps_all_other_cells_and_input_unchanged(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/'original.html';path.write_text('Total revenues 120 million')
            row,entry=self.entry(path);original={'A':[row]};before=copy.deepcopy(original)
            out,audit=study.overlay_revenues(original,[entry])
            self.assertEqual(original,before)
            after=out['A'][0]
            self.assertEqual(after['revenue'],120000000.)
            self.assertEqual({k:v for k,v in after.items() if k not in {'revenue','rawJson'}},
                             {k:v for k,v in row.items() if k not in {'revenue','rawJson'}})
            self.assertEqual(audit['filled'],1)
            self.assertEqual(json.loads(after['rawJson'])['selectedFieldEvidence']['revenue']['taxonomy'],'VERIFIED_PRIMARY_FILING')

    def test_nonnull_conflict_duplicate_unknown_or_wrong_period_cannot_enter(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/'original.html';path.write_text('Total revenues 120 million')
            row,entry=self.entry(path)
            row['revenue']=99.
            with self.assertRaises(ValueError): study.overlay_revenues({'A':[row]},[entry])
            row['revenue']=None
            with self.assertRaises(ValueError): study.overlay_revenues({'A':[row]},[entry,entry])
            other=copy.deepcopy(entry);other['identity']['accessionNumber']='unknown'
            with self.assertRaises(ValueError): study.overlay_revenues({'A':[row]},[other])
            other=copy.deepcopy(entry);other['proof']['periodEnd']='2019-03-31'
            with self.assertRaises(ValueError): study.overlay_revenues({'A':[row]},[other])

    def test_same_existing_verified_value_is_not_overwritten(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/'original.html';path.write_text('Total revenues 120 million')
            row,entry=self.entry(path);row['revenue']=120000000.
            out,audit=study.overlay_revenues({'A':[row]},[entry])
            self.assertEqual(out,{'A':[row]});self.assertEqual(audit['alreadyPresent'],1)

    def test_date_balanced_error_contributions_reconcile_not_row_weighted(self):
        rows=[dict(day='2019-01-02',symbol='A',y=[0.,0.],base=[0.,0.],new=[2.,-1.]),
              dict(day='2019-01-02',symbol='B',y=[0.,0.],base=[0.,0.],new=[0.,1.]),
              dict(day='2019-01-03',symbol='A',y=[0.,0.],base=[0.,0.],new=[3.,0.])]
        result=study.error_decomposition(rows)
        self.assertEqual(result['deltaMaePp'],[2.,.5])
        self.assertEqual(result['bySymbol']['A']['deltaMaeContributionPp'],[2.,.25])
        self.assertEqual(result['bySymbol']['B']['deltaMaeContributionPp'],[0.,.25])
        self.assertEqual(result['squaredErrorIdentity']['predictionDeltaSquared'],[5.5,.5])
        self.assertEqual(result['squaredErrorIdentity']['baseErrorCrossTerm'],[0.,0.])

    def test_diagnostic_rejects_duplicate_nonfinite_and_old60_records(self):
        row=dict(day='2019-01-02',symbol='A',y=[0.,0.],base=[0.,0.],new=[1.,1.])
        with self.assertRaises(ValueError): study.error_decomposition([row,row])
        bad=copy.deepcopy(row);bad['new'][0]=float('nan')
        with self.assertRaises(ValueError): study.error_decomposition([bad])
        bad=copy.deepcopy(row);bad['day']='2020-09-30'
        with self.assertRaises(ValueError): study.error_decomposition([bad])


if __name__=='__main__': unittest.main()
