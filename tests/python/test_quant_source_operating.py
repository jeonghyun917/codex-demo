import copy,json,sys,tempfile,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'scripts'))
import quant_source_bridge as bridge

class SourceOperatingTest(unittest.TestCase):
    def service(self):
        import quant_source_operating
        return quant_source_operating
    def pack(self,root):
        source=root/'release.html';source.write_bytes(b'quarterly revenue 50')
        digest=bridge.hashlib.sha256(source.read_bytes()).hexdigest()
        identity=dict(symbol='AMGN',cik='0000318154',sourceKind='OFFICIAL_ISSUER_EARNINGS_RELEASE',
            sourceDocumentId='https://www.amgen.com/newsroom/quarter',sourceSha256=digest,publicationDate='2020-01-30',
            secAccessionNumber=None,secFiledDate=None,startDate='2019-10-01',endDate='2019-12-31',fiscalPeriod='Q4',currency='USD')
        candidate=dict(candidateId='AMGN_Q4',canonicalIdentity=identity,sourceLocalPath=str(source),
            availability=dict(publicDate='2020-01-30',precision='DATE_ONLY',publicAt=None,publicationTimeZone=None,systemAcquiredAt=None),
            values=dict(revenue='50',netIncome=None,epsDiluted=None,operatingIncome=None),
            units=dict(revenue='USD',netIncome=None,epsDiluted=None,operatingIncome=None),
            fieldProofs=dict(revenue=dict(sourceURL=identity['sourceDocumentId'],sourceSha256=digest,
                sourcePublicationDate='2020-01-30',currency='USD',printedValue='50',unitsScale=1,totalScopeConfirmed=True)),
            periodProof=dict(printedYear=2019,startDateBasis='calendar Q4',endDateLiteralPrinted=True))
        return dict(version='fixture',candidates=[candidate],legacyTargetAssociations=[],
            legacyRecoveryAccounting=dict(exactRecoveredBefore=189,exactRecoveredAfter=189))
    def test_existing_pack_and_null_accessions_are_preserved_in_versioned_artifact(self):
        s=self.service()
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);pack=self.pack(root);path=root/'pack.json';path.write_text(json.dumps(pack))
            before=path.read_bytes();value=s.build(path)
            self.assertEqual(value['sourcePackSha256'],bridge.hashlib.sha256(before).hexdigest())
            self.assertIsNone(value['rows'][0]['candidate']['canonicalIdentity']['secAccessionNumber'])
            self.assertIsNone(value['rows'][0]['candidate']['availability']['systemAcquiredAt'])
            self.assertEqual(path.read_bytes(),before)
            self.assertFalse(value['historicalSystemCaptureProved'])
    def test_resealed_value_tampering_cannot_pass_exact_source_rebuild(self):
        s=self.service()
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);path=root/'pack.json';path.write_text(json.dumps(self.pack(root)))
            body=s.build(path);artifact=root/'admission.json';artifact.write_text(json.dumps(body));s.verify(artifact)
            body['rows'][0]['candidate']['values']['revenue']='999'
            body.pop('contentSha256');s.forward.seal(body);artifact.write_text(json.dumps(body))
            with self.assertRaisesRegex(ValueError,'rebuild'):
                s.verify(artifact)
    def test_source_bytes_changed_after_preparation_are_rejected(self):
        s=self.service()
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);pack=self.pack(root);path=root/'pack.json';path.write_text(json.dumps(pack))
            body=s.build(path);artifact=root/'admission.json';artifact.write_text(json.dumps(body))
            Path(pack['candidates'][0]['sourceLocalPath']).write_text('changed')
            with self.assertRaisesRegex(ValueError,'hash drift'):
                s.verify(artifact)
