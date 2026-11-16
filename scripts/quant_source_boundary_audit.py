"""Read-only selected-source unit-boundary diagnostic; never constructs returns."""
import csv, hashlib, io, json, subprocess
from collections import defaultdict
from datetime import date
from pathlib import Path

ROOT = Path('tmp/quant-source-boundary-audit-20261031-v1')
INPUT = Path('tmp/quant-long-extended-oof-study-20261031-v2/inputs.json')

def query(sql):
    symbols="'MMM','AXP','AMGN','AAPL','BA','CAT','CVX','CSCO','KO','DIS','DOW','GS','HD','HON','IBM','INTC','JNJ','JPM','MCD','MRK','MSFT','NKE','PG','CRM','TRV','UNH','VZ','V','WBA','WMT'"
    sql += ' AND symbol IN ('+symbols+')'
    out = subprocess.check_output(['.tools/mariadb-11.8.2-winx64/bin/mariadb.exe', '-u', 'root', '--batch', '--raw', 'king_yurina', '-e', 'SET SESSION TRANSACTION READ ONLY; ' + sql]).decode('utf-8')
    return [json.loads(r['j']) for r in csv.DictReader(io.StringIO(out), delimiter='\t')]

def issuer(symbol, cik, day):
    if symbol != 'DIS': return True
    return str(cik).lstrip('0') == ('1001039' if day < '2019-03-20' else '1744489')

def valid(row, annual):
    period = row.get('fiscalPeriod') or ''
    if period != 'FY' if annual else period not in ('Q1','Q2','Q3','Q4'): return False
    actual = (date.fromisoformat(row['endDate']) - date.fromisoformat(row['startDate'])).days + 1 if row.get('startDate') else None
    days = row.get('periodDays') or actual or 0
    return (330 if annual else 70) <= days <= (380 if annual else 110) and (actual is None or actual == days)

def select(rows, symbol, day, annual, before=None):
    def eligible(r):
        if not (r.get('filedAt') and r.get('endDate') and r['filedAt'] < day and r['endDate'] < day and valid(r, annual) and issuer(symbol, r.get('cik'), day)): return False
        if before:
            gap = (date.fromisoformat(before)-date.fromisoformat(r['endDate'])).days
            return (330 if annual else 70) <= gap <= (380 if annual else 110)
        return True
    def key(r):
        preferred = r.get('form') in ('10-K','10-K/A','20-F','20-F/A','40-F','40-F/A') if annual else r.get('assets') is not None or r.get('equity') is not None
        return (r['endDate'], preferred, r['filedAt'], r.get('id') or -1)
    choices = [r for r in rows if eligible(r)]
    return max(choices, key=key) if choices else None

