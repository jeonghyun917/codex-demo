"""Immutable daily research extension with documented membership reconstruction."""
import io
import json
import math
import shutil
import subprocess
import sys
from datetime import date,timedelta
from collections import defaultdict
import pandas as pd
from acquire_sp500_extension import OUT
from recover_sp500_daily_dataset import validate_chart
from build_sp500_daily_dataset import read_csv,write_csv,digest,price_flags,members_on

BASE=OUT.parent/'sp500-free-followup-20261027/dataset'
DATA=OUT/'dataset'
END='2026-10-26'
CHANGES=[('2026-09-21',['BE','P','ILMN'],['TAP','TTD','BLDR'],'sp-september'),
         ('2026-10-01',['VYLR'],[],'sp-october'),
         ('2026-10-06',['TWLO','SKYD'],['WBD','CTVA','PSKY'],'sp-october+skydance-sec')]
NEW_NAMES={'BE':'bloom','P':'everpure','ILMN':'illumina','TWLO':'twilio','HONA':'honeywell aerospace','FDXF':'fedex freight'}
QUARANTINE={'PSKY','SKYD','VMRK','VYLR'}


def same_ohlc(a,b):
    try:return all(math.isclose(float(a[k]),float(b[k]),rel_tol=1e-5,abs_tol=1e-6) for k in ('open_price','high_price','low_price','close_price'))
    except (TypeError,ValueError):return False


def apply_change(roster,adds,removes):
    if not set(removes)<=roster or set(adds)&roster:raise ValueError('Membership transition conflict')
    return (roster-set(removes))|set(adds)


