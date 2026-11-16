"""New evidence and prespecified pre2023 research. No DB writes or promotion."""
import argparse
import csv
import hashlib
import io
import json
import math
import subprocess
from collections import defaultdict
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path

import numpy as np
from quant_history_usage import window_usage, require_unused_windows
from quant_20d_error_research import fit_ridge, predict_ridge, metrics

ROOT=Path('tmp/quant-strict-unused-2021-20261031-v1')
SOURCES=Path('tmp/quant-strict-dow-inputs-20261031-v1')
ROSTER=sorted('MMM AXP AMGN AAPL BA CAT CVX CSCO KO DIS DOW GS HD HON IBM INTC JNJ JPM MCD MRK MSFT NKE PG CRM TRV UNH VZ V WBA WMT'.split())
TRAIN=['2020-12-24','2020-12-31','2021-01-08','2021-01-15','2021-01-22','2021-01-29','2021-02-03','2021-02-05','2021-02-10','2021-02-12','2021-02-19','2021-02-26']
TEST=['2021-03-31','2021-04-30']
FACTORS=['valuation','quality','growth','stability','earnings','analyst','news','momentum','risk','institution']


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def save(path,value):
    with Path(path).open('x',encoding='utf-8') as f:
        json.dump(value,f,indent=2,allow_nan=False)


