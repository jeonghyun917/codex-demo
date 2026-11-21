from unittest import TestCase
from sp500_watchlist_tracking import track_symbol
from sp500_watchlist_tracking import project_tracking
from unittest.mock import patch
from datetime import datetime,timezone


def bar(o=100,h=104,l=98,c=102):
    return dict(open=o,high=h,low=l,close=c,volume=100,execution_blocked=False)


class WatchlistTrackingTests(TestCase):
    def test_changed_adjustment_basis_is_not_reported_as_a_trade(self):
        r=dict(anchor='2026-10-30',entrySession='2026-11-02',datasetPath='unused',inputHashes={},
            predictions=[dict(symbol='A',probability=.8)],ranks=[])
        calendar=dict(calendar=[dict(session='2026-11-02',closeUtc='2026-11-02T21:00:00Z')])
        latest={('A','2026-10-30'):bar(50,52,49,51),('A','2026-11-02'):bar(50,60,49,59)}
        frozen={('A','2026-10-30'):bar()}
        with patch('sp500_watchlist_tracking.load_prices',side_effect=[(latest,'2026-11-02'),(frozen,'2026-10-30')]),patch('sp500_watchlist_tracking.ledger.dataset_hashes',return_value={}):
            result=project_tracking([r],calendar,datetime(2026,11,3,tzinfo=timezone.utc))['2026-10-30']
        self.assertEqual(result['blocked'],1)
        self.assertEqual(result['completed'],0)
        self.assertIsNone(result['rows'][0]['net10'])

    def test_waiting_and_open_do_not_fabricate_completed_returns(self):
        self.assertEqual(track_symbol('A',['d1'],{},'d0')['status'],'WAITING_ENTRY')
        r=track_symbol('A',['d1','d2'],{('A','d1'):bar()},'d1')
        self.assertEqual(r['status'],'OPEN')
        self.assertIsNone(r['net10'])

    def test_tp_uses_entry_open_and_costs(self):
        r=track_symbol('A',['d1'],{('A','d1'):bar(200,231,198,225)},'d1')
        self.assertEqual(r['status'],'TP')
        self.assertAlmostEqual(r['exitPrice'],230)
        self.assertAlmostEqual(r['net10'],100*(230*.999/(200*1.001)-1))
        self.assertLess(r['net20'],r['net10'])

    def test_stop_gap_uses_gap_open_not_stop_level(self):
        r=track_symbol('A',['d1','d2'],{('A','d1'):bar(),('A','d2'):bar(90,94,88,92)},'d2')
        self.assertEqual(r['status'],'SL_GAP')
        self.assertEqual(r['exitPrice'],90)

    def test_ambiguous_bar_uses_existing_stop_first_policy(self):
        r=track_symbol('A',['d1'],{('A','d1'):bar(100,116,94,102)},'d1')
        self.assertEqual(r['status'],'SL')
        self.assertTrue(r['ambiguous'])

    def test_time_exit_counts_entry_as_session_one(self):
        days=[f'd{i:02}' for i in range(1,22)]
        prices={('A',d):bar() for d in days}
        r=track_symbol('A',days,prices,'d21')
        self.assertEqual((r['status'],r['exitDate'],r['sessions']),('TIME','d20',20))

    def test_missing_or_invalid_bar_blocks_without_skipping_to_later_hit(self):
        r=track_symbol('A',['d1','d2'],{('A','d2'):bar(100,116,98,110)},'d2')
        self.assertEqual(r['status'],'DATA_GAP')
        self.assertIsNone(r['net10'])
        b=bar();b['execution_blocked']=True
        self.assertEqual(track_symbol('A',['d1'],{('A','d1'):b},'d1')['status'],'DATA_GAP')

    def test_no_future_bar_is_used_and_missing_after_exit_is_irrelevant(self):
        prices={('A','d1'):bar(),('A','d2'):bar(100,116,98,110)}
        self.assertEqual(track_symbol('A',['d1','d2'],prices,'d1')['status'],'OPEN')
        self.assertEqual(track_symbol('A',['d1','d2','d3'],prices,'d3')['status'],'TP')
