"""Full-model, training-only study adapter; explicit canonical facts and primary totals."""
import argparse
import collections
import copy
import json
import math
from datetime import date
from decimal import Decimal
from pathlib import Path
import numpy as np
import quant_v9_eps_growth as utility
import quant_eps_training_recovery_v2 as paired
import quant_v9_nested_training as metrics
import quant_quarter_highvol as conditional

ROOT=Path('tmp/quant-full-financial-study-20261101-v1')
BASE=Path('tmp/quant-eps-v9-training-20261101-v2')
PACK=Path('tmp/quant-quarterly-financials-20261101-v3')
BASE_INPUT_SHA='5eb790770bfa3b1c9188de10c484c044cfa3418583c1f5958c81317eedfd3af9'
BASE_FORECAST_SHA='39a49257f2d1281b83138bce251606ee95b394fc996ef3cdaa38ca0c9d711b48'
PACK_SHA='bdc1c1cf79bd3dc929dfb5b364b18b43be53fcfa11e88a26994a86ddeebcdd99'
NAMESPACE='DOW30_FULL_FINANCIAL_RESEARCH_20261101'
OVERRIDE_IDENTITY=['symbol','cik','accessionNumber','filedAt','startDate','endDate','form','fiscalPeriod']
IDENTITY=['symbol','cik','accessionNumber','filedAt','form','startDate','endDate','periodDays','currency']
DTO_KEYS=['id','symbol','cik','fiscalYear','fiscalPeriod','form','filedAt','startDate','endDate','accessionNumber','periodDays','currency','revenue','operatingIncome','netIncome','epsDiluted','assets','liabilities','equity','operatingCashFlow','epsUnit','rawJson','mappedAt']
PRIORITY={'revenue':['Revenues','RevenueFromContractWithCustomerExcludingAssessedTax','SalesRevenueNet','RevenuesNetOfInterestExpense','RevenueFromContractWithCustomerIncludingAssessedTax'],
          'netIncome':['NetIncomeLoss','NetIncomeLossAvailableToCommonStockholdersBasic'],
          'operatingIncome':['OperatingIncomeLoss'],'epsDiluted':['EarningsPerShareDiluted']}
sha,load,save=utility.sha,utility.load,utility.save
join_pairs=paired.join_pairs

def key(row,fields=IDENTITY):return tuple(utility.cik(row.get(f)) if f=='cik' else row.get(f) for f in fields)

def validate_override(entry,target):
    proof=entry.get('proof',{});identity=entry.get('identity',{})
    if key(identity,OVERRIDE_IDENTITY)!=key(target,OVERRIDE_IDENTITY):raise ValueError('Primary target identity mismatch')
    if proof.get('totalScopeConfirmed') is not True or proof.get('sourceDocumentAccession')!=target['accessionNumber'] or proof.get('periodStart')!=target['startDate'] or proof.get('periodEnd')!=target['endDate'] or proof.get('currency')!='USD':raise ValueError('Primary total/source period not proved')
    if date.fromisoformat(proof['sourcePublicationDate'])>date.fromisoformat(target['filedAt']):raise ValueError('Later primary publication cannot backfill an earlier filing')
    if not proof.get('sourceURL','').startswith('https://') or not proof.get('tableLabel') or not proof.get('excerpt') or not proof.get('pageOrLocator'):raise ValueError('Primary document/table proof missing')
    if sha(proof['localDocumentPath'])!=proof['sourceSha256']:raise ValueError('Primary document SHA mismatch')
    if proof.get('originalContextProof'):
        context=proof['originalContextProof']
        if sha(context['localDocumentPath'])!=context['sourceSha256'] or context.get('dimensions',[])!=[]:raise ValueError('Original dimensional/context proof mismatch')
    printed=str(proof['printedValue']).replace(',','').replace('$','').replace(' ','').replace('(','-').replace(')','')
    value=Decimal(str(entry['revenueUsd']));scaled=Decimal(printed)*Decimal(str(proof['unitsScale']))
    if not value.is_finite() or value<=0 or value!=scaled:raise ValueError('Printed primary value or USD scale mismatch')
    return float(value)

