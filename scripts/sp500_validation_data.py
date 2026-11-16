"""Bounded official-notice aliases, gap triage and period qualification; file-only."""
import json
import shutil
import subprocess
import sys
from collections import defaultdict
from pathlib import Path
from build_sp500_daily_dataset import ROOT, read_csv, write_csv, digest, price_flags
from recover_sp500_daily_dataset import validate_chart, compare_bar

BASE = ROOT/'output/sp500-daily-dataset-20261027'
OUT = ROOT/'output/sp500-validation-20261027'
DATA = OUT/'dataset'
# Verified primary-source change notices. These identify continuity, not historical availability.
ALIASES = [
 ('ABC','COR','2023-08-30','https://www.miaxglobal.com/alert/2023/08/29/miax-exchange-group-options-markets-corporate-action-alert-1'),
 ('ANTM','ELV','2022-06-28','https://www.elevancehealth.com/newsroom/anthem-inc-shareholders-approve-corporate-rebranding-new-name'),
 ('FB','META','2022-06-09','https://investor.atmeta.com/investor-news/press-release-details/2022/Meta-Platforms-Inc.-to-Change-Ticker-Symbol-to-META-on-June-9/default.aspx'),
 ('BLL','BALL','2022-05-10','https://investors.ball.com/news-presentations/press-releases/detail/82/ball-board-declares-quarterly-dividend-stock-ticker-symbol-changing-to-ball'),
 ('FLT','CPAY','2024-03-25','https://investor.corpay.com/news-releases/news-release-details/fleetcor-announces-rebranding-corpay-0'),
 ('PKI','RVTY','2023-05-16','https://news.revvity.com/press-announcements/press-releases/press-release-details/2023/Revvity-Announces-Financial-Results-for-the-First-Quarter-of-2023/default.aspx'),
 ('WLTW','WTW','2022-01-10','https://investors.wtwco.com/news-releases/news-release-details/willis-towers-watson-announces-nasdaq-ticker-symbol-change-wltw'),
 ('RE','EG','2023-07-10','https://investors.everestglobal.com/news/news-details/2023/Everest-Re-Group-to-Hold-Second-Quarter-2023-Earnings-Conference-Call-on-Thursday-July-27-2023/default.aspx'),
 ('MMC','MRSH','2026-01-14','https://www.marsh.com/en/corp/about/news/marsh-mclennan-to-change-nyse-symbol-to-mrsh.html'),
 ('FI','FISV','2025-11-11','https://www.nasdaqtrader.com/TraderNews.aspx?id=DTN2025-32'),
 ('BK','BNY','2026-05-21','https://www.bny.com/corporate/global/en/about-us/newsroom/press-release/bny-announces-planned-change-of-stock-ticker-symbol-to-bny-130465.html'),
]


