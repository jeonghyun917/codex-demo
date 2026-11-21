"""Immutable public raw snapshot; never promotes prices or claims live predictions."""
import argparse
import io
import json
import time
from datetime import datetime,timezone
from pathlib import Path
from urllib.parse import quote
import pandas as pd
import acquire_sp500_free_followup as client
from build_sp500_daily_dataset import digest,price_flags,read_csv
from recover_sp500_daily_dataset import validate_chart

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/sp500-forward-snapshot-20261029'
FREEZE=ROOT/'output/sp500-filter-validation-20261027'
BASE=ROOT/'output/sp500-actions-20261028-retry/dataset'
END='2026-10-28'


def mature_decisions(sessions,start):
    days=sorted(set(sessions))
    return [d for i,d in enumerate(days) if d>=start and i+20<len(days)]


def chart_url(symbol):
    stamp=lambda d:int(datetime.fromisoformat(d).replace(tzinfo=timezone.utc).timestamp())
    return f'https://query1.finance.yahoo.com/v8/finance/chart/{quote(symbol)}?period1={stamp("2026-10-01")}&period2={stamp("2026-10-29")}&interval=1d&events=div%2Csplits&includeAdjustedClose=true'


def acquire():
    client.OUT=OUT
    (OUT/'raw').mkdir(parents=True,exist_ok=True)
    target=OUT/'acquisition.json';partial=OUT/'acquisition.partial.json'
    if target.exists():raise ValueError('Snapshot already complete; use audit only')
    records=json.loads(partial.read_text()) if partial.exists() else []
    done={r['name'] for r in records}
    def fetch(name,url,ext):
        if name in done:return
        path=OUT/'raw'/f'{name}.{ext}'
        if path.exists():raise ValueError('Unmanifested response; preserve and investigate')
        r=client.fetch(url,path);r['name']=name;records.append(r)
        partial.write_text(json.dumps(records,indent=2),encoding='utf-8')
        print(name,r.get('status'),r.get('bytes'),flush=True)
    fetch('roster','https://en.wikipedia.org/wiki/List_of_S%26P_500_companies','html')
    fetch('nyse-calendar','https://www.nyse.com/markets/hours-calendars','html')
    fetch('fred-index','https://fred.stlouisfed.org/graph/fredgraph.csv?id=SP500&cosd=2026-10-01&coed=2026-10-28','csv')
    roster=pd.read_html(io.StringIO((OUT/'raw/roster.html').read_text(encoding='utf-8')))[0]
    symbols=sorted(set(roster.Symbol.str.replace('.','-',regex=False)))
    assert len(symbols)==len(roster) and 490<=len(symbols)<=510
    roster.to_csv(OUT/'current_roster.csv',index=False)
    for symbol in symbols:
        if any(r.get('status') in (403,429) or r.get('blocked') for r in records if r['name'].startswith('yahoo-')):break
        fetch('yahoo-'+symbol,chart_url(symbol),'json')
        time.sleep(.1)
    target.write_text(json.dumps(records,indent=2),encoding='utf-8')