def canonical_field(row,field,archive,override):
    if utility.cik(archive.get('cik'))!=utility.cik(row['cik']):raise ValueError('Issuer/archive mismatch')
    unit='USD/shares' if field=='epsDiluted' else 'USD'
    for concept in PRIORITY[field]:
        if concept=='RevenueFromContractWithCustomerIncludingAssessedTax' and row['symbol']!='DOW':continue
        candidates=[o for o in archive.get('facts',{}).get('us-gaap',{}).get(concept,{}).get('units',{}).get(unit,[]) if all(o.get(a)==row[b] for a,b in [('accn','accessionNumber'),('filed','filedAt'),('form','form'),('start','startDate'),('end','endDate')])]
        if not candidates:continue
        values={Decimal(str(o['val'])) for o in candidates}
        if len(values)!=1 or not all(v.is_finite() for v in values):raise ValueError('Conflicting exact source concept values')
        value=next(iter(values))
        return float(value),dict(concept=concept,taxonomy='us-gaap',unit=unit,startDate=row['startDate'],endDate=row['endDate'],periodDays=row['periodDays'],sourceFiscalYear=candidates[0].get('fy'),sourceFiscalPeriod=candidates[0].get('fp'),sourceObservationJson=json.dumps(candidates[0],sort_keys=True))
    if field=='revenue' and override is not None:
        value=validate_override(override,row)
        # Full override artifacts retain all collateral facts; embed only compact actual document proof.
        proof={k:v for k,v in override['proof'].items() if k!='originalFieldProofs'}
        return value,dict(concept='PRIMARY_FILING_CONSOLIDATED_TOTAL_REVENUE',taxonomy='VERIFIED_PRIMARY_FILING',unit='USD',startDate=row['startDate'],endDate=row['endDate'],periodDays=row['periodDays'],primaryDocumentProof=proof)
    return None,None

def merge_financials(original,source,archives,overrides):
    before={key(r):r for values in original.values() for r in values}
    if len(before)!=sum(map(len,original.values())):raise ValueError('Duplicate original physical period key')
    staged={key(e['identity'],OVERRIDE_IDENTITY):e for e in overrides}
    if len(staged)!=len(overrides):raise ValueError('Duplicate primary override')
    result=collections.defaultdict(list);matched=set();used=set();audit=collections.Counter();changes=[]
    for symbol,values in source.items():
        for fresh in values:
            k=key(fresh);previous=before.get(k);row={name:copy.deepcopy(fresh.get(name)) for name in DTO_KEYS};row['rawJson']=None
            if previous:
                matched.add(k)
                for name in ['id','mappedAt','assets','liabilities','equity','operatingCashFlow']:row[name]=previous.get(name)
            previous_raw=json.loads(previous.get('rawJson') or '{}') if previous else {}
            evidence=copy.deepcopy(previous_raw.get('selectedFieldEvidence',{}));override=staged.get(key(fresh,OVERRIDE_IDENTITY))
            if override:
                if fresh.get('revenue') is not None:raise ValueError('Primary recovery can fill only missing source revenue')
                validate_override(override,fresh);used.add(key(fresh,OVERRIDE_IDENTITY))
            for field in PRIORITY:
                value,proof=canonical_field(fresh,field,archives[(symbol,utility.cik(fresh['cik']))],override)
                old_value=previous.get(field) if previous else None;old_proof=evidence.get(field,{})
                if old_value is not None:
                    if value is None:row[field]=old_value;audit['preservedOriginalUnavailableInCurrentArchive']+=1;continue
                    if Decimal(str(value))!=Decimal(str(old_value)):
                        old_concept=old_proof.get('concept');new_concept=proof['concept'];order=PRIORITY[field]
                        if old_concept==new_concept or old_concept not in order or new_concept not in order or order.index(new_concept)>=order.index(old_concept):raise ValueError('Non-null same/lower/unknown source-basis conflict '+str((symbol,field,old_concept,new_concept,old_value,value)))
                        audit['priorityUpgrades']+=1;changes.append(dict(symbol=symbol,accession=fresh['accessionNumber'],start=fresh['startDate'],end=fresh['endDate'],field=field,before=old_value,after=value,oldConcept=old_concept,newConcept=new_concept,reason='UNCHANGED_PRODUCT_PRIORITY_NEWLY_AVAILABLE_ORIGINAL_FACT'))
                row[field]=value
                if proof:evidence[field]=proof
            row['epsUnit']='USD/shares' if row.get('epsDiluted') is not None else previous.get('epsUnit') if previous else None
            raw=dict(selectedFieldEvidence=evidence,fullFinancialRecovery=dict(policy='ACTUAL_PRODUCT_CONCEPT_PRIORITIES_EXACT_PERIOD_WITH_PRIMARY_TOTALS',phaseEvidence=fresh.get('_phaseEvidence'),preservedOldNonIncomeFields=bool(previous),originalRawEvidenceRetainedInSealedBaseline=bool(previous)))
            row['rawJson']=json.dumps(raw,sort_keys=True)
            result[symbol].append(row);audit['newRows']+=1;audit['matchedOriginal']+=int(previous is not None)
    for k,row in before.items():
        if k not in matched:result[row['symbol']].append(copy.deepcopy(row));audit['retainedUnmatchedOriginal']+=1
    if used!=staged.keys():raise ValueError('Primary override has no exact source target')
    for values in result.values():values.sort(key=lambda r:(r['filedAt'],r['endDate'],r['accessionNumber']))
    return dict(result),dict(audit,priorityUpgrades=audit['priorityUpgrades'],retainedUnmatchedOriginal=audit['retainedUnmatchedOriginal'],appliedPrimaryRevenues=len(used),priorityChanges=changes)