def main():
    if DATA.exists():
        raise RuntimeError('Version exists; do not overwrite evidence.')
    inputs = DATA/'inputs'
    inputs.mkdir(parents=True)
    for p in (BASE/'inputs').iterdir():
        if p.is_file():
            shutil.copyfile(p, inputs/p.name)
    shutil.copyfile(BASE/'inputs/prices.csv.gz', inputs/'pre_alias_prices.csv.gz')
    prices = read_csv(inputs/'prices.csv.gz')
    fields = list(prices[0])
    start,end = min(r['trade_date'] for r in prices),max(r['trade_date'] for r in prices)
    by_symbol = defaultdict(dict)
    for r in prices:
        by_symbol[r['symbol']][r['trade_date']] = r
    lineage, decisions = [], []
    for old,new,effective,url in ALIASES:
        records = by_symbol[new]
        status = 'CANONICAL_PROVIDER_HISTORY'
        raw_hash = ''
        if not records and new == 'BNY':
            folder = ROOT/'tmp/quant-complete-window-new-prices-20261018'
            manifest = json.loads((folder/'manifest.json').read_text())
            for entry in manifest['sources']:
                p = ROOT/entry['path']
                assert digest(p) == entry['sha256']
            raw = folder/'yahoo-chart-BNY.raw'
            chart = json.loads(raw.read_bytes())['chart']['result'][0]
            records = {d:dict(symbol=new,trade_date=d,**b,source='yahoo-chart-local-alias',updated_at='')
                       for d,b in validate_chart(new,chart).items() if start<=d<=end and not price_flags(b)}
            raw_hash = digest(raw)
            shutil.copyfile(raw,inputs/'BNY.raw')
            status = 'VERIFIED_LOCAL_RAW_HISTORY'
        if not records or by_symbol[old]:
            raise ValueError(f'Unresolved or conflicting alias: {old} {new}')
        added = 0
        for day,r in records.items():
            if start<=day<=end:
                item = dict(r,symbol=old,source='verified-ticker-alias:'+new)
                prices.append(item)
                lineage.append(dict(symbol=old,provider_symbol=new,trade_date=day,source_url=url,
                                    **{k:r[k] for k in ('open_price','high_price','low_price','close_price','volume')}))
                added += 1
        decisions.append(dict(symbol=old,provider_symbol=new,effective_date=effective,
                              source_url=url,status=status,rows=added,raw_sha256=raw_hash,
                              limitation='Retrospective identity evidence; same vendor basis, not total return/PIT.'))
    write_csv(inputs/'prices.csv.gz', sorted(prices,key=lambda r:(r['symbol'],r['trade_date'])),fields)
    write_csv(inputs/'aliases.csv',decisions,list(decisions[0]))
    write_csv(OUT/'alias_lineage.csv.gz',lineage,list(lineage[0]))
    del prices,by_symbol,lineage
    subprocess.run([sys.executable,str(ROOT/'scripts/build_sp500_daily_dataset.py'),'--offline','--out',str(DATA)],check=True)
    missing = read_csv(DATA/'missing_symbols.csv')
    # Categories distinguish confirmed evidence from hypotheses; mergers never become aliases.
    complex_names = set('COG DISCA DISCK FBHS LB PEAK PARA VIAC WRK CDAY DAY NLOK GPS FERG'.split())
    failed_names = {'FRC','SBNY','SIVB'}
    triage=[]
    for r in missing:
        s=r['symbol']
        if r['first_available']:
            category='PARTIAL_HISTORY_OR_SESSION_GAP'
        elif s in complex_names:
            category='IDENTITY_OR_CORPORATE_ACTION_REVIEW_NO_AUTOMATIC_ALIAS'
        elif s in failed_names:
            category='FAILED_BANK_HISTORY_AND_TERMINAL_RECOVERY_REVIEW'
        else:
            category='ABSENT_HISTORY_REASON_UNCONFIRMED'
        triage.append(dict(r,category=category,priority='P0',
                           needed='Dated identity, missing OHLCV, corporate-action/terminal settlement evidence.'))
    write_csv(OUT/'remaining_data_queue.csv',triage,list(triage[0]))
    coverage=read_csv(DATA/'daily_coverage.csv')
    runs=[]
    for r in coverage:
        if r['members']==r['usable']:
            if not runs or runs[-1]['end_index'] != coverage.index(r)-1:
                runs.append(dict(start=r['trade_date'],end=r['trade_date'],sessions=0,end_index=coverage.index(r)))
            runs[-1].update(end=r['trade_date'],sessions=runs[-1]['sessions']+1,end_index=coverage.index(r))
    for r in runs:
        del r['end_index']
    write_csv(OUT/'complete_price_runs.csv',runs,['start','end','sessions'])
    years=[]
    for year in sorted({r['trade_date'][:4] for r in coverage}):
        subset=[r for r in coverage if r['trade_date'].startswith(year)]
        denominator=sum(int(r['members']) for r in subset)
        years.append(dict(year=year,sessions=len(subset),member_rows=denominator,
                          usable_pct=100*sum(int(r['usable']) for r in subset)/denominator,
                          complete_sessions=sum(r['members']==r['usable'] for r in subset)))
    write_csv(OUT/'coverage_by_year.csv',years,list(years[0]))
    result=dict(alias_count=len(decisions),remaining_missing=sum(int(r['missing_member_sessions']) for r in missing),
                remaining_symbols=len(missing),complete_runs=runs,
                formal_validation_status='NOT_QUALIFIED_OFFICIAL_PIT_ACTIONS_CALENDAR_AND_FORECAST_HISTORY_INCOMPLETE',
                diagnostic_period='2023-01-03 through 2026-08-18 decisions, including cash periods; historical evaluation only')
    (OUT/'data_qualification.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result,indent=2))


if __name__=='__main__':
    main()
