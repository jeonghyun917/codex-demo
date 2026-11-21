"""Refresh only the published watchlist tracking from verified daily-bar extensions."""
import argparse
from collections import Counter
from datetime import datetime,timedelta,timezone
import json
import subprocess
from pathlib import Path
import time
from urllib.parse import quote
from zoneinfo import ZoneInfo
import acquire_sp500_free_followup as client
from build_sp500_daily_dataset import digest
from build_sp500_paper_start import approve_extension
from quant_daily_forward import Database,check_provider,collection_lock,write_record
from quant_forward_validation import timestamp
import sp500_paper_ledger as ledger
from sp500_watchlist_tracking import LABELS,load_prices,track_symbol


def refresh_row(previous,sessions,prices,asof):
    if previous['status'] not in ('OPEN','WAITING_ENTRY','DATA_GAP'):
        return dict(previous)
    row=track_symbol(previous['symbol'],sessions,prices,asof)
    money=lambda v:'-' if v is None else f'${v:,.2f}'
    pct=lambda v:'-' if v is None else f'{v:+.2f}%'
    return dict(previous,**row,label=LABELS[row['status']],entryPriceText=money(row['entryPrice']),
        exitPriceText=money(row['exitPrice']),net10Text=pct(row['net10']),net20Text=pct(row['net20']))


