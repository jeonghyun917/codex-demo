"""Immutable, original-source-rebuilt admission artifact. Actual DB admission time is recorded by Java, never backdated."""
import argparse
from copy import deepcopy
from datetime import datetime,timezone
import json
from pathlib import Path
import quant_forward_validation as forward
import quant_source_bridge as bridge

VERSION='SOURCE_FINANCIAL_BRIDGE_V1_20261105'

def build(pack_path, observed=None):
    pack_path=Path(pack_path).resolve();raw=pack_path.read_bytes();pack=json.loads(raw)
    bridge.build_plan(pack)  # Exact source bytes, printed field/scale/unit/period, unique identity and associations.
    observed=observed or forward.iso(datetime.now(timezone.utc))
    if forward.timestamp(observed)>forward.now_utc():raise ValueError('Future source observation')
    rows=[]
    for source in pack['candidates']:
        candidate=deepcopy(source);identity=candidate['canonicalIdentity']
        candidate['canonicalIdentitySha256']=bridge.canonical_digest(identity)
        candidate.setdefault('sourceForm','ISSUER_RELEASE' if identity['secAccessionNumber'] is None else '10-Q')
        rows.append(dict(identitySha256=candidate['canonicalIdentitySha256'],symbol=identity['symbol'],
            publicationDate=identity['publicationDate'],candidate=candidate))
    return forward.seal(dict(format='QUANT_SOURCE_FINANCIAL_ADMISSION_V1',version=VERSION,
        observedSourceAtUtc=observed,sourcePackPath=str(pack_path),sourcePackSha256=forward.digest(raw),rows=rows,
        legacyTargetAssociations=deepcopy(pack.get('legacyTargetAssociations',[])),
        legacyRecoveryAccounting=deepcopy(pack.get('legacyRecoveryAccounting',{})),
        historicalSystemCaptureProved=False,legacyStandardWrites=False,forecastWrites=False,
        knowledgePolicy='Original public date is not system receipt time. DB admission records its actual current UTC time.'))

def verify(path,expected_sha=None):
    path=Path(path);raw=path.read_bytes();sha=forward.digest(raw)
    if expected_sha and expected_sha!=sha:raise ValueError('Admission bytes changed')
    body=json.loads(raw);forward.verify_seal(body)
    if body!=build(body['sourcePackPath'],body['observedSourceAtUtc']):raise ValueError('Admission differs from original-source rebuild')
    if path.read_bytes()!=raw:raise ValueError('Admission changed during verification')
    return sha

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--pack',type=Path);p.add_argument('--out',type=Path)
    p.add_argument('--verify',type=Path);p.add_argument('--expected-sha');a=p.parse_args()
    if a.verify:
        print('SOURCE_ADMISSION_VERIFIED '+verify(a.verify,a.expected_sha));return
    if not a.pack or not a.out:p.error('Creation requires --pack and --out')
    value=build(a.pack);forward.write_immutable(a.out,value);print('SOURCE_ADMISSION_PREPARED rows='+str(len(value['rows'])))
if __name__=='__main__':main()