def main():
    inp=json.loads(INPUT.read_text(encoding='utf-8'))
    facts=query("SELECT JSON_OBJECT('id',id,'symbol',symbol,'cik',cik,'concept',concept,'filed',CAST(filed_at AS CHAR),'end',CAST(end_date AS CHAR),'value',value,'accession',accession_number,'raw',raw_json) AS j FROM stock_sec_fact WHERE unit='shares' AND filed_at<'2020-10-30' AND end_date<'2020-10-30' AND (start_date IS NULL OR start_date=end_date) AND concept IN ('EntityCommonStockSharesOutstanding','CommonStockSharesOutstanding','NumberOfSharesOutstanding') AND value>0")
    native=query("SELECT JSON_OBJECT('id',id,'symbol',symbol,'cik',cik,'filedAt',CAST(filed_at AS CHAR),'startDate',CAST(start_date AS CHAR),'endDate',CAST(end_date AS CHAR),'fiscalPeriod',fiscal_period,'periodDays',period_days,'form',form,'assets',assets,'equity',equity,'epsDiluted',eps_diluted,'epsUnit',eps_unit) AS j FROM stock_sec_financial_standard WHERE filed_at<'2020-10-30'")
    bysymbol=defaultdict(list); financial=defaultdict(list)
    for r in facts: bysymbol[r['symbol']].append(r)
    for r in native: financial[r['symbol']].append(r)
    for s,rows in inp['financialsBySymbol'].items(): financial[s].extend(rows)
    selected_shares=defaultdict(list); selected_eps=defaultdict(list)
    days=inp['trainingDecisions']+inp['testDecisions']
    for day in days:
        population=inp['trainingPopulations'].get(day,inp['population'])
        for symbol in population:
            choices=[]
            prior=sorted((r for r in bysymbol[symbol] if r['filed']<day and r['end']<day),key=lambda r:(r['end'],r['filed'],r['id']),reverse=True)[:80]
            for r in prior:
                try: raw=json.loads(r['raw'])
                except (TypeError,json.JSONDecodeError): continue
                if 'start' in raw or raw.get('filed')!=r['filed'] or raw.get('end')!=r['end'] or raw.get('accn')!=r['accession'] or raw.get('val')!=r['value'] or not issuer(symbol,r.get('cik'),day): continue
                choices.append(dict(measurement=r['end'],filed=r['filed'],value=r['value'],concept=r['concept'],cik=r.get('cik'),source='DB_ORIGINAL_INSTANT'))
            for r in inp['sharesBySymbol'].get(symbol,[]):
                if r['filedDate']<day and r['measurementDate']<day and issuer(symbol,r.get('cik',r.get('sourceCIK')),day): choices.append(dict(measurement=r['measurementDate'],filed=r['filedDate'],value=r['totalEconomicCommonShares'],concept='SUPPLEMENT',cik=r.get('cik',r.get('sourceCIK')),source=r['sourcePath']))
            if not choices: raise ValueError('Missing shares '+symbol+day)
            chosen=max(choices,key=lambda r:(r['measurement'],r['filed']))
            selected_shares[symbol].append(dict(chosen,decision=day))
            for annual in (True,False):
                latest=select(financial[symbol],symbol,day,annual)
                previous=select(financial[symbol],symbol,day,annual,latest['endDate']) if latest else None
                for tag,row in [('latest',latest),('previous',previous)]:
                    if row and row.get('epsDiluted') is not None: selected_eps[symbol].append(dict(decision=day,role=('annual_' if annual else 'quarter_')+tag,filed=row['filedAt'],end=row['endDate'],eps=row['epsDiluted'],unit=row.get('epsUnit')))
    summaries=[]
    for s in inp['population']:
        ss=selected_shares[s];ee=selected_eps[s]
        summaries.append(dict(symbol=s,decisions=len(ss),oldestSelectedShareMeasurement=min(r['measurement'] for r in ss),oldestSelectedShareFiled=min(r['filed'] for r in ss),firstSelectedShares=ss[0],lastSelectedShares=ss[-1],oldestSelectedEpsFiled=min((r['filed'] for r in ee),default=None),oldestSelectedEpsExamples=[r for r in ee if r['filed']==min(x['filed'] for x in ee)][:4] if ee else []))
    ROOT.mkdir(exist_ok=False)
    value=dict(classification='READ_ONLY_SELECTED_SOURCE_BOUNDARIES_NO_RETURNS_OR_MODEL_EVALUATION',inputPath=str(INPUT),inputSha256=hashlib.sha256(INPUT.read_bytes()).hexdigest(),summaries=summaries,selectedShares=dict(selected_shares),selectedEps=dict(selected_eps),databaseWrites=0,limitations=['Diagnostic replicates source selection from exact current rows and sealed supplements; original share-class semantics need primary-file verification.','No target returns, model predictions, OOF, or MAE computed.'])
    with (ROOT/'receipt.json').open('x',encoding='utf-8') as f: json.dump(value,f,indent=2)
    print(json.dumps(summaries,indent=2))

if __name__=='__main__': main()
