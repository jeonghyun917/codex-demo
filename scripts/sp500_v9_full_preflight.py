"""Read-only daily full-universe readiness gate. Missing V9 inputs are not scores."""
import argparse
from collections import defaultdict
from datetime import datetime, timezone
from decimal import Decimal
import json
from pathlib import Path
import subprocess
import pandas as pd
from sp500_v9_shadow import digest
from sp500_stable_ranking import EXCLUDED

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/sp500-v9-full-preflight-20261101'
DATA=ROOT/'output/sp500-actions-20261028-retry/dataset'
LATEST=ROOT/'tmp/quant-full-financial-study-20261101-v3'
CONCEPTS={'EntityCommonStockSharesOutstanding','CommonStockSharesOutstanding','NumberOfSharesOutstanding'}


def valid_share(r):
    try:
        raw=r['raw_json'];raw=json.loads(raw) if isinstance(raw,str) else raw
        return bool(r.get('cik') and r.get('filed_at') and r.get('end_date') and
            r.get('unit')=='shares' and r.get('concept') in CONCEPTS and
            (not r.get('start_date') or r['start_date']==r['end_date']) and
            (not raw.get('start') or raw['start']==raw.get('end')) and
            raw.get('accn')==r['accession_number'] and raw.get('filed')==r['filed_at'] and
            raw.get('end')==r['end_date'] and Decimal(str(raw['val']))==Decimal(str(r['value']))>0)
    except (KeyError,TypeError,ValueError):return False


def prior_observation(rows,day):
    eligible=[r for r in rows if r.get('filed_at') and r.get('end_date') and r['filed_at']<day and r['end_date']<day]
    return max(eligible,key=lambda r:(r['end_date'],r['filed_at']),default=None)


def require_full_coverage(expected,actual):
    if expected!=actual:raise ValueError(f'Incomplete full-universe predictions: missing={len(expected-actual)}, extra={len(actual-expected)}')


def in_scope(d):
    return '2025-01-02'<=d<='2026-08-18' or '2026-09-17'<=d<='2026-09-25'