def audit():
    gate=json.loads((FREEZE/'prospective_gate.json').read_text())
    assert digest(FREEZE/'protocol.json')==gate['protocol_sha256']
    records=json.loads((OUT/'acquisition.json').read_text())
    supplement=OUT/'retry.json'
    if supplement.exists():records+=json.loads(supplement.read_text())
    for r in records:
        if r.get('sha256'):assert digest(OUT/r['path'])==r['sha256']
    records=list({r['name']:r for r in records}.values())
    old=pd.read_csv(ROOT/'output/sp500-extension-20261027/current_roster.csv')
    new=pd.read_csv(OUT/'current_roster.csv')
    normalize=lambda f:set(f.Symbol.str.replace('.','-',regex=False))
    before=normalize(old);after=normalize(new)
    fred=pd.read_csv(OUT/'raw/fred-index.csv')
    fred=fred[pd.to_numeric(fred.SP500,errors='coerce').notna()]
    sessions=sorted(d for d in fred.observation_date if d<=END)
    oldbars={(r['symbol'],r['trade_date']):r for r in read_csv(BASE/'inputs/prices.csv.gz') if r['trade_date']>='2026-10-01'}
    inventory=[];changes=[]
    for r in records:
        if not r['name'].startswith('yahoo-'):continue
        s=r['name'][6:];row=dict(symbol=s,status='UNAVAILABLE',valid_bars=0,latest='',ohlc_revisions=0,events='')
        if r.get('sha256'):
            try:
                chart=json.loads((OUT/r['path']).read_bytes())['chart']['result'][0]
                bars=validate_chart(s,chart)
                bars={d:b for d,b in bars.items() if d<=END and not price_flags(b)}
                row.update(status='RAW_VALIDATED_NOT_PROMOTED',valid_bars=len(bars),latest=max(bars,default=''),events=json.dumps(chart.get('events',{})))
                for d,b in bars.items():
                    oldbar=oldbars.get((s,d))
                    if oldbar and any(abs(float(b[k])-float(oldbar[k]))>.00501 for k in ('open_price','high_price','low_price','close_price')):
                        row['ohlc_revisions']+=1
                        changes.append(dict(symbol=s,date=d,old=json.dumps(oldbar),new=json.dumps(b)))
            except (KeyError,ValueError,TypeError,IndexError) as exc:row['status']=str(exc)
        inventory.append(row)
    pd.DataFrame(inventory).to_csv(OUT/'price_inventory.csv',index=False)
    pd.DataFrame(changes,columns=['symbol','date','old','new']).to_csv(OUT/'price_revision_candidates.csv',index=False)
    # Session/date boundary is conservative: first date after freeze's NY date.
    # Calendar is still a FRED proxy; no unverified exchange session is promoted.
    freeze_day=gate['frozen_at_utc'][:10]
    candidates=[d for d in sessions if d>freeze_day]
    result=dict(status='AWAITING_MATURE_DATA_AND_PIT_VALIDATION',snapshot_end=END,
        protocol_hash_verified=True,frozen_at_utc=gate['frozen_at_utc'],
        original_protocol_unchanged=True,roster_count=len(after),roster_added=sorted(after-before),roster_removed=sorted(before-after),
        raw_price_responses=sum(r.get('sha256') is not None for r in records if r['name'].startswith('yahoo-')),
        latest_price_date=max((r['latest'] for r in inventory),default=''),fred_last_date=max(sessions),
        completed_dates_strictly_after_freeze_date=candidates,
        mature_decisions=mature_decisions(sessions,candidates[0]) if candidates else [],
        ohlc_revision_candidates=len(changes),promotion=False,live_predictions_recorded=False,
        blockers=['20-session outcomes not yet mature','Current roster snapshot is not historical PIT proof',
          'Corporate events and overlap revisions must be reviewed before promotion',
          'No timestamped pre-order prediction ledger exists for these past dates'],
        note='Raw collection only. No new account return, no DB changes, no scheduled job. Same-day freeze session left unclassified, not silently redefined.')
    (OUT/'readiness.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result,indent=2),flush=True)


def retry_timeouts():
    client.OUT=OUT
    target=OUT/'retry.json'
    if target.exists():raise ValueError('Only one bounded retry batch is allowed')
    records=[]
    for r in json.loads((OUT/'acquisition.json').read_text()):
        if r.get('status') is None and 'timed out' in r.get('error',''):
            path=OUT/'raw'/(r['name']+'-retry.json')
            assert not path.exists()
            new=client.fetch(r['url'],path);new['name']=r['name'];records.append(new)
    target.write_text(json.dumps(records,indent=2),encoding='utf-8')


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--acquire',action='store_true');parser.add_argument('--retry-timeouts',action='store_true');args=parser.parse_args()
    if args.acquire:acquire()
    if args.retry_timeouts:retry_timeouts()
    audit()
