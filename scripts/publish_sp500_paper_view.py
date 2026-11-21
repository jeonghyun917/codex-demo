"""Publish a verified, sanitized ledger projection to the existing DB view table."""
import argparse
import csv
import hashlib
import gzip
import math
from datetime import datetime,timezone
import json
from pathlib import Path
import subprocess
import sp500_paper_ledger as ledger

VERSION='SP500_PAPER_WEB_V1'


def pct(value,scale=1):return '-' if value is None else f'{float(value)*scale:,.2f}%'
def money(value):return '-' if value is None else f'${float(value):,.2f}'


def reference_closes(record):
    """Exact-session closes from the hash-sealed prediction input, never latest quotes."""
    if not record.get('datasetPath'):return {}
    path=Path(record['datasetPath'])/'price_history.csv.gz'
    try:
        if hashlib.sha256(path.read_bytes()).hexdigest()!=record.get('inputHashes',{}).get(path.name):return {}
        result={};seen=set()
        with gzip.open(path,'rt',encoding='utf-8',newline='') as handle:
            for row in csv.DictReader(handle):
                if row['trade_date']!=record['anchor']:continue
                symbol=row['symbol']
                if symbol in seen:raise ValueError('Duplicate decision-day price')
                seen.add(symbol)
                value=float(row['close_price'])
                if row['price_usable']=='1' and math.isfinite(value) and value>0:result[symbol]=value
        return result
    except (OSError,ValueError,KeyError):return {}


def company_names(roster=None):
    result=subprocess.run([str(ledger.ROOT/'.tools/mariadb-11.8.2-winx64/bin/mariadb.exe'),
        '-h','127.0.0.1','-u','root','--default-character-set=utf8mb4','-N','-B','king_yurina',
        '-e',"SELECT JSON_OBJECT('symbol',s.symbol,'name',COALESCE(NULLIF(p.name,''),s.name)) FROM stock_symbol s LEFT JOIN company_profile p ON p.symbol=s.symbol"],
        capture_output=True,text=True,encoding='utf-8',check=True)
    names={r['symbol']:r['name'] for r in map(json.loads,result.stdout.splitlines()) if r['name']}
    if roster:
        with Path(roster).open(encoding='utf-8-sig',newline='') as handle:
            for row in csv.DictReader(handle):names.setdefault(row['Symbol'].replace('.','-'),row['Security'])
    return names


def project_day(record,names=None):
    prices=reference_closes(record)
    selected={x['symbol'] for x in record['ranks']}
    audit=next(iter(record.get('filterAudit',[])),{})
    top=audit.get('top_twenty')
    trend=audit.get('index_ma50')
    cap=audit.get('vol75')
    rows=[]
    for rank,x in enumerate(sorted(record['predictions'],key=lambda x:(-x['probability'],x['symbol'])),1):
        reasons=[]
        if top is not None and rank>top:reasons.append('확률 상위 20% 밖')
        if trend is not None and trend<=0:reasons.append('시장 추세 미충족')
        if cap is not None and x['vol20']>cap:reasons.append('변동성 초과')
        chosen=x['symbol'] in selected
        rows.append(dict(symbol=x['symbol'],rank=rank,probability=pct(x['probability'],100),
            companyName=(names or {}).get(x['symbol'],''),referenceClose=prices.get(x['symbol']),
            referenceDate=record['anchor'] if x['symbol'] in prices else '',
            volatility=pct(x['vol20'],100),high63=pct(x.get('high63'),100),selected=chosen,
            interest=top is not None and rank<=top,
            reason='조건 통과 · 체결 전 후보' if chosen else ' · '.join(reasons) or '제외 사유 기록 없음'))
    return dict(date=record['anchor'],recordedAt=record['recordedAtUtc'],nextOpen=record['nextOpenUtc'],
        analysisCount=len(rows),interestCount=top,candidateCount=len(selected),trend=pct(trend,100),
        volatilityCap=pct(cap,100),decision='매수 후보 검토' if selected else '신규 매수 대기',predictions=rows)


