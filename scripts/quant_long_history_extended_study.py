"""Readiness-driven earlier-history extension. Same unopened tests, unchanged gates."""
import argparse,csv,hashlib,json,math
from datetime import datetime,timezone
from pathlib import Path
import quant_long_history_study as base
from quant_strict_unused_study import query
from quant_history_usage import window_usage,require_unused_windows

ROOT=Path('tmp/quant-long-extended-oof-study-20261031-v1')
DATA=base.DATA;INPUT=base.INPUT;ROSTER=base.ROSTER;TEST=base.TEST;CUTOFF=base.CUTOFF
sha,load,save=base.sha,base.load,base.save


def issuer_admitted(symbol,day):
    return symbol!='DIS' or day<'2019-03-20' or day>='2019-05-09'


def price_admitted(symbol,count):
    if count>=80:return True
    if symbol=='DOW':return False
    raise ValueError('Unexpected missing history for '+symbol)


def bucket_ready(count):return count>=200


def prepare():
    ROOT.mkdir(parents=True,exist_ok=True)
    if (ROOT/'protocol.json').exists():raise ValueError('Already sealed')
    audit=load(DATA/'cohort-source-audit.json')
    hashes={str(DATA/'cohort-source-audit.json'):sha(DATA/'cohort-source-audit.json')}
    benchmark=[]
    for r in csv.DictReader((INPUT/'DJIA-2016-2020.csv').read_text(encoding='utf-8').splitlines()):
        if r['DJIA'] not in ('','.'):
            benchmark.append(dict(day=r['observation_date'],level=r['DJIA'],source='FRED_DJIA_SP_DOW_JONES_PRICE_INDEX'))
    calendar=[r['day'] for r in benchmark]
    train=[d for d in calendar if '2017-01-31'<=d<='2020-08-28']
    prices={};events={};eligibility={};expected_rows=0
    for s in ROSTER:
        p=DATA/(s+'-zenodo-original.csv');hashes[str(p)]=sha(p)
        source_audit=next(r for r in audit['datasets'] if r['symbol']==s)
        if sha(p)!=source_audit['raw_sha256']:raise ValueError('Publisher source changed')
        rows=list(csv.DictReader(p.read_text(encoding='utf-8-sig').splitlines()))
        series=[dict(day=r['Date'],close=r['Close'],source='ZENODO_AUTHOR_COHERENT_CLOSE_NOT_ADJCLOSE')
                for r in rows if '2016-06-01'<=r['Date']<='2020-12-01' and (s!='DOW' or r['Date']>='2019-04-02')]
        days=[r['day'] for r in series]
        if days!=sorted(set(days)) or any(not math.isfinite(float(r['close'])) or float(r['close'])<=0 for r in series):raise ValueError('Invalid source series '+s)
        if [d for d in days if d>=calendar[0]]!=[d for d in calendar if d>=max(days[0],calendar[0])]:raise ValueError('Calendar gap '+s)
        prices[s]=series
        archive=Path('tmp/pit-acquisition/actions/raw')/(s+'.json')
        events[s]=[]
        if archive.exists():
            hashes[str(archive)]=sha(archive)
            original=load(archive)['chart']['result'][0]
            if original['meta']['symbol']!=s:raise ValueError('Adjustment issuer mismatch')
            for e in original.get('events',{}).get('splits',{}).values():
                d=datetime.fromtimestamp(e['date'],timezone.utc).strftime('%Y-%m-%d')
                if d<='2024-06-26':events[s].append(dict(day=d,numerator=str(e['numerator']),denominator=str(e['denominator']),kind='SPLIT' if s in ('AAPL','WMT') else 'SEPARATION_RESEARCH',sourcePath=str(archive),sourceSha256=sha(archive)))
    for day in train:
        held=[d for d in calendar if d>day][:21]
        if len(held)!=21 or not held[-1]<CUTOFF:raise ValueError('Training maturity gate')
        admitted=[]
        for s in ROSTER:
            if not issuer_admitted(s,day):continue
            days=[p['day'] for p in prices[s]]
            if not price_admitted(s,sum(d<=day for d in days)):continue
            if not set(held)<=set(days):raise ValueError('Incomplete training prices '+s+day)
            admitted.append(s)
        eligibility[day]=admitted;expected_rows+=len(admitted)
    old=query('SELECT signal_date AS signalDate,entry_trade_date AS entryTradeDate,exit_trade_date AS exitTradeDate,horizon_days AS horizonDays FROM stock_backtest_result_v2')
    old=[dict(r,horizonDays=int(r['horizonDays'])) for r in old]
    files=[]
    for p in sorted(Path('output').glob('sp500*/**/manifest.json')):
        m=load(p)
        if isinstance(m,dict) and m.get('start') and m.get('end'):
            hashes[str(p)]=sha(p);files.append(str(p));old.append(dict(signalDate=m['start'],entryTradeDate=m['start'],exitTradeDate=m['end'],horizonDays=0))
    if not files:raise ValueError('File-only exposure missing')
    for p in [Path('tmp/quant-new-period-20261031-v1/replay-v8-complete/result.json'),Path('tmp/quant-strict-unused-2021-20261031-v1/replay/result.json'),Path('tmp/quant-long-oof-study-20261031-v1/replay/result.json')]:
        r=load(p);hashes[str(p)]=sha(p)
        if isinstance(r.get('trainingRows'),list):old.extend({k:v[k] for k in ['signalDate','entryTradeDate','exitTradeDate','horizonDays']} for v in r['trainingRows'])
        old.extend(dict(signalDate=d['decision'],entryTradeDate=v['entry'],exitTradeDate=v['exit'],horizonDays=20) for d in r.get('dates',[]) for v in d['pairs'])
    targets=[]
    for day in TEST:
        h=[d for d in calendar if d>day][:21]
        if len(h)!=21:raise ValueError('Incomplete target calendar')
        for s in ROSTER:
            if not issuer_admitted(s,day) or not price_admitted(s,sum(r['day']<=day for r in prices[s])) or not set(h)<=set(r['day'] for r in prices[s]):raise ValueError('Target30coverage failure')
        targets.append(dict(decision=day,entry=h[0],exit=h[-1]))
    exposure=base.assert_new_windows(old,targets)
    save(ROOT/'prior-label-exposure.json',dict(rows=len(old),testIntervals=exposure,fileOnlyConservativeCalendars=files,priorWindowFingerprint=hashlib.sha256(json.dumps(old,sort_keys=True).encode()).hexdigest(),strictUnusedPassed=True))
    financial={'WBA':load('tmp/quant-strict-dow-inputs-20261031-v1/WBA-financials.json'),'DIS':load(INPUT/'DIS-legacy-financials.json')}
    shares={'WBA':load('tmp/quant-strict-dow-inputs-20261031-v1/WBA-shares.json'),'DIS':load(INPUT/'DIS-legacy-shares.json')+load(INPUT/'DIS-current-shares.json')}
    rules={'DIS':dict(legacyCik='1001039',validUntilExclusive='2019-03-20',eligibleIntervals=[dict(fromInclusive='2017-01-31',toExclusive='2019-03-20'),dict(fromInclusive='2019-05-09',toExclusive='2020-12-02')])}
    for p in [INPUT/'DJIA-2016-2020.csv',INPUT/'DIS-legacy-companyfacts-original.json',INPUT/'DIS-legacy-financials.json',INPUT/'DIS-legacy-shares.json',INPUT/'DIS-legacy-source-audit.json',
              INPUT/'DIS-current-companyfacts-original.json',INPUT/'DIS-current-shares.json',INPUT/'DIS-legacy-financials-audit.json',
              INPUT/'AAPL-split-primary-20200730.html',Path('tmp/quant-strict-dow-inputs-20261031-v1/WBA-financials.json'),Path('tmp/quant-strict-dow-inputs-20261031-v1/WBA-shares.json'),
              Path('tmp/quant-strict-dow-inputs-20261031-v1/WBA-companyfacts.json'),Path('scripts/QuantLongHistoryExtendedReplay.java'),Path('scripts/QuantLongHistoryReplay.java'),
              Path('scripts/QuantStrictDowReplay.java'),Path('scripts/quant_long_history_extended_study.py'),Path('scripts/quant_long_history_study.py'),Path('scripts/quant_history_usage.py')]:hashes[str(p)]=sha(p)
    value=dict(population=ROSTER,trainingDecisions=train,testDecisions=TEST,fixedCutoff=CUTOFF,namespace='DOW30_LONG_EXTENDED_OOF_RESEARCH_20261031',
      trainingPopulationPolicy='ACTUAL_IPO_AND_VERIFIED_ISSUER_ELIGIBILITY',issuerRulesBySymbol=rules,expectedTrainingRows=expected_rows,trainingPopulations=eligibility,
      pricePolicy='COHERENT_PROVIDER_SPLIT_ADJUSTED_CLOSE_WITH_ASOF_NOMINAL_FEATURES',providerSnapshotDate='2024-06-26',pricesBySymbol=prices,providerEventsBySymbol=events,
      financialsBySymbol=financial,sharesBySymbol=shares,benchmark=benchmark,inputHashes=hashes)
    save(ROOT/'inputs.json',value)
    previous=load(base.ROOT/'protocol.json')
    protocol=dict(previous,sealedAtUtc=datetime.now(timezone.utc).isoformat(),inputsPath=str(ROOT/'inputs.json'),inputsSha256=sha(ROOT/'inputs.json'),
      trainingDecisions=train,trainingRows=expected_rows,testIntervals=targets,inputHashes=hashes,exposureAuditSha256=sha(ROOT/'prior-label-exposure.json'),
      originalProtocolSha256=sha(base.ROOT/'protocol.json'),
      amendmentReason='Before any held-out outcomes/MAE, functional readiness lacked2TRVbuckets(64/107<200). Extend earlier actual history only; preserve targetdates/cohort/min200/candidates/cutoff. No performance-based choices.',
      replayClockAdmission='Only exact present-anchored V8/V9 source-snapshot query is admitted relative to sealed historical decision; all source dates and label maturity remain strict. No production formula edits.',
      trainingEligibility='DOW excluded until80regularwaycloses; DISlegacyissuer untilMar19 2019, gapMar20-May8excluded until newissuerpriorfiling knownMay9; targets30mandatory.',
      limitations=previous['limitations']+['Historical training has29/30or28eligible stocks, not a claim of actual index membership. LegacyDisney data cannot cross2019merger/new307mshareissuance.'])
    save(ROOT/'protocol.json',protocol)
    print(json.dumps(dict(protocol=str(ROOT/'protocol.json'),sha256=sha(ROOT/'protocol.json'),trainingDates=len(train),trainingRows=expected_rows,
       cohortCounts=sorted(set(len(v) for v in eligibility.values())),oldExposureRows=len(old),testOverlapRows={d:exposure[d]['priorAnyHorizonLabelWindows']['overlapRows'] for d in TEST})))


def evaluate(replay):
    readiness=load(Path(replay).parent/'readiness.json')
    if not readiness.get('ready') or not all(bucket_ready(r['matureOOFRows']) for r in readiness['targetChecks']):raise ValueError('Functional200bucketcoverage required; no scoring')
    base.ROOT=ROOT
    base.evaluate(replay)


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('mode',choices=['prepare','evaluate']);p.add_argument('--replay');a=p.parse_args()
    prepare() if a.mode=='prepare' else evaluate(a.replay)
