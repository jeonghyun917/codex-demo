"""Resumable original-model daily pipeline. No strategy changes or broker orders."""
import argparse
from collections import defaultdict
from datetime import datetime,timedelta,timezone
import io
import json
import math
from pathlib import Path
import shutil
import subprocess
import sys
import time
from urllib.parse import quote
from zoneinfo import ZoneInfo
import pandas as pd
import acquire_sp500_free_followup as client
from build_sp500_daily_dataset import read_csv,write_csv,digest,members_on
from build_sp500_paper_start import approve_extension
from quant_daily_forward import Database,check_provider,collection_lock,write_record
from recover_sp500_daily_dataset import validate_chart
import sp500_paper_ledger as ledger


def read(path):return json.loads(Path(path).read_text(encoding='utf-8'))


def verified_cached(out,name):
    receipt=out/'sources'/f'{name}.json'
    if not receipt.exists():return None
    source=read(receipt)
    if source.get('status')!=200 or source.get('blocked'):raise ValueError('PRIOR_SOURCE_FAILURE: '+name)
    if digest(out/source['path'])!=source['sha256']:raise ValueError('CACHED_SOURCE_CHANGED: '+name)
    return source


def extend_unchanged_membership(rows,aliases,roster,old_end,anchor):
    prior={aliases.get(s,s) for s in members_on(rows,old_end)}
    if prior!=roster:raise ValueError('MEMBERSHIP_CHANGE_REQUIRES_REVIEW')
    result=[dict(r) for r in rows]
    for r in result:
        if r['end_date']==old_end and r['is_member'].lower()=='true':
            r['end_date']=anchor
            r['provenance_ids']+=';daily-unchanged-roster-'+anchor
    return result


def acquire(out,config,protocol):
    if (out/'acquisition.json').exists():
        for name in read(out/'acquisition.json'):verified_cached(out,name)
        return
    anchor=config['anchor'];base=Path(config['base']);client.OUT=out
    (out/'raw').mkdir(exist_ok=True);(out/'sources').mkdir(exist_ok=True)
    with collection_lock(ledger.ROOT/'data/quant-daily/collection.lock'):
        ledger.capture_window(protocol['calendar'],ledger.now_utc(),anchor)
        db=Database();check_provider(db)
        if int(db.query(f"SELECT COUNT(*) FROM stock_candle_daily WHERE trade_date>'{config['baseEnd']}'")[0][0]):
            raise ValueError('NEW_DB_PRICES_REQUIRE_REUSE_RECONCILIATION')
        def fetch(name,url,extension):
            prior=verified_cached(out,name)
            if prior:return prior
            path=out/'raw'/f'{name}.{extension}'
            if path.exists():raise ValueError('UNRECEIPTED_SOURCE_REQUIRES_REVIEW: '+name)
            source=client.fetch(url,path);source['name']=name
            write_record(out/'sources'/f'{name}.json',source)
            if source.get('status')!=200 or source.get('blocked'):raise ValueError('SOURCE_FAILED_NO_AUTO_RETRY: '+name)
            return source
        fetch('roster','https://en.wikipedia.org/wiki/List_of_S%26P_500_companies','html')
        fetch('membership-history','https://en.wikipedia.org/wiki/Historical_components_of_the_S%26P_500','html')
        frame=pd.read_html(io.StringIO((out/'raw/roster.html').read_text(encoding='utf-8')))[0]
        roster=set(frame.Symbol.str.replace('.','-',regex=False))
        if len(roster)!=len(frame) or not 490<=len(roster)<=510:raise ValueError('INVALID_ROSTER')
        aliases={r['symbol']:r['provider_symbol'] for r in read_csv(base/'inputs/aliases.csv')}
        extend_unchanged_membership(read_csv(base/'inputs/sp500_membership_intervals.csv'),aliases,roster,config['baseEnd'],anchor)
        history=pd.read_html(io.StringIO((out/'raw/membership-history.html').read_text(encoding='utf-8')))[0]
        effective=pd.to_datetime(history[('Effective Date','Effective Date')],errors='coerce')
        if ((effective>pd.Timestamp(config['baseEnd']))&(effective<=pd.Timestamp(anchor))).any():
            raise ValueError('INTERVENING_MEMBERSHIP_EVENT_REQUIRES_REVIEW')
        frame.to_csv(out/'current_roster.csv',index=False)
        reuse=Path(config['reuse']) if config.get('reuse') else None
        saved={}
        if reuse:
            for source in read(reuse/'acquisition.json'):
                if source.get('status')==200 and source.get('sha256')==digest(reuse/source['path']):saved[source['symbol']]=source
        stamp=lambda d:int(datetime.fromisoformat(d).replace(tzinfo=timezone.utc).timestamp())
        start=(datetime.fromisoformat(config['baseEnd'])-timedelta(days=40)).strftime('%Y-%m-%d')
        end=(datetime.fromisoformat(anchor)+timedelta(days=1)).strftime('%Y-%m-%d')
        for i,symbol in enumerate(['^GSPC']+sorted(roster),1):
            name='yahoo-'+('INDEX' if symbol=='^GSPC' else symbol)
            if not verified_cached(out,name) and symbol in saved:
                source=saved[symbol];chart=read(reuse/source['path'])['chart']['result'][0]
                incoming=validate_chart(symbol,chart)
                if anchor in incoming:
                    target=out/'raw'/f'{name}.json';shutil.copyfile(reuse/source['path'],target)
                    write_record(out/'sources'/f'{name}.json',dict(source,name=name,path=str(target.relative_to(out)),reusedFrom=str(reuse/source['path'])))
            url=f'https://query1.finance.yahoo.com/v8/finance/chart/{quote(symbol)}?period1={stamp(start)}&period2={stamp(end)}&interval=1d&events=div%2Csplits&includeAdjustedClose=true'
            fetch(name,url,'json')
            if i%25==0:print(f'acquire {i}/{len(roster)+1}',flush=True)
            time.sleep(.2)
        names=['roster','membership-history']+['yahoo-'+('INDEX' if s=='^GSPC' else s) for s in ['^GSPC']+sorted(roster)]
        write_record(out/'acquisition.json',names)