def historical_evidence():
    folder=ledger.ROOT/'output/sp500-actions-20261028-retry'
    notice='이미 확인한 과거 구간의 재검증입니다. 미사용 기간 성과나 현재 종목의 예상 수익률이 아닙니다.'
    try:
        verification=json.loads((folder/'revalidation_verification.json').read_text(encoding='utf-8'))
        if verification['status']!='PASS':raise ValueError('Unverified history')
        for path,expected in verification['source_hashes'].items():
            if hashlib.sha256(Path(path).read_bytes()).hexdigest()!=expected:raise ValueError('Changed historical source')
        source=folder/'revalidation.csv'
        with source.open(encoding='utf-8',newline='') as handle:raw=list(csv.DictReader(handle))
        labels={'FROZEN_BASELINE_REPLAY':'기존 검증 구간',
                'NEWLY_INSPECTED_RETROSPECTIVE_WINDOW':'짧은 과거 구간'}
        rows=[dict(label=labels[r['scenario']],start=r['start'],end=r['evaluated_end'],
            lastDecision=r['last_decision'],fee=pct(r['fee'],100),returnPct=pct(r['total_return_pct']),
            drawdownPct=pct(r['max_drawdown_pct']),trades=r['trades']) for r in raw
            if r['scenario'] in labels and r['policy']=='TREND50_VOL75' and r['status']=='COMPLETE_RESEARCH_ONLY']
        if len(rows)!=4:raise ValueError('Incomplete historical evidence')
        return dict(available=True,notice=notice,rows=rows,source='sp500-actions-20261028-retry/revalidation.csv',
            sha256=hashlib.sha256(source.read_bytes()).hexdigest())
    except (OSError,ValueError,KeyError):
        return dict(available=False,notice='검증된 과거 결과를 조회할 수 없습니다.',rows=[])


def probability_evidence(folder=ledger.ROOT/'output/sp500-probability-bins-20261101'):
    try:
        source=folder/'report.json';verification=json.loads((folder/'verification.json').read_text(encoding='utf-8'))
        if verification['status']!='PASS' or hashlib.sha256(source.read_bytes()).hexdigest()!=verification['reportSha256']:
            raise ValueError('Unverified probability bins')
        if hashlib.sha256((folder/'joined_trades.csv').read_bytes()).hexdigest()!=verification['joinedTradesSha256']:
            raise ValueError('Changed joined trades')
        report=json.loads(source.read_text(encoding='utf-8'))
        for path,expected in report['sourceHashes'].items():
            if hashlib.sha256((ledger.ROOT/path).read_bytes()).hexdigest()!=expected:raise ValueError('Changed probability source')
        groups=[]
        for g in report['groups']:
            rows=[]
            for b in g['bins']:
                rows.append(dict(label=f"{b['index']*10}% 이상 ~ {(b['index']+1)*10}% {'이하' if b['upperInclusive'] else '미만'}",
                    count=b['count'],meanProbability=pct(b['meanProbabilityPct']),tpRate=pct(b['tpRatePct']),
                    slRate=pct(b['slRatePct']),timeRate=pct(b['timeRatePct']),meanReturn=pct(b['meanNetReturnPct']),
                    sampleNote='표본 없음' if b['count']==0 else '소표본 (<30건)' if b['count']<30 else '-',
                    tpCount=b['tpCount'],slCount=b['slCount'],timeCount=b['timeCount']))
            groups.append(dict(key=f"{g['period']}-{g['fee']}",label=g['label'],fee=pct(g['fee'],100),
                start=g['start'],end=g['end'],lastDecision=g['lastDecision'],count=g['summary']['count'],
                tpRate=pct(g['summary']['tpRatePct']),slRate=pct(g['summary']['slRatePct']),
                meanReturn=pct(g['summary']['meanNetReturnPct']),rows=rows))
        return dict(available=True,groups=groups,source=str(source.relative_to(ledger.ROOT)),sha256=verification['reportSha256'])
    except (OSError,ValueError,KeyError):
        return dict(available=False,groups=[])


