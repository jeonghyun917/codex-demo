"""Evidence-gated free recovery. No forced rebasing, successor-price substitution or DB writes."""
import json
import shutil
import subprocess
import sys
from collections import defaultdict,Counter
from pathlib import Path
from build_sp500_daily_dataset import ROOT,read_csv,write_csv,digest,price_flags
from recover_sp500_daily_dataset import validate_chart,compare_bar
from acquire_sp500_free_followup import OUT

BASE=ROOT/'output/sp500-validation-20261027/dataset'
DATA=OUT/'dataset'
NAMES={'FERG':'ferguson','RDDT':'reddit','VLTO':'veralto','FISV':'fiserv','EA':'electronic arts',
       'EQR':'equity residential','LEG':'leggett','QRVO':'qorvo','GEN':'gen digital',
       'GAP':'gap','FBIN':'fortune brands','BBWI':'bath','SEDG':'solaredge','SMCI':'super micro','DELL':'dell'}
REJECT={'INFO':'TICKER_REUSED_ETF_NOT_IHS_MARKIT','LB':'TICKER_REUSED_LANDBRIDGE_NOT_L_BRANDS',
        'PARA':'TICKER_REUSED_BANZAI_NOT_PARAMOUNT','VMRK':'MERGER_RATIO_AND_PROVIDER_PREDECESSOR_IDENTITY_UNRESOLVED',
        'SBNY':'POST_FAILURE_HISTORY_NOT_PRE_FAILURE_SECURITY_PROOF','PSKY':'PROVIDER_START_DATE_CONFLICTS_WITH_2025_MERGER_HISTORY'}
NEW_ALIASES=[
 ('NLOK','GEN','2022-11-08','https://investor.gendigital.com/news/news-details/2022/Introducing-Gen-The-Company-to-Power-Digital-Freedom/default.aspx/1000/'),
 ('GPS','GAP','2024-08-22','https://investors.gapinc.com/press-releases/news-details/2024/Gap-Inc.-To-Change-Ticker-Symbol-to-GAP-on-August-22-To-Report-Second-Quarter-Fiscal-2024-Results-on-August-29/default.aspx'),
 ('FBHS','FBIN','2022-12-15','https://www.fbin.com/stories/fortune-brands-completes-separation-of-masterbrand/'),
 ('LB','BBWI','2021-08-03','https://www.victoriassecretandco.com/our-company/company-faqs'),
]


def identity_gate(symbol,meta):
    if symbol in REJECT:return False,REJECT[symbol]
    if symbol not in NAMES:return False,'NO_VALIDATED_SECURITY_IDENTITY'
    if meta.get('symbol')!=symbol or meta.get('currency')!='USD' or meta.get('instrumentType')!='EQUITY':
        return False,'SYMBOL_CURRENCY_OR_TYPE_MISMATCH'
    if NAMES[symbol] not in meta.get('longName','').lower():return False,'ISSUER_NAME_MISMATCH'
    return True,'EXACT_SYMBOL_AND_ISSUER_CONTRACT'


