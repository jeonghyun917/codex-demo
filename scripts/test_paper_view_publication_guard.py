from copy import deepcopy
from unittest import TestCase
from paper_view_publication_guard import validate_publication


def snapshot():
    return dict(days=[dict(date='2026-11-13',recordedAt='a',nextOpen='b',trend='-1%',volatilityCap='3%',
        predictions=[dict(symbol='A',rank=1,probability='60%',selected=False,interest=True,reason='trend')],
        tracking=dict(available=True,asof='2026-11-16',rows=[dict(symbol='A',status='OPEN')]))],
        accounts=[dict(feeKey='0.001',end='2026-11-13',trades=0)])


class PublicationGuardTests(TestCase):
    def test_identical_is_allowed(self):validate_publication(snapshot(),snapshot())
    def test_older_or_missing_tracking_is_blocked(self):
        for value in [dict(available=False),dict(available=True,asof='2026-11-13',rows=[])]:
            new=snapshot();new['days'][0]['tracking']=value
            with self.assertRaises(ValueError):validate_publication(new,snapshot())
    def test_changed_prediction_or_missing_day_is_blocked(self):
        new=snapshot();new['days'][0]['predictions'][0]['probability']='90%'
        with self.assertRaises(ValueError):validate_publication(new,snapshot())
        with self.assertRaises(ValueError):validate_publication(dict(days=[],accounts=[]),snapshot())
    def test_older_account_is_blocked(self):
        new=snapshot();new['accounts'][0]['end']='2026-10-30'
        with self.assertRaises(ValueError):validate_publication(new,snapshot())
    def test_newer_tracking_and_display_enrichment_are_allowed(self):
        old=snapshot();new=deepcopy(old);new['days'][0]['tracking']['asof']='2026-11-17'
        new['days'][0]['predictions'][0]['companyName']='Example'
        validate_publication(new,old)
