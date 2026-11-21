"""Independent arithmetic and input checks of saved forecasts; no model fitting."""
import csv, hashlib, json, math
from collections import defaultdict
from pathlib import Path
from statistics import mean

ROOT=Path('tmp/quant-20d-error-research-v2-20261031')

def verify():
    path=ROOT/'result.json';r=json.loads(path.read_text(encoding='utf-8'))
    assert r['databaseWrites']==0 and r['promotionAllowed'] is False
    for p,h in r['inputHashes'].items():assert hashlib.sha256(Path(p).read_bytes()).hexdigest()==h,p
    groups=defaultdict(list);keys=set()
    for p in r['predictions']:
        key=(p['day'],p['target'],p['family'],p['symbol']);assert key not in keys;keys.add(key)
        assert p['day']<p['entry']<p['exit']
        assert all(math.isfinite(p[k]) for k in ('predicted','actual'))
        groups[p['day'],p['target'],p['family']].append(p)
    daily={(d['day'],d['target'],d['family']):d for d in r['daily']}
    for key,rows in groups.items():
        assert len(rows)==101
        e=[p['predicted']-p['actual'] for p in rows]
        for k,value in [('maePp',mean(abs(v) for v in e)),('rmsePp',math.sqrt(mean(v*v for v in e))),('biasPp',mean(e))]:
            assert math.isclose(daily[key][k],value,abs_tol=1e-10),(key,k)
        base=groups[key[0],key[1],'V9']
        assert {(p['symbol'],p['actual']) for p in rows}=={(p['symbol'],p['actual']) for p in base}
    for s in r['summary']:
        ds=[d for d in r['daily'] if d['target']==s['target'] and d['family']==s['family']]
        assert len(ds)==s['dates']==11 and s['rows']==1111
        assert math.isclose(s['maePp'],mean(d['maePp'] for d in ds),abs_tol=1e-10)
        delta=[d['maePp']-daily[d['day'],d['target'],'V9']['maePp'] for d in ds]
        assert math.isclose(s['maeDeltaVsV9Pp'],mean(delta),abs_tol=1e-10)
        assert s['betterDates']==sum(d<0 for d in delta)
    for f in r['fits']:
        assert len(f['trainingDates'])>=8 and max(f['trainingDates'])<f['day']
        assert f['maximumTrainingExit']<f['day']
    result=dict(status='PASS',savedForecastRows=len(keys),matchedDates=11,matchedSecuritiesPerDate=101,
                sourceHashesVerified=len(r['inputHashes']),sameCohortAndArithmeticVerified=True,
                matureTrainingVerified=True,databaseWrites=0,promotionAllowed=False,
                resultSha256=hashlib.sha256(path.read_bytes()).hexdigest())
    (ROOT/'verification.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result,indent=2))

if __name__=='__main__':verify()
