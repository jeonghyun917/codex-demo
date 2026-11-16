"""One credential-free original-provider request per member; no retries/bypass."""
import hashlib,json,time,urllib.request
from pathlib import Path
from datetime import datetime,timezone
from quant_long_history_study import ROSTER

ROOT=Path('tmp/quant-older-actions-20261031-v1')
ROOT.mkdir(exist_ok=True,parents=True)
for symbol in ROSTER:
    path=ROOT/(symbol+'.json')
    if path.exists():raise ValueError('Refuse original overwrite '+symbol)
    url=f'https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?period1=1451606400&period2=1577836800&interval=1mo&events=splits'
    with urllib.request.urlopen(url,timeout=30) as response:
        raw=response.read();status=response.status
    parsed=json.loads(raw);result=parsed['chart']['result'][0]
    if status!=200 or result['meta']['symbol']!=symbol:raise ValueError('Source identity failure')
    with path.open('xb') as stream:stream.write(raw)
    evidence=dict(symbol=symbol,url=url,status=status,completedUtc=datetime.now(timezone.utc).isoformat(),sha256=hashlib.sha256(raw).hexdigest(),events=result.get('events',{}).get('splits',{}))
    with (ROOT/(symbol+'-request.json')).open('x',encoding='utf-8') as stream:json.dump(evidence,stream,indent=2)
    print(symbol,len(evidence['events']),flush=True)
    time.sleep(.4)