def build(out,config,protocol):
    data=out/'dataset';base=Path(config['base']);anchor=config['anchor'];old_end=config['baseEnd']
    if (data/'verification.json').exists():
        ledger.checked_data(data);return
    for name in read(out/'acquisition.json'):verified_cached(out,name)
    roster=set(pd.read_csv(out/'current_roster.csv').Symbol.str.replace('.','-',regex=False))
    aliases={r['symbol']:r['provider_symbol'] for r in read_csv(base/'inputs/aliases.csv')}
    intervals=extend_unchanged_membership(read_csv(base/'inputs/sp500_membership_intervals.csv'),aliases,roster,old_end,anchor)
    new_days=[r['session'] for r in protocol['calendar'] if old_end<r['session']<=anchor]
    calendar=read_csv(base/'inputs/calendar.csv.gz');prior={r['trade_date']:float(r['benchmark_level']) for r in calendar}
    chart=read(out/'raw/yahoo-INDEX.json')['chart']['result'][0]
    if any(chart['meta'].get(k)!=v for k,v in dict(symbol='^GSPC',currency='USD',instrumentType='INDEX',exchangeTimezoneName='America/New_York',dataGranularity='1d').items()):raise ValueError('INDEX_IDENTITY_MISMATCH')
    levels={datetime.fromtimestamp(t,ZoneInfo('America/New_York')).date().isoformat():round(float(c),2) for t,c in zip(chart['timestamp'],chart['indicators']['quote'][0]['close'])}
    shared=set(levels)&set(prior)
    if len(shared)<15 or any(abs(prior[d]-levels[d])>=.011 for d in shared):raise ValueError('INDEX_BASIS_CONFLICT')
    if any(d not in levels or not math.isfinite(levels[d]) or levels[d]<=0 for d in new_days):raise ValueError('MISSING_INDEX_BAR')
    calendar += [dict(trade_date=d,benchmark_level=levels[d],source='YAHOO_GSPC_OVERLAP_VERIFIED') for d in new_days]
    prices=read_csv(base/'inputs/prices.csv.gz');fields=list(prices[0]);by_symbol=defaultdict(dict)
    for row in prices:by_symbol[row['symbol']][row['trade_date']]=row
    audit=[]
    for symbol in sorted(roster):
        chart=read(out/'raw'/f'yahoo-{symbol}.json')['chart']['result'][0]
        incoming=validate_chart(symbol,chart)
        for kind,items in chart.get('events',{}).items():
            for event in items.values():
                day=datetime.fromtimestamp(event['date'],ZoneInfo('America/New_York')).date().isoformat()
                if old_end<day<=anchor and kind!='dividends':raise ValueError('ACTION_REVIEW_REQUIRED: '+symbol)
        added=approve_extension(by_symbol[symbol],incoming,new_days)
        for day,bar in added.items():prices.append(dict(symbol=symbol,trade_date=day,**bar,source='yahoo-paper-daily',updated_at=''))
        audit.append(dict(symbol=symbol,added=len(added)))
    # A partial build may be recreated from these immutable source receipts; frozen decisions cannot.
    inputs=data/'inputs';inputs.mkdir(parents=True,exist_ok=True)
    for path in (base/'inputs').iterdir():
        if path.is_file():shutil.copyfile(path,inputs/path.name)
    write_csv(inputs/'prices.csv.gz',sorted(prices,key=lambda r:(r['symbol'],r['trade_date'])),fields)
    write_csv(inputs/'calendar.csv.gz',calendar,list(calendar[0]))
    write_csv(inputs/'sp500_membership_intervals.csv',intervals,list(intervals[0]))
    overrides=read_csv(inputs/'quality_overrides.csv')
    for row in overrides:
        if row['end_date']==old_end:row['end_date']=anchor
    write_csv(inputs/'quality_overrides.csv',overrides,list(overrides[0]))
    meta=read(inputs/'manifest.json');meta['coverage']['lastDate']=anchor
    meta['dailyExtension']=dict(parent=str(base),anchor=anchor,sourceManifestSha256=digest(out/'acquisition.json'),strategyChanged=False,
        limitations=['Provider-adjusted daily prices; no independent latest benchmark confirmation','Historical data limitations remain'])
    (inputs/'manifest.json').write_text(json.dumps(meta,indent=2),encoding='utf-8')
    (out/'integration.json').write_text(json.dumps(audit,indent=2),encoding='utf-8')
    del prices,by_symbol
    for script in ('build_sp500_daily_dataset.py','verify_sp500_daily_dataset.py'):
        args=[sys.executable,str(ledger.ROOT/'scripts'/script),'--out',str(data)]
        if script.startswith('build'):args+=['--offline']
        with (out/(script+'.log')).open('w',encoding='utf-8') as log:subprocess.run(args,stdout=log,stderr=subprocess.STDOUT,check=True)
    print('dataset verified',flush=True)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path);parser.add_argument('--reuse',type=Path)
    parser.add_argument('--run',action='store_true',help='Choose the latest completed-session run automatically; otherwise show its read-only plan')
    parser.add_argument('--stage',choices=['acquire','build','capture','publish','all'],default='all')
    args=parser.parse_args()
    p=ledger.protocol(ledger.DEFAULT)
    if args.out is None:
        records=ledger.decisions(ledger.DEFAULT,p)
        anchor=max(s['session'] for s in p['calendar'] if ledger.timestamp(s['closeUtc'])<=ledger.now_utc())
        existing=next((r for r in records if r['anchor']==anchor),None)
        out=Path(existing['datasetPath']).parent if existing else ledger.ROOT/'output/sp500-paper-daily'/anchor
        if not args.run:
            print(json.dumps(dict(anchor=anchor,runDirectory=str(out),captured=bool(existing),complete=(out/'complete.json').exists(),
                action='RESUME_OR_RUN_BEFORE_NEXT_OPEN',strategyChanged=False),indent=2));return
    else:out=args.out.resolve()
    out.mkdir(parents=True,exist_ok=True)
    if (out/'config.json').exists():config=read(out/'config.json')
    else:
        records=ledger.decisions(ledger.DEFAULT,p);anchor=max(s['session'] for s in p['calendar'] if ledger.timestamp(s['closeUtc'])<=ledger.now_utc())
        ledger.capture_window(p['calendar'],ledger.now_utc(),anchor)
        base=Path(records[-1]['datasetPath'])
        if records[-1]['anchor']>=anchor:raise ValueError('Anchor already captured; resume its existing run folder')
        config=dict(anchor=anchor,base=str(base),baseEnd=records[-1]['anchor'],baseHashes=ledger.dataset_hashes(base),reuse=str(args.reuse.resolve()) if args.reuse else None)
        write_record(out/'config.json',config)
    if ledger.dataset_hashes(Path(config['base']))!=config['baseHashes']:raise ValueError('BASE_CHANGED')
    try:
        if args.stage=='all' and (out/'complete.json').exists():
            publish_run(out,config,p);return
        if args.stage in ('acquire','all'):acquire(out,config,p)
        if args.stage in ('build','all'):build(out,config,p)
        if args.stage in ('capture','all'):
            target=ledger.DEFAULT/'decisions'/f"{config['anchor']}.json"
            if target.exists():
                records=ledger.decisions(ledger.DEFAULT,p)
                record=next(r for r in records if r['anchor']==config['anchor'])
                if record['inputHashes']!=ledger.dataset_hashes(out/'dataset'):raise ValueError('CAPTURE_DATASET_MISMATCH')
                print('capture already frozen; reused',flush=True)
            else:print(json.dumps(ledger.capture(ledger.DEFAULT,out/'dataset')),flush=True)
        if args.stage in ('publish','all'):publish_run(out,config,p)
    except Exception as error:
        write_record(out/('failure-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%f')+'.json'),dict(stage=args.stage,errorType=type(error).__name__,message=str(error),strategyChanged=False))
        raise