def main():
    if DATA.exists():raise ValueError('Refusing to overwrite version')
    inputs=DATA/'inputs';inputs.mkdir(parents=True)
    for p in (BASE/'inputs').iterdir():
        if p.is_file():shutil.copyfile(p,inputs/p.name)
    shutil.copyfile(BASE/'inputs/prices.csv.gz',inputs/'before_free_recovery.csv.gz')
    prices=read_csv(inputs/'prices.csv.gz');fields=list(prices[0])
    by=defaultdict(dict)
    for r in prices:by[r['symbol']][r['trade_date']]=r
    days={r['trade_date'] for r in read_csv(inputs/'calendar.csv.gz')}
    start,end=min(r['trade_date'] for r in prices),max(r['trade_date'] for r in prices)
    attempts=json.loads((OUT/'acquisition.json').read_text())
    decisions=[];lineage=[];events=[];conflicts=[]
    for record in attempts['sources']:
        if record['provider']!='yahoo':continue
        symbol=record['symbol'];status='NO_PROVIDER_RESPONSE';added=0;overlap=0;bad=0
        meta={}
        if record.get('sha256'):
            path=OUT/record['path'];assert digest(path)==record['sha256']
            root=json.loads(path.read_bytes())['chart'];chart=root['result'][0];meta=chart['meta']
            ok,status=identity_gate(symbol,meta)
            if ok:
                try:
                    incoming=validate_chart(symbol,chart)
                    for d,old in by[symbol].items():
                        if d in incoming and not price_flags(old) and not price_flags(incoming[d]):
                            overlap+=1
                            if not compare_bar(old,incoming[d]):
                                bad+=1
                                conflicts.append(dict(symbol=symbol,trade_date=d,old=json.dumps(old),new=json.dumps(incoming[d])))
                    if bad:raise ValueError('PRICE_BASIS_CONFLICT_NO_MERGE')
                    if by[symbol] and overlap<20:raise ValueError('INSUFFICIENT_OVERLAP_NO_MERGE')
                    for d,b in incoming.items():
                        if start<=d<=end and d in days and d not in by[symbol] and not price_flags(b):
                            item=dict(symbol=symbol,trade_date=d,**b,source='yahoo-free-followup',updated_at='')
                            by[symbol][d]=item;prices.append(item);added+=1
                            lineage.append(dict(symbol=symbol,trade_date=d,provider_symbol=symbol,
                                                source_sha256=record['sha256'],method='EXACT_SECURITY_MISSING_ONLY'))
                    status='ACCEPTED_MISSING_ONLY' if added else 'VALIDATED_NO_MISSING_ROWS'
                    from datetime import datetime
                    from zoneinfo import ZoneInfo
                    for kind,items in chart.get('events',{}).items():
                        for event in items.values():
                            events.append(dict(symbol=symbol,event_type=kind,
                                               effective_date=datetime.fromtimestamp(event['date'],ZoneInfo('America/New_York')).date().isoformat(),
                                               payload=json.dumps(event),source_sha256=record['sha256'],
                                               status='PROVIDER_EVENT_EVIDENCE_NOT_AUTOMATIC_REBASING'))
                except (ValueError,KeyError,TypeError) as exc:status=str(exc)
        decisions.append(dict(symbol=symbol,provider_name=meta.get('longName',''),http_status=record.get('status'),
                              status=status,overlap_rows=overlap,conflicting_rows=bad,added_rows=added,
                              source_sha256=record.get('sha256',''),url=record['url']))
    aliases=read_csv(inputs/'aliases.csv')
    incoming_hashes={(r['symbol'],r['trade_date']):r['source_sha256'] for r in lineage}
    for old,new,effective,url in NEW_ALIASES:
        assert not by[old],old
        if not by[new]:raise ValueError('No approved provider history: '+new)
        for d,r in by[new].items():
            item=dict(r,symbol=old,source='verified-ticker-alias:'+new)
            prices.append(item);by[old][d]=item
            lineage.append(dict(symbol=old,trade_date=d,provider_symbol=new,
                                source_sha256=incoming_hashes.get((new,d),digest(BASE/'inputs/prices.csv.gz')),method='OFFICIAL_CONTINUITY_ALIAS'))
        aliases.append(dict(symbol=old,provider_symbol=new,effective_date=effective,source_url=url,status='VERIFIED_CONTINUITY_WITH_CORPORATE_ACTION_LIMITS',
                            rows=len(by[old]),raw_sha256='',limitation='Split/spin distributions not total-return reconciled.'))
    # Identity boundaries preserve raw bars but prevent predecessor history from warming a new security.
    overrides=[dict(symbol='PSKY',start_date=start,end_date='2025-08-06',flag='PREDECESSOR_IDENTITY_UNRESOLVED',
                    source_url='https://www.sec.gov/Archives/edgar/data/2041610/000119312525175046/d841914d8k12b.htm'),
               dict(symbol='AVB',start_date='2026-08-17',end_date=end,flag='TERMINAL_CORPORATE_ACTION_UNRESOLVED',
                    source_url='https://investors.avalonbay.com/sec-filings/all-sec-filings/content/0001104659-26-097833/tm2623381d1_8k.htm'),
               dict(symbol='EQR',start_date='2026-08-18',end_date=end,flag='TERMINAL_CORPORATE_ACTION_UNRESOLVED',
                    source_url='https://investors.avalonbay.com/sec-filings/all-sec-filings/content/0001104659-26-097833/tm2623381d1_8k.htm')]
    write_csv(inputs/'prices.csv.gz',sorted(prices,key=lambda r:(r['symbol'],r['trade_date'])),fields)
    write_csv(inputs/'aliases.csv',aliases,list(aliases[0]))
    write_csv(inputs/'quality_overrides.csv',overrides,list(overrides[0]))
    shutil.copyfile(OUT/'acquisition.json',inputs/'free_acquisition.json')
    write_csv(OUT/'integration_decisions.csv',decisions,list(decisions[0]))
    write_csv(OUT/'added_price_lineage.csv.gz',lineage,list(lineage[0]))
    write_csv(OUT/'provider_corporate_events.csv',events,['symbol','event_type','effective_date','payload','source_sha256','status'])
    write_csv(OUT/'price_basis_conflicts.csv',conflicts,['symbol','trade_date','old','new'])
    del prices,by,lineage
    subprocess.run([sys.executable,str(ROOT/'scripts/build_sp500_daily_dataset.py'),'--offline','--out',str(DATA)],check=True)
    missing=read_csv(DATA/'missing_symbols.csv');decision_map={r['symbol']:r for r in decisions}
    unresolved=[dict(r,provider_status=decision_map.get(r['symbol'],{}).get('status','NOT_REQUESTED'),
                     action_required='Obtain dated security identity and missing daily prices; never use successor/terminal cash as daily bars.') for r in missing]
    write_csv(OUT/'unresolved_data.csv',unresolved,list(unresolved[0]))
    before=json.loads((BASE/'manifest.json').read_text());after=json.loads((DATA/'manifest.json').read_text())
    summary=dict(before_missing=before['issues']['MISSING_BAR'],after_missing=after['issues']['MISSING_BAR'],
                 recovered_member_rows=before['issues']['MISSING_BAR']-after['issues']['MISSING_BAR'],
                 remaining_symbols=len(missing),request_count=len(attempts['sources']),
                 downloaded_responses=sum(bool(r.get('sha256')) for r in attempts['sources']),
                 yahoo_downloads=sum(r['provider']=='yahoo' and bool(r.get('sha256')) for r in attempts['sources']),
                 added_aliases=len(NEW_ALIASES),overrides=overrides,issues=after['issues'])
    (OUT/'recovery_summary.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
    print(json.dumps(summary,indent=2))


if __name__=='__main__':main()
