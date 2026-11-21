from unittest import TestCase
from datetime import datetime,timezone,timedelta
from sp500_readiness_audit import assess


class ReadinessTests(TestCase):
    def test_latest_open_capture_window_is_not_a_missed_capture(self):
        calendar=[dict(session='2026-11-13',openUtc='2026-11-13T14:30:00Z',closeUtc='2026-11-13T21:00:00Z'),
            dict(session='2026-11-16',openUtc='2026-11-16T14:30:00Z',closeUtc='2026-11-16T21:00:00Z'),
            dict(session='2026-11-17',openUtc='2026-11-17T14:30:00Z',closeUtc='2026-11-17T21:00:00Z')]
        record=dict(anchor='2026-11-13',entrySession='2026-11-16',predictions=[dict(symbol='A',probability=.7)])
        payload=dict(days=[dict(date='2026-11-13',tracking=dict(available=True,asof='2026-11-16',rows=[dict(symbol='A',status='SL')]))])
        r=assess([record],calendar,payload,datetime(2026,11,16,22,tzinfo=timezone.utc))
        self.assertEqual(r['missedDecisionDates'],[])
        self.assertEqual(r['pendingDecisionDates'],['2026-11-16'])
        self.assertEqual(r['matureCohorts'],0)
        self.assertIsNone(r['matureTop20Brier'])
    def test_full_horizon_without_complete_outcomes_is_not_evaluable(self):
        start=datetime(2026,1,1,tzinfo=timezone.utc)
        calendar=[dict(session=(start+timedelta(days=i)).date().isoformat(),
            openUtc=(start+timedelta(days=i,hours=14)).isoformat(),closeUtc=(start+timedelta(days=i,hours=21)).isoformat()) for i in range(22)]
        record=dict(anchor=calendar[0]['session'],entrySession=calendar[1]['session'],predictions=[dict(symbol='A',probability=.7)])
        payload=dict(days=[dict(date=record['anchor'],tracking=dict(available=True,asof=calendar[20]['session'],rows=[dict(symbol='A',status='DATA_GAP')]))])
        result=assess([record],calendar,payload,start+timedelta(days=21,hours=22))
        self.assertEqual(result['matureCohorts'],1)
        self.assertEqual(result['evaluableCohorts'],0)
        self.assertIsNone(result['matureTop20Brier'])
