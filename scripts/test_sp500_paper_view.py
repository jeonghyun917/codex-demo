import tempfile
from pathlib import Path
from unittest import TestCase
from unittest.mock import patch
import publish_sp500_paper_view as view


class PaperViewTests(TestCase):
    def test_reference_close_requires_exact_date_and_verified_usable_source(self):
        import gzip, hashlib
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/'price_history.csv.gz'
            with gzip.open(path,'wt') as f:
                f.write('symbol,trade_date,close_price,price_usable\nA,2026-11-13,123.45,1\nB,2026-11-12,999,1\nC,2026-11-13,22,0\n')
            record=dict(datasetPath=d,anchor='2026-11-13',inputHashes={path.name:hashlib.sha256(path.read_bytes()).hexdigest()})
            self.assertEqual(view.reference_closes(record),{'A':123.45})
            record['inputHashes'][path.name]='wrong'
            self.assertEqual(view.reference_closes(record),{})

    def test_empty_is_not_fake_zero_performance(self):
        with tempfile.TemporaryDirectory() as d,patch.object(view.ledger,'protocol',return_value={'createdAtUtc':'2026-10-31T00:00:00Z'}),patch.object(view.ledger,'decisions',return_value=[]):
            data=view.build(Path(d))
            self.assertEqual(data['capturedDays'],0)
            self.assertEqual(data['accounts'],[])
            self.assertEqual(data['latest'],'-')

    def test_forecast_order_and_filter_status(self):
        records=[dict(anchor='2026-10-30',recordedAtUtc='2026-10-31T00:00:00Z',nextOpenUtc='2026-11-02T14:30:00Z',
            predictions=[dict(symbol='LOW',probability=.4,vol20=.01),dict(symbol='HIGH',probability=.8,vol20=.02)],ranks=[dict(symbol='HIGH')])]
        with tempfile.TemporaryDirectory() as d,patch.object(view.ledger,'protocol',return_value={'createdAtUtc':'2026-10-31T00:00:00Z'}),patch.object(view.ledger,'decisions',return_value=records):
            rows=view.build(Path(d))['days'][0]['predictions']
            self.assertEqual(rows[0]['symbol'],'HIGH')
            self.assertEqual(rows[0]['probability'],'80.00%')
            self.assertTrue(rows[0]['selected'])
            self.assertFalse(rows[1]['selected'])

    def test_missing_return_is_not_zero(self):
        self.assertEqual(view.pct(None),'-')
        self.assertEqual(view.money(None),'-')

    def test_trend_rejection_explains_cash_wait(self):
        records=[dict(anchor='2026-10-30',recordedAtUtc='2026-10-31T00:00:00Z',
            nextOpenUtc='2026-11-02T14:30:00Z',predictions=[],ranks=[],
            filterAudit=[dict(index_ma50=-.0152)])]
        with tempfile.TemporaryDirectory() as d,patch.object(view.ledger,'protocol',return_value={'createdAtUtc':'2026-10-31T00:00:00Z'}),patch.object(view.ledger,'decisions',return_value=records):
            message=view.build(Path(d))['message']
            self.assertIn('1.52%',message)
            self.assertIn('현금 대기',message)

    def test_reasons_use_frozen_thresholds_and_preserve_selected(self):
        record=dict(anchor='2026-10-30',recordedAtUtc='now',nextOpenUtc='later',
            predictions=[dict(symbol='A',probability=.5,vol20=.03,high63=-.12),
                         dict(symbol='B',probability=.4,vol20=.02,high63=-.04)],ranks=[],
            filterAudit=[dict(top_twenty=1,vol75=.025,index_ma50=-.01)])
        day=view.project_day(record)
        self.assertEqual(day['candidateCount'],0)
        self.assertEqual(day['predictions'][0]['reason'],'시장 추세 미충족 · 변동성 초과')
        self.assertEqual(day['predictions'][1]['reason'],'확률 상위 20% 밖 · 시장 추세 미충족')
        self.assertEqual(day['predictions'][0]['high63'],'-12.00%')
        self.assertTrue(day['predictions'][0]['interest'])
        self.assertFalse(day['predictions'][1]['interest'])

    def test_boundary_volatility_and_positive_trend_pass(self):
        record=dict(anchor='d',recordedAtUtc='now',nextOpenUtc='later',
            predictions=[dict(symbol='A',probability=.5,vol20=.025)],ranks=[dict(symbol='A')],
            filterAudit=[dict(top_twenty=1,vol75=.025,index_ma50=.001)])
        row=view.project_day(record)['predictions'][0]
        self.assertEqual(row['reason'],'조건 통과 · 체결 전 후보')
        self.assertTrue(row['selected'])

    def test_history_separate_and_only_frozen_combination(self):
        history=view.historical_evidence()
        self.assertTrue(history['available'])
        self.assertEqual(len(history['rows']),4)
        self.assertEqual({r['trades'] for r in history['rows']},{'324','14'})
        self.assertIn('미사용',history['notice'])

    def test_probability_evidence_has_separate_fees_and_empty_bins(self):
        data=view.probability_evidence()
        self.assertTrue(data['available'])
        self.assertEqual(len(data['groups']),4)
        group=data['groups'][0]
        self.assertEqual(group['count'],324)
        self.assertEqual(len(group['rows']),10)
        empty=next(r for r in group['rows'] if r['count']==0)
        self.assertEqual(empty['tpRate'],'-')
        self.assertEqual(empty['meanReturn'],'-')
        self.assertEqual(empty['sampleNote'],'표본 없음')

    def test_missing_probability_artifact_is_not_fake_zero(self):
        with tempfile.TemporaryDirectory() as d:
            data=view.probability_evidence(Path(d))
        self.assertFalse(data['available'])
        self.assertEqual(data['groups'],[])