def publish_tracking(db,original_hex,payload):
    from paper_view_publication_guard import validate_publication
    validate_publication(payload,json.loads(bytes.fromhex(original_hex)))
    body=json.dumps(payload,ensure_ascii=False,allow_nan=False).encode().hex()
    sql=f"UPDATE stock_dashboard_view_snapshot SET payload_json=CONVERT(0x{body} USING utf8mb4),generated_at=NOW(),source='WATCHLIST_TRACKING_REFRESH' WHERE index_code='SP500' AND view_version='SP500_PAPER_WEB_V1' AND HEX(payload_json)='{original_hex}';"
    subprocess.run([str(db.client),'-h','127.0.0.1','-u','root','--default-character-set=utf8mb4','king_yurina'],
        input=sql,text=True,encoding='utf-8',check=True)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--publish',action='store_true')
    args=parser.parse_args();out=args.out.resolve()
    if out.exists():raise ValueError('Use a new immutable output directory')
    p=ledger.protocol(ledger.DEFAULT);records=ledger.decisions(ledger.DEFAULT,p)
    before={str(f.relative_to(ledger.DEFAULT)):digest(f) for f in ledger.DEFAULT.rglob('*') if f.is_file()}
    now=datetime.now(timezone.utc)
    end=max(s['session'] for s in p['calendar'] if timestamp(s['closeUtc'])<=now)
    sql="SELECT HEX(payload_json) FROM stock_dashboard_view_snapshot WHERE index_code='SP500' AND view_version='SP500_PAPER_WEB_V1'"
    db=Database();original_hex=db.query(sql)[0][0]
    payload=json.loads(bytes.fromhex(original_hex))
    if {d['date'] for d in payload['days']}!={r['anchor'] for r in records}:raise ValueError('Unknown published decisions')
    if any(not d.get('tracking',{}).get('available') for d in payload['days']):raise ValueError('Publish initial verified tracking first')
    if any(d['tracking']['asof']>=end for d in payload['days']):raise ValueError('No newer complete session')
    active={r['symbol'] for d in payload['days'] for r in d['tracking']['rows'] if r['status'] in ('OPEN','WAITING_ENTRY','DATA_GAP')}
    prices,base_end=load_prices(records[-1],active,records[0]['anchor'])
    sessions=[s['session'] for s in p['calendar']]
    required=[d for d in sessions if base_end<d<=end]
    out.mkdir(parents=True);(out/'raw').mkdir();client.OUT=out
    write_record(out/'before.json',payload)
    manifest=[];audit=[]
    from recover_sp500_daily_dataset import validate_chart
    with collection_lock(ledger.ROOT/'data/quant-daily/collection.lock'):
        check_provider(db)
        existing=db.query(f"SELECT COUNT(*) FROM stock_candle_daily WHERE trade_date>'{base_end}'")
        if int(existing[0][0]):raise ValueError('New DB prices exist; reuse/reconcile before provider collection')
        for symbol in sorted(active):
            start=(datetime.fromisoformat(base_end)-timedelta(days=35)).strftime('%Y-%m-%d')
            stamp=lambda d:int(datetime.fromisoformat(d).replace(tzinfo=timezone.utc).timestamp())
            until=(datetime.fromisoformat(end)+timedelta(days=1)).strftime('%Y-%m-%d')
            url=f'https://query1.finance.yahoo.com/v8/finance/chart/{quote(symbol)}?period1={stamp(start)}&period2={stamp(until)}&interval=1d&events=div%2Csplits&includeAdjustedClose=true'
            path=out/'raw'/f'{symbol}.json'
            source=client.fetch(url,path);source['symbol']=symbol;manifest.append(source)
            write_record(out/f'acquisition-{symbol}.json',source)
            if source.get('status') in (403,429) or source.get('blocked'):
                raise ValueError('Provider denied access; no retries or alternate-host bypass')
            try:
                if source.get('status')!=200 or source.get('sha256')!=digest(path):raise ValueError('UNAVAILABLE_SOURCE')
                chart=json.loads(path.read_bytes())['chart']['result'][0]
                incoming=validate_chart(symbol,chart)
                for kind,events in chart.get('events',{}).items():
                    for event in events.values():
                        date=datetime.fromtimestamp(event['date'],ZoneInfo('America/New_York')).date().isoformat()
                        if base_end<date<=end and kind!='dividends':raise ValueError('CORPORATE_ACTION_REVIEW_REQUIRED')
                old={d:dict(**{k+'_price':b[k] for k in ('open','high','low','close')},volume=b['volume'])
                     for (s,d),b in prices.items() if s==symbol and not b.get('execution_blocked')}
                added=approve_extension(old,incoming,required)
                for day,b in added.items():
                    prices[symbol,day]=dict(**{k:float(b[k+'_price']) for k in ('open','high','low','close')},
                        volume=float(b['volume']),execution_blocked=False)
                audit.append(dict(symbol=symbol,status='VERIFIED',added=len(added),overlap=len(set(old)&set(incoming))))
            except (ValueError,KeyError,TypeError,OSError) as exc:
                audit.append(dict(symbol=symbol,status='WITHHELD',reason=str(exc)))
            print(json.dumps(audit[-1]),flush=True)
            time.sleep(.3)
    write_record(out/'acquisition.json',manifest)
    write_record(out/'price-audit.json',audit)
    for day in payload['days']:
        record=next(r for r in records if r['anchor']==day['date'])
        window=sessions[sessions.index(record['entrySession']):]
        tracking=day['tracking']
        tracking['rows']=[refresh_row(r,window,prices,end) for r in tracking['rows']]
        counts=Counter(r['status'] for r in tracking['rows'])
        tracking.update(asof=end,tp=counts['TP'],sl=counts['SL']+counts['SL_GAP'],time=counts['TIME'],
            completed=sum(counts[s] for s in ('TP','SL','SL_GAP','TIME')),open=counts['OPEN'],waiting=counts['WAITING_ENTRY'],
            blocked=sum(counts[s] for s in ('DATA_GAP','BASIS_MISMATCH','CALENDAR_MISSING')),
            extension=dict(path=str(out),acquisitionSha256=digest(out/'acquisition.json'),auditSha256=digest(out/'price-audit.json')))
    payload['syncedAt']=now.isoformat()
    old=json.loads(bytes.fromhex(original_hex))
    assert payload['accounts']==old['accounts']
    for a,b in zip(payload['days'],old['days']):assert a['predictions']==b['predictions']
    assert before=={str(f.relative_to(ledger.DEFAULT)):digest(f) for f in ledger.DEFAULT.rglob('*') if f.is_file()}
    for source in manifest:
        if source.get('sha256'):assert digest(out/source['path'])==source['sha256']
    write_record(out/'after.json',payload)
    if args.publish:
        if db.query(sql)[0][0]!=original_hex:raise ValueError('Published snapshot changed concurrently')
        publish_tracking(db,original_hex,payload)
        assert json.loads(bytes.fromhex(db.query(sql)[0][0]))==payload
    write_record(out/'verification.json',dict(status='PASS',published=args.publish,ledgerHashes=before,
        accountsUnchanged=True,predictionsUnchanged=True,asof=end,afterSha256=digest(out/'after.json'),
        codeSha256=digest(Path(__file__))))
    print(json.dumps({d['date']:{k:d['tracking'][k] for k in ('asof','tp','sl','time','open','waiting','blocked')} for d in payload['days']},indent=2))


if __name__=='__main__':main()
