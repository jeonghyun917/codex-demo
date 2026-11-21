"""Older-window source staging. No DB writes or substitutions across tickers/providers."""
import csv,json,hashlib,math
from collections import defaultdict
from datetime import date
from decimal import Decimal
from pathlib import Path
from quant_team_financials import FIELDS,INSTANT,financial_rows
from quant_extended_financials import foreign_rows

ROOT=Path('tmp/quant-older-inputs-20261018')
OUT=Path('tmp/quant-older-staged-20261030')
CIKS={'ANSS':1013462,'MDB':1441816,'ILMN':1110803}

def price_eligible(history,sessions,prices):
    return len(history)>=80 and len(sessions)>=2 and len(set(sessions))==len(sessions) and all(
        d in prices and math.isfinite(prices[d]) and prices[d]>0 for d in sessions)

def rows(path,delimiter=','):
    with Path(path).open(encoding='utf-8-sig',newline='') as stream:
        yield from csv.DictReader(stream,delimiter=delimiter)

def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def us_rows(payload,symbol,decision):
    if symbol not in CIKS or int(payload['cik'])!=CIKS[symbol]:raise ValueError('Wrong company CIK')
    date.fromisoformat(decision)
    facts=payload['facts'].get('us-gaap',{})
    anchors={}
    for tag in FIELDS['revenue'][1]:
        for r in facts.get(tag,{}).get('units',{}).get('USD',[]):
            if not r.get('start') or r.get('filed','9999')>=decision or r['end']>=decision:continue
            period=r.get('fp');form=r.get('form','')
            days=(date.fromisoformat(r['end'])-date.fromisoformat(r['start'])).days+1
            valid=(period=='FY' and form in ('10-K','10-K/A') and 330<=days<=380) or (
                period in ('Q1','Q2','Q3','Q4') and form in ('10-Q','10-Q/A') and 70<=days<=110)
            if not valid:continue
            key=(r['end'],period,r['filed'],r['accn'],r['start'])
            anchors[key]=r
    output=[]
    for (end,period,filed,accession,start),anchor in anchors.items():
        row=dict(symbol=symbol,cik=str(CIKS[symbol]),fiscalYear=anchor.get('fy'),fiscalPeriod=period,
            form=anchor['form'],filedAt=filed,startDate=start,endDate=end,accessionNumber=accession,
            periodDays=(date.fromisoformat(end)-date.fromisoformat(start)).days+1,currency='USD',epsUnit='USD/shares')
        provenance={}
        for field,(unit,tags) in FIELDS.items():
            row[field]=None
            for tag in tags:
                matches=[r for r in facts.get(tag,{}).get('units',{}).get(unit,[]) if
                    r.get('accn')==accession and r.get('filed')==filed and r.get('end')==end
                    and (field in INSTANT and not r.get('start') or field not in INSTANT and r.get('start')==start)]
                values={Decimal(str(r['val'])) for r in matches}
                if any(not v.is_finite() for v in values):raise ValueError('Nonfinite financial fact')
                if len(values)>1:raise ValueError('Conflicting same-accession financial values')
                if values:
                    row[field]=str(next(iter(values)));provenance[field]=dict(tag=tag,unit=unit,accession=accession,filed=filed)
                    break
        row['rawJson']=json.dumps(provenance,sort_keys=True)
        output.append(row)
    return sorted(output,key=lambda r:(r['endDate'],r['filedAt'],r['accessionNumber']),reverse=True)

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    if (OUT/'inputs.json').exists():raise ValueError('Refuse completed output overwrite')
    manifest=json.loads((ROOT/'manifest.json').read_text(encoding='utf-8'))
    for s in manifest['sources']:
        assert s.get('status')==200 and sha(s['path'])==s['sha256']
    evidence=Path('tmp/quant-anss-free-evidence-20261018/chartexchange-anss.csv')
    assert sha(evidence)=='d3bd7e80e31fa9a082f82ac30caa52de017aa6a06b36e8c127068d8bf52247c1'
    anss={}
    for r in rows(evidence):
        assert r['symbol']=='ANSS' and r['currency']=='USD'
        if r['date'] in anss:raise ValueError('Duplicate ANSS observation')
        date.fromisoformat(r['date'])
        anss[r['date']]=float(r['close'])
    old=json.loads(Path('tmp/quant-complete-windows-recovered-20261018/report.json').read_text(encoding='utf-8'))
    prices=defaultdict(dict)
    for r in rows('tmp/quant-complete-window-canonical-20261018.tsv','\t'):
        prices[r['symbol']][r['trade_date']]=float(r['close_price'])
    for r in rows('tmp/quant-complete-windows-recovered-20261018/additional-closes.csv'):
        assert r['tradeDate'] not in prices[r['symbol']]
        prices[r['symbol']][r['tradeDate']]=float(r['close'])
    assert not prices['ANSS'],'Unexpected canonical ANSS: price-basis reconciliation required'
    prices['ANSS']=anss
    members=defaultdict(list)
    for r in rows('tmp/quant-complete-window-members-20261018.tsv','\t'):
        if r['index_code']=='NASDAQ100_MACRO_FREE_20261018':members[r['snapshot_date']].append(r['symbol'])
    levels={}
    for r in rows('tmp/quant-complete-window-benchmarks-20261018.tsv','\t'):
        if r['index_code']=='NASDAQ100_MACRO_FREE_20261018':
            levels[r['trade_date']]=None if r['benchmark_level']=='NULL' else float(r['benchmark_level'])
    payloads={s:json.loads((ROOT/f'{s}-facts.json').read_text(encoding='utf-8')) for s in CIKS}
    payloads['TEAM']=json.loads(Path('tmp/quant-team-inputs-20261018/team-facts.json').read_text(encoding='utf-8'))
    for s in ('AZN','GFS'):payloads[s]=json.loads(Path(f'tmp/quant-extended-inputs-20261018/{s}-facts.json').read_text(encoding='utf-8'))
    periods=[];holdings=[];excluded={}
    for p in old['periods']:
        if p['index']!='NASDAQ100_MACRO_FREE_20261018' or set(p['missingPrices'])!={'ANSS'}:continue
        day=p['decision']
        sessions=sorted(d for d in levels if p['entry']<=d<=p['exit'])
        history={d:c for d,c in anss.items() if d<=day}
        if not price_eligible(history,sessions,anss) or len(sessions)!=21:
            excluded[day]='ANSS fewer than80 prior candles or incomplete outcome sessions (never bridge acquisition)'
            continue
        pop=members[day]
        assert len(pop)==len(set(pop))==101
        assert len(sessions)==21 and all(levels[d] is not None and levels[d]>0 for d in sessions)
        assert sessions==sorted(d for d in prices['AAPL'] if p['entry']<=d<=p['exit'])
        for symbol in pop:
            assert all(d in prices[symbol] and prices[symbol][d]>0 for d in sessions),(day,symbol)
        bench=(levels[sessions[-1]]/levels[sessions[0]]-1)*100
        periods.append(dict(decision=day,entry=sessions[0],exit=sessions[-1],members=pop,anssPriorCandles=len(history),missingPredictors=p['missingForecasts']))
        for symbol in pop:
            e,x=prices[symbol][sessions[0]],prices[symbol][sessions[-1]]
            holdings.append(dict(index=p['index'],decision=day,entry=sessions[0],exit=sessions[-1],symbol=symbol,
                entryClose=e,exitClose=x,actualReturnPct=(x/e-1)*100,benchmarkReturnPct=bench,
                sourceId='chartexchange-anss-research' if symbol=='ANSS' else 'preserved-canonical-plus-compatible-staging'))
    dates=[p['decision'] for p in periods]
    assert dates,'No eligible earlier period'
    bysymbol={}
    for symbol,payload in payloads.items():
        if symbol=='TEAM':bysymbol[symbol]={d:financial_rows(payload,d) for d in dates}
        elif symbol in CIKS:bysymbol[symbol]={d:us_rows(payload,symbol,d) for d in dates}
        else:bysymbol[symbol]={d:foreign_rows(payload,symbol,d,'ORDINARY_EQUIVALENT_ARCHIVE' if symbol=='AZN' else 'ORDINARY') for d in dates}
    paths=[str(ROOT/'manifest.json'),str(evidence),'tmp/quant-complete-windows-recovered-20261018/report.json',
        'tmp/quant-complete-windows-recovered-20261018/additional-closes.csv',
        'tmp/quant-complete-window-canonical-20261018.tsv','tmp/quant-complete-window-members-20261018.tsv',
        'tmp/quant-complete-window-benchmarks-20261018.tsv','scripts/quant_older_inputs.py']
    paths.extend(str(ROOT/f'{s}-facts.json') for s in CIKS)
    paths+=['tmp/quant-team-inputs-20261018/team-facts.json']+[f'tmp/quant-extended-inputs-20261018/{s}-facts.json' for s in ('AZN','GFS')]
    result=dict(classification='OLDER_PROVIDER_CLOSE_RECONSTRUCTED_INPUTS_NOT_PIT',databaseWrites=0,promotionAllowed=False,
        dates=dates,periods=periods,excluded=excluded,bySymbol=bysymbol,inputHashes={p:sha(p) for p in paths},
        priceSource='ChartExchange provider-reported ANSS closes; adjustment policy unproven; independent StockAnalysis overlap retained, volumes not mixed',
        capPolicy='No invented historical shares or cap; whole V9 date ineligible when any member lacks market input')
    with (OUT/'holdings.csv').open('x',encoding='utf-8',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=list(holdings[0]));writer.writeheader();writer.writerows(holdings)
    with (OUT/'inputs.json').open('x',encoding='utf-8') as stream:json.dump(result,stream,indent=2)
    print(json.dumps(dict(dates=dates,holdings=len(holdings),excluded=excluded),indent=2))

if __name__=='__main__':main()