def document_hashes(value):
    result={}
    def visit(v):
        if isinstance(v,dict):
            if 'localDocumentPath' in v and 'sourceSha256' in v:result[v['localDocumentPath']]=v['sourceSha256']
            for item in v.values():visit(item)
        elif isinstance(v,list):
            for item in v:visit(item)
    visit(value);return result

def prepare():
    if ROOT.exists():raise ValueError('Never overwrite a study')
    if sha(BASE/'inputs.json')!=BASE_INPUT_SHA or sha(BASE/'replay/forecast-only.json')!=BASE_FORECAST_SHA or sha(PACK/'financials.json')!=PACK_SHA:raise ValueError('Frozen baseline/source changed')
    source=load(PACK/'financials.json');manifest=load(PACK/'source-manifest.json');archives={};source_hashes={}
    for record in manifest['records']:
        path=record['archivePath'];source_hashes[path]=record['sha256']
        if sha(path)!=record['sha256']:raise ValueError('Primary companyfacts SHA changed')
        archives[(record['symbol'],utility.cik(record['cik']))]=load(path)
    override_paths=[Path('tmp/quant-revenue-primary-goods-20261101-v1/overrides.json'),Path('tmp/quant-revenue-primary-services-20261101-v1/overrides.json')]
    overrides=[e for p in override_paths for e in load(p)]
    source_hashes.update(document_hashes(overrides));utility.verify_sources(source_hashes)
    value=load(BASE/'inputs.json');old=copy.deepcopy(value['financialsBySymbol']);financials,audit=merge_financials(old,source,archives,overrides)
    value['financialsBySymbol']=financials;value['namespace']=NAMESPACE;value['testDecisions']=[]
    ROOT.mkdir();save(ROOT/'merged-financials.json',financials);save(ROOT/'merge-audit.json',audit)
    plan=Path('docs/superpowers/plans/2026-11-01-full-v9-financial-replay.md');save(ROOT/'design-prespec.json',dict(path=str(plan),sha256=sha(plan),text=plan.read_text(encoding='utf-8')))
    baseline_panel_protocol=load(utility.PANEL.parent/'protocol.json')
    if sha(utility.PANEL)!=baseline_panel_protocol['inputHashes'][str(utility.PANEL)]:raise ValueError('Archived market-regime panel changed')
    paths=[BASE/'inputs.json',BASE/'replay/forecast-only.json',PACK/'financials.json',PACK/'source-manifest.json',ROOT/'merged-financials.json',ROOT/'merge-audit.json',ROOT/'design-prespec.json',utility.PANEL,utility.PANEL.parent/'protocol.json',*override_paths]
    paths.extend(Path(p) for p in ['scripts/quant_full_financial_study.py','scripts/test_quant_full_financial_study.py','scripts/QuantFullFinancialTrainingReplay.java','scripts/QuantFullFinancialTrainingReplayTest.java','scripts/quant_eps_training_recovery_v2.py','scripts/quant_v9_nested_training.py','scripts/quant_quarter_highvol.py'])
    paths.extend(Path('tmp/quant-full-financial-classes-20261101')/(name+'.class') for name in ['QuantFullFinancialTrainingReplay','QuantFullFinancialTrainingReplayTest'])
    hashes={str(p):sha(p) for p in paths};hashes.update(source_hashes);value['inputHashes']=hashes
    save(ROOT/'inputs.json',value)
    protocol=dict(inputsPath=str(ROOT/'inputs.json'),inputsSha256=sha(ROOT/'inputs.json'),inputHashes=hashes,classification='INTERNAL_PREVIOUSLY_EXPOSED_FULL_V9_FINANCIAL_REPLAY_NOT_NEW_HOLDOUT',old60TargetsUsed=False,productionWeightsChanged=False,promotionAllowed=False,
                  financialSelection='SEALED_FINANCIAL_UNION_ONLY_NO_NATIVE_DB_FALLBACK',primary=dict(rows=17904,dates=609,fromInclusive='2018-04-01',toInclusive='2020-08-28'),ancillary=dict(oofRows=25473,oofDates=870),
                  highVolatility='observedmarketvol20>=25 percent; same109dates3250rows',bootstrap=dict(blockDates=21,draws=1000,seed=20261101),mergeSummary={k:v for k,v in audit.items() if k!='priorityChanges'})
    utility.verify_sources(hashes);save(ROOT/'protocol.json',protocol);print(json.dumps(dict(protocolSha256=sha(ROOT/'protocol.json'),mergeSummary=protocol['mergeSummary'],financialRows=sum(map(len,financials.values())))))