def publish_run(out,config,p):
    import publish_sp500_paper_view as view
    from refresh_sp500_watchlist_tracking import publish_tracking
    from paper_view_publication_guard import validate_publication
    records=ledger.decisions(ledger.DEFAULT,p)
    current=next(r for r in records if r['anchor']==config['anchor'])
    hashes=ledger.dataset_hashes(out/'dataset')
    if current['inputHashes']!=hashes:raise ValueError('CAPTURE_DATASET_MISMATCH')
    selected,ranks,audit=ledger.select(pd.DataFrame(current['predictions']).assign(date=config['anchor']))
    if ranks!=current['ranks'] or audit!=current['filterAudit']:raise ValueError('FILTER_REPLAY_MISMATCH')
    complete=out/'complete.json'
    if complete.exists():
        receipt=read(complete)
        if receipt['decisionSha256']!=current['contentSha256'] or receipt['datasetHashes']!=hashes:raise ValueError('COMPLETION_INPUT_CHANGED')
        if digest(out/'paper-web-after.json')!=receipt['snapshotSha256']:raise ValueError('COMPLETED_PAYLOAD_CHANGED')
        preparation=read(out/'publication-prepared.json')
        publish_prepared(Database(),preparation['originalHex'],read(out/'paper-web-after.json'),publish_tracking)
        if not (out/'verification.json').exists():
            write_record(out/'verification.json',dict(status='PASS',published=True,afterSha256=receipt['snapshotSha256'],
                ledgerHashes=receipt['ledgerHashes'],strategyChanged=False,decisionSha256=current['contentSha256'],asof=config['anchor']))
        print('already captured; current publication verified without collection or prediction',flush=True);return
    # Reuse an existing evaluation if the write completed before this process was interrupted.
    evaluations=[ledger.load(path) for path in (ledger.DEFAULT/'evaluations').glob('*.json')]
    expected=[r['contentSha256'] for r in records]
    if not any(r['decisionHashes']==expected and r['priceInputHashes']==hashes for r in evaluations):
        print(json.dumps(ledger.evaluate(ledger.DEFAULT,out/'dataset')),flush=True)
    frozen={str(path.relative_to(ledger.DEFAULT)):digest(path) for path in ledger.DEFAULT.rglob('*.json')}
    db=Database();prepared=out/'publication-prepared.json'
    if prepared.exists():
        body=read(prepared)
        if body['datasetHashes']!=hashes or body['ledgerHashes']!=frozen:raise ValueError('PREPARED_INPUT_CHANGED')
        if digest(out/'paper-web-after.json')!=body['afterSha256']:raise ValueError('PREPARED_PAYLOAD_CHANGED')
        payload=read(out/'paper-web-after.json');original_hex=body['originalHex']
    else:
        previous=db.query(SNAPSHOT_SQL)
        if not previous:raise ValueError('Existing view required; initialize paper view first')
        original_hex=previous[0][0];old=json.loads(bytes.fromhex(original_hex))
        payload=view.build(ledger.DEFAULT,view.company_names(out/'current_roster.csv'),out/'dataset')
        if any(not d['tracking']['available'] for d in payload['days']):raise ValueError('TRACKING_NOT_VERIFIED')
        validate_publication(payload,old)
        # Preparation is write-once. Missing receipt after a partial local write is checked before reuse.
        for name,value in [('paper-web-before.json',old),('paper-web-after.json',payload)]:
            path=out/name
            if path.exists():
                existing=read(path)
                if name=='paper-web-after.json':value['syncedAt']=existing['syncedAt']
                if existing!=value:raise ValueError('PARTIAL_PREPARATION_CONFLICT')
            else:write_record(path,value)
        body=dict(originalHex=original_hex,afterSha256=digest(out/'paper-web-after.json'),datasetHashes=hashes,ledgerHashes=frozen)
        write_record(prepared,body)
    publish_prepared(db,original_hex,payload,publish_tracking)
    if frozen!={str(path.relative_to(ledger.DEFAULT)):digest(path) for path in ledger.DEFAULT.rglob('*.json')}:
        raise ValueError('LEDGER_CHANGED_DURING_PUBLICATION')
    receipt=dict(status='COMPLETE',anchor=config['anchor'],decisionSha256=current['contentSha256'],
        recordedAtUtc=current['recordedAtUtc'],nextOpenUtc=current['nextOpenUtc'],protocolSha256=p['contentSha256'],
        analyzed=len(current['predictions']),candidateCount=len(current['ranks']),datasetHashes=hashes,
        snapshotSha256=digest(out/'paper-web-after.json'),ledgerHashes=frozen,strategyChanged=False)
    verification=dict(status='PASS',published=True,afterSha256=receipt['snapshotSha256'],ledgerHashes=frozen,
        strategyChanged=False,decisionSha256=current['contentSha256'],asof=config['anchor'])
    if (out/'verification.json').exists():
        if read(out/'verification.json')!=verification:raise ValueError('VERIFICATION_RECEIPT_CHANGED')
    else:write_record(out/'verification.json',verification)
    write_record(complete,receipt)
    print(json.dumps({k:v for k,v in receipt.items() if k not in ('datasetHashes','ledgerHashes')},indent=2),flush=True)


SNAPSHOT_SQL="SELECT HEX(payload_json) FROM stock_dashboard_view_snapshot WHERE index_code='SP500' AND view_version='SP500_PAPER_WEB_V1'"


def publish_prepared(db,original_hex,payload,writer):
    current=db.query(SNAPSHOT_SQL)
    if not current:raise ValueError('Publication disappeared')
    if json.loads(bytes.fromhex(current[0][0]))==payload:return
    if current[0][0]!=original_hex:raise ValueError('CONCURRENT_PUBLICATION_CHANGED')
    writer(db,original_hex,payload)
    if json.loads(bytes.fromhex(db.query(SNAPSHOT_SQL)[0][0]))!=payload:raise ValueError('PUBLICATION_READBACK_FAILED')


if __name__=='__main__':
    if '--run' in sys.argv or '--out' in sys.argv:
        with collection_lock(ledger.ROOT/'data/quant-daily/paper-daily.lock'):main()
    else:main()