def main():
    if DATA.exists():raise ValueError('Refusing overwrite')
    records=[]
    for filename in ('acquisition.json','price_acquisition.json','supplement_acquisition.json'):
        batch=json.loads((OUT/filename).read_text())
        for r in batch:
            if r.get('sha256'):assert digest(OUT/r['path'])==r['sha256']
        records.extend(batch)
    inputs=DATA/'inputs';inputs.mkdir(parents=True)
    for p in (BASE/'inputs').iterdir():
        if p.is_file():shutil.copyfile(p,inputs/p.name)
    # Explicit changes plus an independently acquired whole-roster endpoint.
    intervals=read_csv(inputs/'sp500_membership_intervals.csv')
    fields=list(intervals[0]);start_roster=set(members_on(intervals,'2026-08-18'))
    expected=set(pd.read_csv(OUT/'current_roster.csv').Symbol.str.replace('.','-',regex=False))
    roster=start_roster.copy()
    for day,adds,removes,source in CHANGES:roster=apply_change(roster,adds,removes)
    assert roster==expected,dict(extra=sorted(roster-expected),missing=sorted(expected-roster))
    wiki=pd.read_html(io.StringIO((OUT/'raw/wiki-history.html').read_text(encoding='utf-8')))[0]
    observed=[]
    for _,r in wiki.iterrows():
        day=pd.to_datetime(r[('Effective Date','Effective Date')]).date().isoformat()
        if '2026-08-19'<=day<=END:
            observed.append((day,'' if pd.isna(r[('Added','Ticker')]) else r[('Added','Ticker')],'' if pd.isna(r[('Removed','Ticker')]) else r[('Removed','Ticker')]))
    assert set(observed)=={('2026-09-21','BE','TAP'),('2026-09-21','P','TTD'),('2026-09-21','ILMN','BLDR'),('2026-10-01','VYLR',''),('2026-10-06','TWLO','WBD'),('2026-10-06','','CTVA')}
    for name,tickers in [('sp-september',['BE','TAP','P','TTD','ILMN','BLDR']),('sp-october',['VYLR','CTVA','TWLO','WBD']),('skydance-sec',['SKYD','PSKY','October'])]:
        content=(OUT/'raw'/f'{name}.html').read_text(encoding='utf-8')
        assert all(s in content for s in tickers),name
    for r in intervals:
        if r['is_member'].lower()=='true' and r['end_date']=='2026-08-18':
            r['end_date']=END;r['source']='COMMUNITY_ENDPOINT_WITH_OFFICIAL_CHANGE_CHAIN'
            r['provenance_ids']+=';extension-20261027'
    for day,adds,removes,source in CHANGES:
        for s in removes:
            match=[r for r in intervals if r['symbol']==s and r['is_member'].lower()=='true' and r['start_date']<=day<=r['end_date']]
            assert len(match)==1
            match[0]['end_date']=(date.fromisoformat(day)-timedelta(days=1)).isoformat()
        for s in adds:
            intervals.append(dict(index_code='SP500',symbol=s,start_date=day,end_date=END,source='COMMUNITY_ENDPOINT_WITH_OFFICIAL_CHANGE_CHAIN',provenance_ids=source,is_member='true'))
    assert set(members_on(intervals,END))==expected
    write_csv(inputs/'sp500_membership_intervals.csv',intervals,fields)
    meta=json.loads((inputs/'manifest.json').read_text())
    meta['coverage']['lastDate']=END
    meta['coverage']['extensionPolicy']='RECONSTRUCTED_CHANGE_CHAIN_MATCHES_ACQUIRED_CURRENT_ROSTER_NOT_OFFICIAL_PIT'
    meta['extension_evidence']=dict(start='2026-08-19',end=END,changes=CHANGES,current_roster_sha256=digest(OUT/'current_roster.csv'),
         official_pit_verified=False,limitation='No certified official full roster or exhaustive official event archive; community chain consistency only.')
    (inputs/'manifest.json').write_text(json.dumps(meta,indent=2),encoding='utf-8')
    calendar=read_csv(inputs/'calendar.csv.gz');calfields=list(calendar[0]);byday={r['trade_date']:r for r in calendar}
    fred=pd.read_csv(OUT/'raw/fred-sp500-csv.csv').dropna()
    overlap=0
    for r in fred.itertuples():
        day=r.observation_date;level=float(r.SP500)
        if day in byday:
            assert math.isclose(float(byday[day]['benchmark_level']),level,abs_tol=.01),day
            overlap+=1
        elif day<=END:byday[day]=dict(trade_date=day,benchmark_level=level,source='FRED_SP500_PRICE_INDEX')
    assert overlap>=20
    write_csv(inputs/'calendar.csv.gz',[byday[d] for d in sorted(byday)],calfields)
    prices=read_csv(inputs/'prices.csv.gz');pricefields=list(prices[0]);by=defaultdict(dict)
    for r in prices:by[r['symbol']][r['trade_date']]=r
    decisions=[];lineage=[];conflicts=[]
    for record in records:
        s=record.get('symbol') or ('CAG' if record.get('name')=='yahoo-CAG' else None)
        if not s or s=='^GSPC':continue
        status='UNAVAILABLE';overlap=0;volume_revisions=0;price_mismatches=0;added=0;name=''
        if record.get('sha256'):
            try:
                chart=json.loads((OUT/record['path']).read_bytes())['chart']['result'][0]
                name=chart['meta'].get('longName','')
                if s in QUARANTINE:raise ValueError('CORPORATE_IDENTITY_OR_PREDECESSOR_UNRESOLVED')
                incoming=validate_chart(s,chart)
                if s=='CAG' and 'conagra' not in name.lower():raise ValueError('CAG_ISSUER_MISMATCH')
                for d,old in by[s].items():
                    if d not in incoming or price_flags(old) or price_flags(incoming[d]):continue
                    overlap+=1
                    if not same_ohlc(old,incoming[d]):
                        price_mismatches+=1
                        conflicts.append(dict(symbol=s,date=d,old=json.dumps(old),new=json.dumps(incoming[d])))
                    if int(old['volume'])!=int(incoming[d]['volume']):volume_revisions+=1
                if by[s] and (overlap<20 or price_mismatches):raise ValueError('INSUFFICIENT_OR_CONFLICTING_PRICE_OVERLAP')
                if not by[s] and (s not in NEW_NAMES or NEW_NAMES[s] not in name.lower()):raise ValueError('NEW_SECURITY_IDENTITY_NOT_APPROVED')
                empty=not by[s]
                for d,b in incoming.items():
                    if d>END or d not in byday or d in by[s] or price_flags(b):continue
                    if not empty and s!='CAG' and d<'2026-08-19':continue
                    row=dict(symbol=s,trade_date=d,**b,source='yahoo-extension-20261027',updated_at='')
                    by[s][d]=row;prices.append(row);added+=1
                    lineage.append(dict(symbol=s,trade_date=d,source_sha256=record['sha256'],source_path=record['path']))
                status='APPROVED_MISSING_ONLY_PRICE_BASIS' if not empty else 'APPROVED_NEW_ISSUER_SINGLE_SOURCE'
            except (KeyError,TypeError,ValueError) as exc:status=str(exc)
        decisions.append(dict(symbol=s,issuer=name,status=status,overlap=overlap,ohlc_mismatches=price_mismatches,volume_revisions=volume_revisions,added=added))
    write_csv(inputs/'prices.csv.gz',sorted(prices,key=lambda r:(r['symbol'],r['trade_date'])),pricefields)
    overrides=read_csv(inputs/'quality_overrides.csv')
    for r in overrides:
        if r['end_date']=='2026-10-16':r['end_date']=END
    source=next(r['url'] for r in records if r.get('name')=='sp-october')
    for s,start in [('WBD','2026-10-06'),('CTVA','2026-10-01'),('VYLR','2026-05-01'),('SKYD','2026-05-01')]:
        overrides.append(dict(symbol=s,start_date=start,end_date=END,flag='TERMINAL_CORPORATE_ACTION_UNRESOLVED',source_url=source))
    write_csv(inputs/'quality_overrides.csv',overrides,list(overrides[0]))
    write_csv(OUT/'integration_decisions.csv',decisions,list(decisions[0]))
    write_csv(OUT/'added_price_lineage.csv.gz',lineage,list(lineage[0]))
    write_csv(OUT/'price_conflicts.csv',conflicts,['symbol','date','old','new'])
    (OUT/'membership_reconstruction.json').write_text(json.dumps(dict(status='RECONSTRUCTED_RESEARCH_ONLY',start_count=len(start_roster),end_count=len(expected),endpoint_exact_match=True,
             effective_changes=CHANGES,official_pit_verified=False),indent=2),encoding='utf-8')
    del prices,by;subprocess.run([sys.executable,'scripts/build_sp500_daily_dataset.py','--offline','--out',str(DATA)],check=True)
    print('Added rows',len(lineage),'CAG',next(r for r in decisions if r['symbol']=='CAG'),flush=True)


if __name__=='__main__':main()