def build(root,names=None,tracking_dataset=None):
    p=ledger.protocol(root);records=ledger.decisions(root,p)
    days=[project_day(r,names) for r in reversed(records)]
    from sp500_watchlist_tracking import project_tracking
    try:
        tracking=project_tracking(records,p,dataset=tracking_dataset)
    except (OSError,ValueError,KeyError):
        tracking={}
    for day in days:
        day['tracking']=tracking.get(day['date'],dict(available=False,rows=[]))
    reports=[]
    for path in (root/'evaluations').glob('*.json'):
        r=ledger.load(path)
        if r['protocolSha256']!=p['contentSha256']:raise ValueError('Evaluation protocol mismatch')
        if not set(r['decisionHashes'])<=set(x['contentSha256'] for x in records):raise ValueError('Unknown evaluation decisions')
        reports.append(r)
    report=max(reports,key=lambda x:x['evaluatedAtUtc']) if reports else None
    accounts=[]
    if report:
        for a in report['accounts']:
            mark=a['equity'][-1] if a['equity'] else {}
            accounts.append(dict(feeKey=str(a['fee']),fee=pct(a['fee'],100),status=a['status'],
                returnPct=pct(a['total_return_pct']),markedReturnPct=pct(a['prefix_return_pct']),
                drawdownPct=pct(a['max_drawdown_pct']),cash=money(mark.get('cash')),
                holdings=money(mark.get('holdings')),equity=money(mark.get('equity')),trades=a['trades'],
                end=a.get('evaluated_end') or '-',positions=mark.get('positions',0),
                start=a.get('start','-'),
                initialState=not a['fills'] and not mark.get('positions',0),
                rows=[dict(symbol=t['symbol'],decision=t['decision'],entry=t['entry'],exit=t['exit'],
                    probability=pct(t['predictedProbability'],100),reason=t['reason'],
                    shares=t['shares'],entryPrice=money(t['entry_price']),exitPrice=money(t['exit_price']),pnl=money(t['pnl']))
                    for t in reversed(a['fills'])]))
    message='실제 일봉을 적용한 모의 결과입니다. 실제 주문·체결은 없습니다.' if days else '아직 저장된 예측이 없습니다. 최신 검증 데이터로 다음 장 개장 전에 기록해야 합니다.'
    if records:
        latest=records[-1]
        audit=latest.get('filterAudit',[])
        if not latest['ranks'] and audit and audit[0]['index_ma50']<=0:
            message=f"{latest['anchor']} 지수가 50일선 대비 {pct(audit[0]['index_ma50'],100)}로 추세 조건 미충족: 매수 후보 0개, 신규 매수 없이 현금 대기. 실제 주문·체결은 없습니다."
    return dict(version=VERSION,status='예측 기록 중' if days else '첫 예측 대기',
        message=message,
        createdAt=p['createdAtUtc'],syncedAt=datetime.now(timezone.utc).isoformat(),
        capturedDays=len(days),latest=days[0]['date'] if days else '-',days=days,accounts=accounts,
        history=historical_evidence(),probabilityEvidence=probability_evidence())


def publish(payload):
    from paper_view_publication_guard import validate_publication
    from quant_daily_forward import Database
    existing=Database().query("SELECT HEX(payload_json) FROM stock_dashboard_view_snapshot WHERE index_code='SP500' AND view_version='SP500_PAPER_WEB_V1'")
    if existing:validate_publication(payload,json.loads(bytes.fromhex(existing[0][0])))
    # Hex literals avoid SQL quoting issues; only this version and one menu are written.
    body=json.dumps(payload,ensure_ascii=False,allow_nan=False).encode('utf-8').hex()
    label='모의운용'.encode('utf-8').hex()
    sql=f"""START TRANSACTION;
INSERT INTO stock_dashboard_view_snapshot(index_code,view_version,generated_at,source,payload_json)
VALUES ('SP500','{VERSION}',NOW(),'PAPER_LEDGER_PUBLISH',CONVERT(0x{body} USING utf8mb4))
ON DUPLICATE KEY UPDATE generated_at=VALUES(generated_at),source=VALUES(source),payload_json=VALUES(payload_json);
INSERT INTO main_menu(code,label,href,sort_order,enabled)
VALUES ('paper-trading',CONVERT(0x{label} USING utf8mb4),'/paper-trading',15,TRUE)
ON DUPLICATE KEY UPDATE label=VALUES(label),href=VALUES(href),sort_order=VALUES(sort_order),enabled=VALUES(enabled);
COMMIT;"""
    subprocess.run([str(ledger.ROOT/'.tools/mariadb-11.8.2-winx64/bin/mariadb.exe'),
                    '-h','127.0.0.1','-u','root','--default-character-set=utf8mb4','king_yurina'],
                   input=sql,text=True,encoding='utf-8',check=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--publish',action='store_true')
    parser.add_argument('--ledger',type=Path,default=ledger.DEFAULT)
    parser.add_argument('--company-roster',type=Path)
    parser.add_argument('--tracking-dataset',type=Path);args=parser.parse_args()
    payload=build(args.ledger,company_names(args.company_roster),args.tracking_dataset)
    if args.publish:publish(payload)
    print(json.dumps(dict(status='PUBLISHED' if args.publish else 'PREVIEW',capturedDays=payload['capturedDays'],accounts=len(payload['accounts'])),indent=2))