def compare():
    protocol=load(ROOT/'protocol.json');utility.verify_sources(protocol['inputHashes']);baseline=load(BASE/'replay/forecast-only.json');report=load(ROOT/'replay/result.json')
    if report['protocolSha256']!=sha(ROOT/'protocol.json') or report['databaseWrites']!=0 or report['heldoutOutcomesComputed'] or report['financialSelectionPolicy']!='SEALED_FINANCIAL_UNION_ONLY_NO_NATIVE_DB_FALLBACK':raise ValueError('Full replay contract violated')
    pairs=join_pairs(baseline['trainingRows'],report['trainingRows'],baseline['oofPredictions'],report['oofPredictions']);primary=[r for r in pairs if r['day']>='2018-04-01']
    if len(pairs)!=25473 or len(primary)!=17904 or len({r['day'] for r in primary})!=609:raise ValueError('Cohort changed')
    scopes={}
    for name,rows in [('primary',primary),('allOOFAncillary',pairs)]:scopes[name]=dict(rows=len(rows),dates=len({r['day'] for r in rows}),before=metrics.summarize(rows,np.array([r['base'] for r in rows])),after=metrics.summarize(rows,np.array([r['new'] for r in rows])))
    panel=load(conditional.old.PANEL);market_vol={(r['day'],r['symbol']):r['x'][panel['features'].index('market_vol20')] for r in panel['rows']}
    mask=np.array([market_vol[(r['day'],r['symbol'])]>=25 for r in primary]);high=[r for r,m in zip(primary,mask) if m]
    if len(high)!=3250 or len({r['day'] for r in high})!=109:raise ValueError('High-volatility cohort changed')
    scopes['highVolatility']=dict(rows=len(high),dates=109,before=metrics.summarize(high,np.array([r['base'] for r in high])),after=metrics.summarize(high,np.array([r['new'] for r in high])))
    quarters=collections.defaultdict(list)
    for r in primary:quarters[r['day'][:4]+'Q'+str((int(r['day'][5:7])-1)//3+1)].append(r)
    for name,rows in quarters.items():scopes[name]=dict(rows=len(rows),before=metrics.summarize(rows,np.array([r['base'] for r in rows])),after=metrics.summarize(rows,np.array([r['new'] for r in rows])))
    before={(r['signalDate'],r['symbol']):r for r in baseline['trainingRows']};changed={f:sum(before[(r['signalDate'],r['symbol'])].get(f)!=r.get(f) for r in report['trainingRows']) for f in paired.FACTORS}
    output=dict(protocolSha256=sha(ROOT/'protocol.json'),resultSha256=sha(ROOT/'replay/result.json'),comparison=scopes,
                bootstrap=metrics.block_interval(primary,np.array([r['new'] for r in primary]),np.array([r['base'] for r in primary])),
                highVolBootstrap=conditional.conditional_interval(primary,np.array([r['new'] for r in primary]),np.array([r['base'] for r in primary]),mask),changedScores=changed,pairs=pairs,
                labelsAndCohortsIdentical=True,old60TargetsUsed=False,promotionAllowed=False,productionWeightsChanged=False,classification=protocol['classification'])
    save(ROOT/'comparison.json',output);print(json.dumps({k:output[k] for k in ['comparison','bootstrap','highVolBootstrap','changedScores']},indent=2))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('mode',choices=['prepare','compare']);args=parser.parse_args();prepare() if args.mode=='prepare' else compare()
