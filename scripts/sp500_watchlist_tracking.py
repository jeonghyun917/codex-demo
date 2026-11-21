"""Read-only TOP20 counterfactual tracking, separate from portfolio execution."""
from collections import Counter
from datetime import datetime, timezone
import math
from pathlib import Path
import sp500_paper_ledger as ledger
from build_sp500_daily_dataset import read_csv, digest
from sp500_daily_validation import exit_event, valid_bar
from quant_forward_validation import timestamp

LABELS={'WAITING_ENTRY':'진입 대기','OPEN':'추적 중','TP':'익절','SL':'손절',
        'SL_GAP':'손절 (시가 갭)','TIME':'20거래일 청산','DATA_GAP':'가격 누락·검증 보류',
        'BASIS_MISMATCH':'가격 기준 불일치','CALENDAR_MISSING':'일정 확인 필요'}


def track_symbol(symbol,sessions,prices,asof):
    result=dict(symbol=symbol,status='WAITING_ENTRY',entryDate=sessions[0] if sessions else '',
        entryPrice=None,exitDate='',exitPrice=None,sessions=0,net10=None,net20=None,
        gross=None,ambiguous=False,checkedThrough='',missingDate='')
    if not sessions:return dict(result,status='CALENDAR_MISSING')
    for age,day in enumerate(sessions[:20],1):
        if day>asof:break
        b=prices.get((symbol,day))
        if not valid_bar(b):return dict(result,status='DATA_GAP',missingDate=day)
        if age==1:result['entryPrice']=b['open']
        result.update(status='OPEN',sessions=age,checkedThrough=day)
        entry=result['entryPrice']
        event=exit_event(b,entry,age)
        if event:
            px,reason=event
            return dict(result,status=reason,exitDate=day,exitPrice=px,
                gross=100*(px/entry-1),net10=100*(px*.999/(entry*1.001)-1),
                net20=100*(px*.998/(entry*1.002)-1),
                ambiguous=b['low']<=entry*.95 and b['high']>=entry*1.15)
    return result


def load_prices(record,symbols,start):
    data=Path(record['datasetPath'])
    if ledger.dataset_hashes(data)!=record['inputHashes']:raise ValueError('TRACKING_SOURCE_CHANGED')
    meta=ledger.checked_data(data)
    aliases={r['symbol']:r['provider_symbol'] for r in read_csv(data/'inputs/aliases.csv')}
    prices={}
    for r in read_csv(data/'price_history.csv.gz'):
        symbol=aliases.get(r['symbol'],r['symbol'])
        if symbol not in symbols or r['trade_date']<start:continue
        key=(symbol,r['trade_date'])
        try:
            b={k:float(r[k+'_price']) for k in ('open','high','low','close')}
            b.update(volume=float(r['volume']),execution_blocked=r['price_usable']!='1')
        except (ValueError,TypeError):b=dict(open=0,high=0,low=0,close=0,execution_blocked=True)
        if key in prices and prices[key]!=b:raise ValueError('CONFLICTING_TRACKING_BAR')
        prices[key]=b
    return prices,meta['end']


def project_tracking(records,protocol,now=None,dataset=None):
    if not records or any(not r.get('datasetPath') for r in records):return {}
    now=now or datetime.now(timezone.utc)
    ordered=sorted(records,key=lambda r:r['anchor'])
    top={r['anchor']:sorted(r['predictions'],key=lambda x:(-x['probability'],x['symbol']))[:20] for r in ordered}
    symbols={x['symbol'] for rows in top.values() for x in rows}
    latest=ordered[-1]
    if dataset is not None:
        dataset=Path(dataset)
        latest=dict(datasetPath=str(dataset.resolve()),inputHashes=ledger.dataset_hashes(dataset))
    prices,end=load_prices(latest,symbols,ordered[0]['anchor'])
    if end<ordered[-1]['anchor']:raise ValueError('TRACKING_DATASET_TOO_OLD')
    closed=[s['session'] for s in protocol['calendar'] if timestamp(s['closeUtc'])<=now]
    asof=min(end,max(closed)) if closed else ''
    calendar=[s['session'] for s in protocol['calendar']]
    result={}
    for record in ordered:
        anchor=record['anchor']
        frozen,_=load_prices(record,{r['symbol'] for r in top[anchor]},anchor)
        sessions=calendar[calendar.index(record['entrySession']):] if record['entrySession'] in calendar else []
        rows=[]
        for prediction in top[anchor]:
            symbol=prediction['symbol']
            a=frozen.get((symbol,anchor));b=prices.get((symbol,anchor))
            row=track_symbol(symbol,sessions,prices,asof)
            if not valid_bar(a) or not valid_bar(b) or not math.isclose(a['close'],b['close'],rel_tol=1e-7):
                row=dict(track_symbol(symbol,sessions,{},''),status='BASIS_MISMATCH')
            money=lambda v:'-' if v is None else f'${v:,.2f}'
            pct=lambda v:'-' if v is None else f'{v:+.2f}%'
            rows.append(dict(row,probabilityText=f"{prediction['probability']*100:.2f}%",
                filterPassed=symbol in {r['symbol'] for r in record['ranks']},
                label=LABELS[row['status']],entryPriceText=money(row['entryPrice']),
                exitPriceText=money(row['exitPrice']),net10Text=pct(row['net10']),net20Text=pct(row['net20'])))
        counts=Counter(r['status'] for r in rows)
        result[anchor]=dict(available=True,asof=asof,rows=rows,total=len(rows),
            completed=sum(counts[s] for s in ('TP','SL','SL_GAP','TIME')),tp=counts['TP'],
            sl=counts['SL']+counts['SL_GAP'],time=counts['TIME'],open=counts['OPEN'],waiting=counts['WAITING_ENTRY'],
            blocked=sum(counts[s] for s in ('DATA_GAP','BASIS_MISMATCH','CALENDAR_MISSING')),
            source=latest['datasetPath'],sourceHashes=latest['inputHashes'],
            method='NEXT_OPEN_TP15_SL5_MAX20_EXISTING_EXIT_ENGINE',
            engineSha256=digest(Path(ledger.engine.__file__)))
    if ledger.dataset_hashes(Path(latest['datasetPath']))!=latest['inputHashes']:
        raise ValueError('TRACKING_SOURCE_CHANGED_DURING_READ')
    return result