def load(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def query(sql):
    # Only static internal read queries, not SQL supplied by users/documents.
    command=['.tools/mariadb-11.8.2-winx64/bin/mariadb.exe','-u','root',
             '--batch','--raw','king_yurina','-e','SET SESSION TRANSACTION READ ONLY; '+sql]
    text=subprocess.check_output(command).decode('utf-8')
    return list(csv.DictReader(io.StringIO(text),delimiter='\t'))


def nominal_close(close,day,events):
    value=Decimal(str(close))
    if not value.is_finite() or value<=0:
        raise ValueError('Actual positive provider close required')
    for event in events:
        n,d=Decimal(str(event['numerator'])),Decimal(str(event['denominator']))
        if not n.is_finite() or not d.is_finite() or n<=0 or d<=0:
            raise ValueError('Invalid documented provider adjustment')
        if day<event['day']:
            value*=n/d
    return value


def share_arithmetic(row):
    if not row['filedDate']<row['decision'] or not row['measurementDate']<row['decision']:
        raise ValueError('Strict prior filing and measurement required')
    count=Decimal(str(row['totalEconomicCommonShares']))
    ratio=Decimal(str(row['sharesPerQuotedUnit']))
    if not count.is_finite() or count<=0 or count!=count.to_integral_value() or not ratio.is_finite() or ratio<=0:
        raise ValueError('Actual integral common shares and explicit positive quote ratio required')
    total=Decimal(0)
    for item in row['classes']:
        value=Decimal(str(item['value']))
        if item.get('unit')!='shares' or not value.is_finite() or value<0 or value!=value.to_integral_value():
            raise ValueError('Invalid original class observation')
        total+=value*Decimal(str(item.get('economicSharesPerShare',1)))
    total-=Decimal(str(row.get('excludedIntragroupShares',0)))
    if total!=count:
        raise ValueError('Economic class sum differs from reported evidence')
    return count/ratio


def check_complete_prices(prices,population,calendar):
    for symbol in population:
        series=prices.get(symbol,[])
        days=[r['day'] for r in series]
        if len(days)!=len(set(days)) or set(days)!=set(calendar):
            raise ValueError('Incomplete/duplicate full historical calendar: '+symbol)
        if any(not Decimal(str(r['close'])).is_finite() or Decimal(str(r['close']))<=0 for r in series):
            raise ValueError('Nonpositive/nonfinite price')


def bundle_shares():
    paths=['tmp/v9-shares-a-20261031-v1/shares-reviewed-v2.json',
           'tmp/v9-shares-b-20261031-v1/observations.json']
    a,b=map(load,paths)
    rows=a['entries']+b
    expected=set('ABNB APP DASH DDOG GOOG GOOGL META MSTR PDD PLTR SHOP'.split())
    keys=set(); hashes={p:sha(p) for p in paths}; prepared=[]
    for row in rows:
        key=row['decision'],row['symbol']
        if key in keys:
            raise ValueError('Duplicate share evidence')
        keys.add(key)
        value=share_arithmetic(row)
        source=row['sourcePath']
        if sha(source)!=row['sourceSha256']:
            raise ValueError('Primary source hash mismatch')
        hashes[source]=sha(source)
        prepared.append(dict(row,quotedUnitEquivalentShares=str(value),
                             sourceClassification='PRIMARY_FILED_CLASS_SUM_RESEARCH_NOT_EXACT_DAILY_FLOAT'))
    if keys!={(d,s) for d in ['2026-04-30','2026-05-29'] for s in expected}:
        raise ValueError('Missing complete11security coverage')
    ROOT.mkdir(parents=True,exist_ok=True)
    value=dict(classification='PRIOR_PRIMARY_SHARE_EVIDENCE_COVERAGE_ONLY_NOT_REPEAT_ERROR_EVALUATION',
               observations=prepared,inputHashes=hashes,databaseWrites=0,
               tickerDecisionCoverage=22,uniqueIssuers=10,
               limitations=['Not daily exact/float shares; original reported precision retained.',
                            'GOOG and GOOGL are one issuer; never add both issuer capitalizations.',
                            'APP uses earlier complete-class snapshot, not latest incomplete cover sum.',
                            'No old April/May errors recalculated and no source-based model promotion.'])
    save(ROOT/'v9-share-coverage.json',value)
    print('V9_SHARE_EVIDENCE_COVERAGE_PASS 22observations/11tickers/10issuers')


def prepare():
    if (ROOT/'protocol.json').exists():
        raise ValueError('Protocol already sealed, refuse reselection')
    ROOT.mkdir(parents=True,exist_ok=True)
    hashes={str(SOURCES/'DJIA-2020-2021.csv'):sha(SOURCES/'DJIA-2020-2021.csv')}
    benchmark=[]
    for r in csv.DictReader((SOURCES/'DJIA-2020-2021.csv').read_text(encoding='utf-8').splitlines()):
        if r['DJIA'] not in ('','.') and r['observation_date']>='2020-09-01':
            v=Decimal(r['DJIA'])
            if not v.is_finite() or v<=0:
                raise ValueError('Invalid official benchmark close')
            benchmark.append(dict(day=r['observation_date'],level=str(v),source='FRED_DJIA_SP_DOW_JONES_PRICE_INDEX'))
    calendar=[r['day'] for r in benchmark]
    prices=defaultdict(list)
    symbols=','.join("'"+s+"'" for s in ROSTER if s!='WBA')
    native=query("SELECT symbol,trade_date,close_price,source FROM stock_candle_daily WHERE symbol IN ("+symbols+") AND trade_date BETWEEN '2021-06-07' AND '2021-06-30' ORDER BY symbol,trade_date")
    events_by_symbol={}; source_audit={}
    for symbol in ROSTER:
        if symbol=='WBA':
            continue
        path=Path('tmp/pit-acquisition/actions/raw')/(symbol+'.json')
        original=load(path)['chart']['result'][0]
        if original['meta']['symbol']!=symbol:
            raise ValueError('Wrong original price issuer')
        hashes[str(path)]=sha(path)
        events=[]
        for event in original.get('events',{}).get('splits',{}).values():
            day=datetime.fromtimestamp(event['date'],timezone.utc).strftime('%Y-%m-%d')
            events.append(dict(day=day,numerator=str(event['numerator']),denominator=str(event['denominator']),
                               splitRatio=event['splitRatio']))
        events_by_symbol[symbol]=events
        archive={datetime.fromtimestamp(t,timezone.utc).strftime('%Y-%m-%d'):v
                 for t,v in zip(original.get('timestamp',[]),original['indicators']['quote'][0]['close']) if v is not None}
        rows=[r for r in native if r['symbol']==symbol]
        if any(r['trade_date'] not in archive or abs(Decimal(r['close_price'])-Decimal(str(archive[r['trade_date']])))>Decimal('0.00002') for r in rows):
            raise ValueError('Canonical/archive close basis differs '+symbol)
        for day,close in sorted(archive.items()):
            if day not in calendar:
                continue
            prices[symbol].append(dict(day=day,close=str(nominal_close(close,day,events)),
                                       source='ORIGINAL_PROVIDER_UNDO_DOCUMENTED_ADJUSTMENTS_NOMINAL_RESEARCH'))
        source_audit[symbol]=dict(overlapCompared=len(rows),futureAdjustmentEvents=events,
            limitation='Provider ratios include separations, sometimes rounded; nominal research reconstruction, not certified historic execution/PIT/total-return data.')
    wba=Path('tmp/quant-unused-dow-20261031-v1/wba-closes-extended-2020-2023.csv')
    hashes[str(wba)]=sha(wba)
    rows=list(csv.DictReader(wba.read_text(encoding='utf-8-sig').splitlines()))
    for r in rows:
        day=r.get('date',r.get('day'))
        if day in calendar:
            prices['WBA'].append(dict(day=day,close=r['close'],source='STOCKANALYSIS_SP_GLOBAL_MARKET_INTELLIGENCE_SPLIT_ADJUSTED_CLOSE_RESEARCH'))
    check_complete_prices(prices,ROSTER,calendar)
    old=query('SELECT signal_date AS signalDate,entry_trade_date AS entryTradeDate,exit_trade_date AS exitTradeDate,horizon_days AS horizonDays FROM stock_backtest_result_v2')
    old=[dict(r,horizonDays=int(r['horizonDays'])) for r in old]
    # The earlier DB-only inventory missed file-only S&P training. Exclude the
    # ENTIRE recorded input calendar conservatively, even before labels mature.
    file_exposure=[]
    for path in sorted(Path('output').glob('sp500*/**/manifest.json')):
        meta=load(path)
        if not isinstance(meta,dict) or not meta.get('start') or not meta.get('end'):
            continue
        first,last=meta['start'],meta['end']
        hashes[str(path)]=sha(path)
        file_exposure.append(dict(source=str(path),entry=first,exit=last,
                                  classification='CONSERVATIVE_WHOLE_DATASET_CALENDAR_NOT_ACTUAL_SINGLE_LABEL'))
        old.append(dict(signalDate=first,entryTradeDate=first,exitTradeDate=last,horizonDays=0))
    if not file_exposure:
        raise ValueError('File-only S&P research exposure inventory missing')
    for path in [Path('tmp/quant-new-period-20261031-v1/replay-v8-complete/result.json')]:
        report=load(path);hashes[str(path)]=sha(path)
        old.extend(dict(signalDate=d['decision'],entryTradeDate=p['entry'],exitTradeDate=p['exit'],horizonDays=20)
                   for d in report['dates'] for p in d['pairs'])
    audit={}; intervals=[]
    for day in TEST:
        holding=[d for d in calendar if d>day][:21]
        if len(holding)!=21:
            raise ValueError('Incomplete held calendar')
        audit[day]=dict(entry=holding[0],exit=holding[-1],
                       priorAnyHorizonLabelWindows=window_usage(old,holding[0],holding[-1],None))
        intervals.append(dict(decision=day,entry=holding[0],exit=holding[-1]))
    require_unused_windows(audit)
    meta=dict(oldRows=len(old),earliestOldEntry=min(r['entryTradeDate'] for r in old),
              oldWindowSha256=hashlib.sha256(json.dumps(old,sort_keys=True).encode()).hexdigest(),
              intervals=audit,fileOnlyConservativeExposure=file_exposure,
              strictUnusedGatePassed=True,queryContainsNoOutcomes=True)
    save(ROOT/'prior-window-usage.json',meta)
    financial=load(SOURCES/'WBA-financials.json');shares=load(SOURCES/'WBA-shares.json')
    for p in [SOURCES/'WBA-companyfacts.json',SOURCES/'WBA-financials.json',SOURCES/'WBA-shares.json',
              SOURCES/'WBA-verification.json',Path('tmp/quant-unused-dow-20261031-v1/sources-manifest.json'),
              SOURCES/'WBA-free-5Y-original.json',
              Path('tmp/quant-unused-dow-20261031-v1/source-manifest-extended-success.json'),
              Path('scripts/quant_strict_unused_study.py'),Path('scripts/quant_history_usage.py'),
              Path('scripts/quant_20d_error_research.py'),Path('scripts/QuantStrictDowReplay.java'),
              Path('scripts/sp500_daily_validation.py')]:
        hashes[str(p)]=sha(p)
    value=dict(population=ROSTER,trainingDecisions=TRAIN,testDecisions=TEST,
               pricePolicy='SEALED_ALL_SYMBOLS_PREPARED_NOMINAL',pricesBySymbol=dict(prices),
               financialsBySymbol={'WBA':financial},sharesBySymbol={'WBA':shares},benchmark=benchmark,
               priceBasisAudit=source_audit,inputHashes=hashes,
               classification='RECONSTRUCTED_2021_DOW30_RESEARCH_COHORT_NOT_CERTIFIED_COMPLETE_PIT')
    save(ROOT/'inputs.json',value)
    protocol=dict(sealedAtUtc=datetime.now(timezone.utc).isoformat(),
        inputsPath=str(ROOT/'inputs.json'),inputsSha256=sha(ROOT/'inputs.json'),
        decisions=TEST,intervals=intervals,trainingDecisions=TRAIN,fixedTrainingCutoff='2021-03-31',
        fullPopulation=ROSTER,expectedTrainingRows=360,expectedOutcomes=60,
        comparisonModelVersion='EXPECTED_RETURN_V9_CORRECTED_20261017',
        candidates=['existing_v9_reconstructed','train_pooled_median','ten_factor_ridge_alpha10'],
        reference='zero_return',ridgeAlpha=10,primaryMetrics=['absoluteMAEpp','excessMAEpp'],
        usageAuditPath=str(ROOT/'prior-window-usage.json'),usageAuditSha256=sha(ROOT/'prior-window-usage.json'),
        inputHashes=hashes,promotionAllowed=False,noOutcomeBasedReselection=True,
        limitations=['Different old universe:30Dow research names, not a Nasdaq100validation or presentLIVEbacktest.',
                     'Partial official roster-change evidence, not certified full point-in-time population/receipt proof.',
                     'Unknown historic sectors/missing source factors retain explicit product fallback, not invented observations.',
                     'Provider adjustment reversal includes rounded separations: reconstructed nominal prices, not exact certified execution/total-return/PIT.',
                     'Fixed360prior observations/12overlapping training timegroups, only2testgroups; no confidence/significance or promotion claim.',
                     'DJIA via FRED is an official-source price index, copyrighted; free retrieval is not redistribution permission.'])
    save(ROOT/'protocol.json',protocol)
    print(json.dumps(dict(protocol=str(ROOT/'protocol.json'),sha256=sha(ROOT/'protocol.json'),
                          oldWindows=len(old),strictOverlapRows={d:audit[d]['priorAnyHorizonLabelWindows']['overlapRows'] for d in TEST},
                          fullNames=len(ROSTER),pricesPerName=len(calendar))))


def evaluate(replay_path):
    protocol=load(ROOT/'protocol.json')
    if sha(ROOT/'inputs.json')!=protocol['inputsSha256']:
        raise ValueError('Sealed input drift')
    for p,h in protocol['inputHashes'].items():
        if sha(p)!=h:
            raise ValueError('Code/source drift '+p)
    report=load(replay_path)
    if report['protocolSha256']!=sha(ROOT/'protocol.json') or report['comparisonModelVersion']!=protocol['comparisonModelVersion']:
        raise ValueError('Replay/protocol mismatch')
    train=report['trainingRows']
    if len(train)!=360 or len({(r['signalDate'],r['symbol']) for r in train})!=360:
        raise ValueError('Wrong training denominator')
    if any(r['signalDate']>='2021-03-31' or r['exitTradeDate']>='2021-03-31' for r in train):
        raise ValueError('Unpurged training')
    x=np.array([[float(r[f+'Score']) for f in FACTORS] for r in train])
    fits={}
    for target in ['Absolute','Excess']:
        y=np.array([float(r['forwardReturnPct'])-(float(r['benchmarkReturnPct']) if target=='Excess' else 0) for r in train])
        fits[target]=dict(ridge=fit_ridge(x,y,10),median=float(np.median(y)))
    observations=[]
    if [d['decision'] for d in report['dates']]!=TEST:
        raise ValueError('Test date drift')
    for day in report['dates']:
        signals={r['symbol']:r for r in day['newSignals']}
        if set(signals)!=set(ROSTER) or sorted(r['symbol'] for r in day['pairs'])!=ROSTER:
            raise ValueError('Full30cohort absent or duplicate')
        for p in day['pairs']:
            row=dict(p,decision=day['decision'])
            tx=[float(signals[p['symbol']][f+'Score']) for f in FACTORS]
            for target in fits:
                row['median'+target]=fits[target]['median']
                row['ridge'+target]=float(predict_ridge(fits[target]['ridge'],tx))
            observations.append(row)
    result_metrics={};daily={}
    for label,rows in [('all',observations)]+[(d,[p for p in observations if p['decision']==d]) for d in TEST]:
        per={}
        for target in ['Absolute','Excess']:
            actual=[float(r['actual'])-(float(r['benchmark']) if target=='Excess' else 0) for r in rows]
            per[target]={}
            for name,prefix in [('existing_v9_reconstructed','baseline'),('train_pooled_median','median'),('ten_factor_ridge_alpha10','ridge')]:
                per[target][name]=metrics([float(r[prefix+target]) for r in rows],actual)
            per[target]['zero_return']=metrics([0]*len(actual),actual)
        if label=='all':
            result_metrics=per
        else:
            daily[label]=per
    out=dict(protocolSha256=sha(ROOT/'protocol.json'),replaySha256=sha(replay_path),
             comparisonModelVersion=protocol['comparisonModelVersion'],outcomes=len(observations),
             trainingRows=len(train),metrics=result_metrics,perDate=daily,
             fittedOnlyOnFixedTraining=fits,pairs=observations,promotionAllowed=False,
             limitations=protocol['limitations'])
    save(ROOT/'evaluation.json',out)
    print(json.dumps({k:v for k,v in out.items() if k in ['outcomes','trainingRows','metrics','perDate']},indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('mode',choices=['shares','prepare','evaluate']);parser.add_argument('--replay')
    args=parser.parse_args()
    if args.mode=='shares':bundle_shares()
    elif args.mode=='prepare':prepare()
    else:evaluate(args.replay)