def export_sources():
    # Both queries run in one read-only consistent snapshot; no application startup hooks.
    sql="""SET SESSION TRANSACTION READ ONLY; START TRANSACTION WITH CONSISTENT SNAPSHOT;
SELECT JSON_OBJECT('kind','share','body',JSON_OBJECT('symbol',symbol,'cik',cik,'filed_at',filed_at,
'end_date',end_date,'start_date',start_date,'value',value,'unit',unit,'concept',concept,
'accession_number',accession_number,'raw_json',raw_json)) FROM stock_sec_fact
WHERE unit='shares' AND concept IN ('EntityCommonStockSharesOutstanding','CommonStockSharesOutstanding','NumberOfSharesOutstanding')
AND value>0 AND filed_at<'2026-09-26' AND (start_date IS NULL OR start_date=end_date);
SELECT JSON_OBJECT('kind','financial','body',JSON_OBJECT('symbol',symbol,'cik',cik,'filed_at',filed_at,
'end_date',end_date,'start_date',start_date,'fiscal_period',fiscal_period,'period_days',period_days,
'currency',currency,'accession_number',accession_number,'eps_diluted',eps_diluted,'eps_unit',eps_unit,
'revenue',revenue,'net_income',net_income)) FROM stock_sec_financial_standard WHERE filed_at<'2026-09-26';
COMMIT;"""
    command=[str(ROOT/'.tools/mariadb-11.8.2-winx64/bin/mariadb.exe'),'--host=127.0.0.1','--port=3306',
             '--user=root','--database=king_yurina','--batch','--raw','--skip-column-names','--default-character-set=utf8mb4',
             '--execute='+sql]
    r=subprocess.run(command,capture_output=True,encoding='utf-8',check=True)
    return r.stdout,[json.loads(line) for line in r.stdout.splitlines() if line.strip()]


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--source',type=Path);args=parser.parse_args()
    if OUT.exists():raise ValueError('Refusing overwrite of sealed audit')
    source_paths=[Path(__file__),ROOT/'scripts/test_sp500_v9_full_preflight.py',
        ROOT/'src/main/java/com/kingyurina/demo/stock/StockExpectedReturnV9Service.java',
        ROOT/'src/main/java/com/kingyurina/demo/stock/StockPortfolioBacktestService.java',
        ROOT/'src/main/java/com/kingyurina/demo/stock/HistoricalBacktestSeedService.java',
        DATA/'daily_panel.csv.gz',DATA/'inputs/aliases.csv',
        ROOT/'output/sp500-stable-ranking-20261027/predictions.csv.gz',
        ROOT/'output/sp500-actions-20261028-retry/unseen_research_predictions.csv.gz',
        LATEST/'inputs.json',LATEST/'protocol.json',LATEST/'comparison.json',
        ROOT/'data/sp500-paper-ledger/protocol.json',ROOT/'data/sp500-paper-ledger/decisions/2026-10-30.json']
    hashes={str(p.relative_to(ROOT)):digest(p) for p in source_paths}
    if args.source:
        raw=args.source.read_text(encoding='utf-8');records=[json.loads(s) for s in raw.splitlines() if s.strip()]
    else:raw,records=export_sources()
    OUT.mkdir();(OUT/'database-source-snapshot.jsonl').write_text(raw,encoding='utf-8')
    hashes[str((OUT/'database-source-snapshot.jsonl').relative_to(ROOT))]=digest(OUT/'database-source-snapshot.jsonl')
    aliases=pd.read_csv(DATA/'inputs/aliases.csv').set_index('symbol').provider_symbol.to_dict()
    shares=defaultdict(list);financial=defaultdict(list);rejected=[]
    for item in records:
        r=item['body'];s=aliases.get(r['symbol'],r['symbol'])
        if item['kind']=='share':
            if valid_share(r):shares[s].append(r)
            else:rejected.append(r)
        else:financial[s].append(r)
    panel=pd.read_csv(DATA/'daily_panel.csv.gz',usecols=['symbol','trade_date'])
    panel=panel[panel.trade_date.map(in_scope)].copy();panel.symbol=panel.symbol.map(lambda s:aliases.get(s,s))
    assert not panel.duplicated(['symbol','trade_date']).any()
    predictions=pd.concat([pd.read_csv(ROOT/'output/sp500-stable-ranking-20261027/predictions.csv.gz'),
        pd.read_csv(ROOT/'output/sp500-actions-20261028-retry/unseen_research_predictions.csv.gz')],ignore_index=True)
    predictions=predictions[predictions.date.map(in_scope)]
    pkeys=set(zip(predictions.date,predictions.symbol))
    counts=[]
    for r in panel.itertuples():
        day=r.trade_date;s=r.symbol;sh=prior_observation(shares[s],day)
        annual=prior_observation([f for f in financial[s] if f.get('fiscal_period')=='FY' and f.get('period_days') and 330<=f['period_days']<=380],day)
        quarter=prior_observation([f for f in financial[s] if f.get('fiscal_period') in ['Q1','Q2','Q3','Q4'] and f.get('period_days') and 70<=f['period_days']<=110],day)
        counts.append(dict(date=day,symbol=s,probabilityEligible=(day,s) in pkeys,
            corporateExcluded=s in EXCLUDED,priorShareCandidate=sh is not None,
            shareFiled=None if sh is None else sh['filed_at'],shareMeasured=None if sh is None else sh['end_date'],
            priorAnnual=annual is not None,priorQuarter=quarter is not None,
            annualEpsPresent=annual is not None and annual.get('eps_diluted') is not None,
            status='CORPORATE_EXCLUDED' if s in EXCLUDED else 'NO_FROZEN_PROBABILITY' if (day,s) not in pkeys else
                   'BLOCKED_NO_PRIOR_INSTANT_SHARES' if sh is None else 'PENDING_IDENTITY_PRICE_BASIS_AND_MODEL_REPLAY'))
    frame=pd.DataFrame(counts);frame.to_csv(OUT/'daily_input_coverage.csv.gz',index=False)
    eligible=frame[frame.probabilityEligible&~frame.corporateExcluded]
    missing=eligible[~eligible.priorShareCandidate]
    summary=missing.groupby('symbol').agg(missingDays=('date','size'),firstMissing=('date','min'),lastMissing=('date','max')).reset_index()
    summary.to_csv(OUT/'missing_share_targets.csv',index=False)
    day=eligible.groupby('date').agg(required=('symbol','size'),shareCandidates=('priorShareCandidate','sum'),
        annualAvailable=('priorAnnual','sum'),quarterAvailable=('priorQuarter','sum')).reset_index()
    day['missingShares']=day.required-day.shareCandidates;day.to_csv(OUT/'daily_readiness.csv',index=False)
    report=dict(status='BLOCKED_FULL_COMPARISON' if len(missing) else 'PENDING_BASIS_AUDIT_AND_REPLAY',
        createdAtUtc=datetime.now(timezone.utc).isoformat(),latestDataVersion=str(LATEST.relative_to(ROOT)),
        periods=[['2025-01-02','2026-08-18'],['2026-09-17','2026-09-25']],
        fullMembershipRows=len(frame),fullMembershipSymbols=frame.symbol.nunique(),requiredProbabilityRows=len(eligible),
        requiredSymbols=eligible.symbol.nunique(),requiredDates=eligible.date.nunique(),
        missingShareRows=len(missing),missingShareSymbols=missing.symbol.nunique(),
        fullyShareCoveredDates=int((day.missingShares==0).sum()),missingShareSymbolsList=sorted(missing.symbol.unique()),
        rejectedShareRows=len(rejected),sourceShareRows=sum(i['kind']=='share' for i in records),
        sourceFinancialRows=sum(i['kind']=='financial' for i in records),
        rowsWithoutAnnual=int((~eligible.priorAnnual).sum()),rowsWithoutQuarter=int((~eligible.priorQuarter).sum()),
        predictionsGenerated=0,accountsCompared=0,productionChanged=False,
        sourceHashes=hashes,limitations=[
            'Prior share candidate is necessary but not sufficient: class totals, issuer identity and split/price basis still require verification.',
            'Financial availability is metadata only, not proof of current mapping correctness or point-in-time archive.',
            'No zero-score, current-share backfill, partial-universe performance or proxy V9 was substituted.',
            'Latest full-financial repairs cover old Dow30 history; uniform SP500 inputs must be reconstructed separately.'])
    for p,h in hashes.items():assert digest(ROOT/p)==h,p
    (OUT/'report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k not in ['sourceHashes','limitations']},indent=2))


if __name__=='__main__':main()
