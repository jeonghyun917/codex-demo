"""Behavior tests for bounded membership corrections, including captured real regressions."""
import copy
import csv
import importlib.util
import json
import subprocess
import sys
import tempfile
from datetime import date
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location('membership_corrections', ROOT/'scripts/correct_quant_membership.py')
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def d(text):
    return date.fromisoformat(text)


def event(symbol, action, day, sid='release'):
    return dict(sourceId=sid,indexCode='SP500',symbol=symbol,action=action,effectiveDate=day,timing='BEFORE_OPEN')


def member(result, symbol, day):
    selected = None
    for when, names in result['states']:
        if when <= d(day):
            selected = names
    return symbol in (selected or set())


class MembershipCorrectionsTest(unittest.TestCase):
    def setUp(self):
        self.sources={'release':dict(id='release',url='https://press.spglobal.com/test',
                                      publishedDate='2022-12-01',publisher='S&P Dow Jones Indices')}

    def test_early_removal_restores_only_the_gap_to_official_effective_open(self):
        states=[(d('2023-01-01'),{'A','VNO'}),(d('2023-01-04'),{'A'}),(d('2023-01-10'),{'A'})]
        result=MODULE.correct_states(states,[event('VNO','REMOVE','2023-01-05')],self.sources)
        self.assertTrue(member(result,'VNO','2023-01-04'))
        self.assertFalse(member(result,'VNO','2023-01-05'))
        self.assertEqual(('2023-01-04','2023-01-04'),(result['overrides'][0]['startDate'],result['overrides'][0]['endDate']))
        self.assertEqual('2022-12-01',result['overrides'][0]['evidence'][0]['publishedDate'])

    def test_missing_temporary_spin_off_is_present_until_official_removal_only(self):
        states=[(d('2022-12-01'),{'A'}),(d('2022-12-20'),{'A'})]
        result=MODULE.correct_states(states,[event('MBC','ADD','2022-12-15'),event('MBC','REMOVE','2022-12-19')],self.sources)
        for day,want in [('2022-12-14',False),('2022-12-15',True),('2022-12-18',True),('2022-12-19',False)]:
            with self.subTest(day=day):
                self.assertEqual(want,member(result,'MBC',day))
        self.assertEqual(1,len(result['overrides']))
        self.assertEqual(2,len(result['overrides'][0]['evidence']))

    def test_late_community_addition_is_advanced_without_overriding_later_removal(self):
        states=[(d('2023-01-01'),{'A'}),(d('2023-01-12'),{'A','X'}),(d('2023-01-20'),{'A'})]
        result=MODULE.correct_states(states,[event('X','ADD','2023-01-10')],self.sources)
        self.assertTrue(member(result,'X','2023-01-10'))
        self.assertTrue(member(result,'X','2023-01-12'))
        self.assertFalse(member(result,'X','2023-01-20'))
        self.assertEqual('2023-01-11',result['overrides'][0]['endDate'])

    def test_early_addition_does_not_leak_before_effective_day(self):
        states=[(d('2023-01-01'),{'A'}),(d('2023-01-08'),{'A','X'}),(d('2023-01-20'),{'A','X'})]
        result=MODULE.correct_states(states,[event('X','ADD','2023-01-10')],self.sources)
        self.assertFalse(member(result,'X','2023-01-09'))
        self.assertTrue(member(result,'X','2023-01-10'))

    def test_missing_unbounded_membership_is_reported_not_invented(self):
        states=[(d('2023-01-01'),{'A'}),(d('2023-02-01'),{'A'})]
        result=MODULE.correct_states(states,[event('X','ADD','2023-01-10')],self.sources)
        self.assertFalse(member(result,'X','2023-01-20'))
        self.assertEqual(1,len(result['unresolved']))
        self.assertEqual([],result['overrides'])

    def test_far_away_transition_is_not_guessed_as_the_wrong_event(self):
        states=[(d('2023-01-01'),{'X'}),(d('2023-01-02'),set()),(d('2023-04-01'),set())]
        result=MODULE.correct_states(states,[event('X','REMOVE','2023-03-01')],self.sources)
        self.assertEqual(1,len(result['unresolved']))
        self.assertFalse(member(result,'X','2023-02-28'))

    def test_conflicting_actions_on_one_security_day_are_rejected(self):
        with self.assertRaises(ValueError):
            MODULE.correct_states([(d('2023-01-01'),set()),(d('2023-02-01'),set())],
                                  [event('X','ADD','2023-01-10'),event('X','REMOVE','2023-01-10')],self.sources)

    def test_source_published_after_effective_date_is_rejected(self):
        self.sources['release']['publishedDate']='2024-01-01'
        with self.assertRaises(ValueError):
            MODULE.correct_states([(d('2023-01-01'),{'X'}),(d('2023-02-01'),set())],
                                  [event('X','REMOVE','2023-01-10')],self.sources)

    def test_after_close_event_is_not_silently_applied_at_same_day_open(self):
        e=event('X','ADD','2023-01-10');e['timing']='AFTER_CLOSE'
        result=MODULE.correct_states([(d('2023-01-01'),set()),(d('2023-02-01'),set())],[e],self.sources)
        self.assertFalse(member(result,'X','2023-01-10'))
        self.assertEqual(1,len(result['unresolved']))

    def test_later_official_event_does_not_extend_community_coverage(self):
        states=[(d('2023-01-01'),{'A'}),(d('2023-02-01'),{'A'})]
        result=MODULE.correct_states(states,[event('X','ADD','2023-02-10')],self.sources)
        self.assertEqual(d('2023-02-01'),result['states'][-1][0])
        self.assertEqual(1,len(result['outOfRange']))

    def test_inputs_are_not_modified(self):
        states=[(d('2023-01-01'),{'X'}),(d('2023-01-09'),set()),(d('2023-01-20'),set())]
        before=copy.deepcopy(states)
        MODULE.correct_states(states,[event('X','REMOVE','2023-01-10')],self.sources)
        self.assertEqual(before,states)

    def test_captured_fja_regressions_are_corrected_without_changing_raw_inputs(self):
        raw=ROOT/'tmp/free-quant-data-2026-10-18/raw/fja05680_sp500/S&P 500 Historical Components & Changes (Updated).csv'
        if not raw.exists():
            self.skipTest('Local acquired research fixture not present')
        original=raw.read_bytes()
        with raw.open(encoding='utf-8') as source:
            states=[(d(r['date']),{s.replace('.','-') for s in r['tickers'].split(',')})
                    for r in csv.DictReader(source)]
        bundle=json.loads((ROOT/'tmp/pit-acquisition/membership/bundle.json').read_text())
        result=MODULE.correct_states(states,[e for e in bundle['events'] if e['indexCode']=='SP500'],
                                     {s['id']:s for s in bundle['sources']})
        self.assertEqual(6,len(result['overrides']))
        self.assertEqual(0,len(result['unresolved']))
        self.assertEqual(4,len(result['outOfRange']))
        self.assertEqual(135,sum(q['afterCorrect'] for q in result['qa']))
        self.assertEqual(128,sum(q['beforeCorrect'] for q in result['qa']))
        self.assertTrue(member(result,'GPS','2022-02-02'))
        self.assertTrue(member(result,'VFC','2024-04-02'))
        self.assertFalse(member(result,'VFC','2024-04-03'))
        self.assertTrue(member(result,'MBC','2022-12-18'))
        self.assertFalse(member(result,'MBC','2022-12-19'))
        self.assertEqual(original,raw.read_bytes())

    def test_cli_exports_traceable_corrected_intervals_and_all_event_qa(self):
        if not (ROOT/'tmp/free-quant-data-2026-10-18/manifest.json').exists():
            self.skipTest('Local acquired research fixture not present')
        with tempfile.TemporaryDirectory(dir=ROOT/'tmp',ignore_cleanup_errors=True) as folder:
            self.assertTrue(Path(folder).resolve().is_relative_to((ROOT/'tmp').resolve()))
            run=subprocess.run([sys.executable,str(ROOT/'scripts/correct_quant_membership.py'),
                                '--output',folder],capture_output=True,text=True)
            self.assertEqual(0,run.returncode,run.stderr)
            output=Path(folder)
            self.assertTrue((output/'manifest.json').is_file(),'Correction CLI must produce auditable artifacts')
            audit=json.loads((output/'manifest.json').read_text())
            self.assertEqual(139,len(audit['officialEventQA']))
            self.assertEqual(135,audit['summary']['officialTransitionsCorrectAfter'])
            self.assertFalse(audit['coverage']['completeOfficialChain'])
            self.assertEqual('2026-08-18',audit['coverage']['lastDate'])
            self.assertEqual(5,len(audit['nasdaqAnnualAnnouncementChecks']))
            self.assertTrue(all(r['matches'] for r in audit['nasdaqAnnualAnnouncementChecks']))
            with (output/'sp500_membership_intervals.csv').open(encoding='utf-8') as inp:
                intervals=list(csv.DictReader(inp))
            mbc=[r for r in intervals if r['symbol']=='MBC']
            self.assertEqual([('2022-12-15','2022-12-18')],[(r['start_date'],r['end_date']) for r in mbc])
            self.assertTrue(mbc[0]['provenance_ids'])
            self.assertNotEqual('OFFICIAL_PIT',mbc[0]['source'])


if __name__=='__main__':
    unittest.main()
