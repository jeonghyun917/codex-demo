import json
from pathlib import Path
import tempfile
import unittest
from datetime import datetime,timezone
from unittest.mock import patch
import pandas as pd
import sp500_paper_ledger as ledger


CAL=[dict(session='2026-10-30',openUtc='2026-10-30T13:30:00Z',closeUtc='2026-10-30T20:00:00Z'),
     dict(session='2026-11-02',openUtc='2026-11-02T14:30:00Z',closeUtc='2026-11-02T21:00:00Z'),
     dict(session='2026-11-03',openUtc='2026-11-03T14:30:00Z',closeUtc='2026-11-03T21:00:00Z')]
NOW=datetime(2026,10,31,12,tzinfo=timezone.utc)


class LedgerTests(unittest.TestCase):
    def test_capture_window_and_dst(self):
        self.assertEqual(ledger.capture_window(CAL,NOW,'2026-10-30')[1]['session'],'2026-11-02')
        for now,anchor in [(datetime(2026,10,30,19,tzinfo=timezone.utc),'2026-10-30'),
                           (datetime(2026,11,2,14,30,tzinfo=timezone.utc),'2026-10-30'),
                           (NOW,'2026-10-23')]:
            with self.assertRaises(ValueError):ledger.capture_window(CAL,now,anchor)

    def test_early_close_and_expired_calendar(self):
        cal=[dict(session='2026-11-27',openUtc='2026-11-27T14:30:00Z',closeUtc='2026-11-27T18:00:00Z'),
             dict(session='2026-11-30',openUtc='2026-11-30T14:30:00Z',closeUtc='2026-11-30T21:00:00Z')]
        ledger.capture_window(cal,datetime(2026,11,27,18,tzinfo=timezone.utc),'2026-11-27')
        with self.assertRaises(ValueError):ledger.capture_window(cal,datetime(2026,12,1,tzinfo=timezone.utc),'2026-11-30')

    def test_create_only_and_integrity(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'record.json'
            ledger.save(p,dict(value=1))
            with self.assertRaises(FileExistsError):ledger.save(p,dict(value=2))
            self.assertEqual(ledger.load(p)['value'],1)
            body=json.loads(p.read_text());body['value']=2;p.write_text(json.dumps(body))
            with self.assertRaises(ValueError):ledger.load(p)

    def test_frozen_filter_no_backfill(self):
        f=pd.DataFrame([dict(symbol=f'S{i}',date='2026-10-30',probability=.9-i*.05,
                            vol20=.5 if i==0 else .1,index_ma50=.1) for i in range(10)])
        predictions,ranks,audit=ledger.select(f)
        self.assertEqual([r['symbol'] for r in ranks],['S1'])
        self.assertEqual(len(predictions),10)
        f['index_ma50']=0
        self.assertEqual(ledger.select(f)[1],[])

    def test_probability_validation(self):
        for p in [-.1,1.1,float('nan')]:
            with self.assertRaises(ValueError):ledger.select(pd.DataFrame([dict(symbol='X',date='2026-10-30',probability=p,vol20=.1,index_ma50=.1)]))

    def test_realized_barriers_and_cash(self):
        from sp500_daily_validation import run_account
        bars={('X','2026-11-02'):dict(open=100,high=116,low=94,close=110,volume=100)}
        a=run_account(['2026-10-30','2026-11-02'],bars,{'2026-10-30':[dict(symbol='X',score=.7)]})
        record=dict(anchor='2026-10-30',predictions=[dict(symbol='X',probability=.7)])
        result=ledger.compare_fills(a,[record])
        self.assertEqual(result[0]['reason'],'SL')
        self.assertEqual(result[0]['actualTargetFirst'],0)
        self.assertEqual(result[0]['predictedProbability'],.7)
        self.assertAlmostEqual(a['equity'][-1]['equity'],100000-99*100*1.001+99*95*.999)

    def test_capture_and_receipt_then_duplicate_rejected(self):
        days=pd.bdate_range(end='2026-10-30',periods=200).strftime('%Y-%m-%d').tolist()
        rows=[dict(symbol='X',date=d,session=i,maturity=days[min(i+20,199)],label=i%2,
                   vol20=.1,index_ma50=.1,ret20=.1,ret60=.1,atr20=.1,high63=-.1)
              for i,d in enumerate(days)]
        p=dict(calendar=CAL,createdAtUtc='2026-10-31T10:00:00Z',contentSha256='protocol')
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            with patch.object(ledger,'protocol',return_value=p),patch.object(ledger,'checked_data',return_value={'end':'2026-10-30'}), \
                 patch.object(ledger,'dataset_hashes',return_value={}),patch.object(ledger,'now_utc',return_value=NOW), \
                 patch.object(ledger.engine,'load_features',return_value=(days,{}, {'2026-10-30':{'X'}},rows,{},[])), \
                 patch.object(ledger,'fit_score',return_value=[.7]):
                result=ledger.capture(root,root/'dataset')
                self.assertEqual(result['status'],'SAVED_BEFORE_NEXT_OPEN')
                self.assertEqual(len(ledger.decisions(root,p)),1)
                with self.assertRaises(FileExistsError):ledger.capture(root,root/'dataset')
            (root/'receipts/2026-10-30.json').unlink()
            with self.assertRaises(FileNotFoundError):ledger.decisions(root,p)

    def test_tp_gap_and_twentieth_session(self):
        event=ledger.engine.exit_event
        self.assertEqual(event(dict(open=90,low=89,high=101,close=100),100,1),(90,'SL_GAP'))
        self.assertEqual(event(dict(open=100,low=99,high=116,close=110),100,1)[1],'TP')
        b=dict(open=100,low=99,high=101,close=100)
        self.assertIsNone(event(b,100,19))
        self.assertEqual(event(b,100,20),(100,'TIME'))

    def test_evaluate_uses_saved_forecast_and_preserves_reports(self):
        p=dict(calendar=CAL,createdAtUtc='2026-10-31T10:00:00Z',contentSha256='protocol',fees=[.001,.002])
        record=dict(anchor='2026-10-30',protocolSha256='protocol',recordedAtUtc=ledger.iso(NOW),
                    entrySession='2026-11-02',nextOpenUtc=CAL[1]['openUtc'],
                    predictions=[dict(symbol='X',probability=.7)],ranks=[dict(symbol='X',score=.7)])
        def rows(path):
            if path.name=='calendar.csv.gz':return [dict(trade_date=r['session']) for r in CAL]
            if path.name=='aliases.csv':return []
            return [dict(symbol='X',trade_date='2026-11-02',open_price=100,high_price=116,
                         low_price=99,close_price=110,volume=100,price_usable='1')]
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);ledger.save(root/'decisions/2026-10-30.json',record)
            ledger.save(root/'receipts/2026-10-30.json',dict(committedAtUtc=ledger.iso(NOW),
                decisionSha256=ledger.load(root/'decisions/2026-10-30.json')['contentSha256']))
            with patch.object(ledger,'protocol',return_value=p),patch.object(ledger,'checked_data',return_value={'end':'2026-11-02'}), \
                 patch.object(ledger,'dataset_hashes',return_value={}),patch.object(ledger,'read_csv',side_effect=rows), \
                 patch.object(ledger,'now_utc',return_value=datetime(2026,11,2,22,tzinfo=timezone.utc)):
                a=ledger.evaluate(root,root/'dataset');b=ledger.evaluate(root,root/'dataset')
            self.assertNotEqual(a['path'],b['path'])
            report=ledger.load(a['path'])
            self.assertEqual(report['accounts'][0]['fills'][0]['actualTargetFirst'],1)
            self.assertEqual(report['accounts'][0]['fills'][0]['predictedProbability'],.7)
            self.assertEqual(len(report['accounts']),2)


if __name__=='__main__':unittest.main()
