"""Audit secondary candidates and rerun the frozen research protocol."""
import io
import json
from pathlib import Path
import pandas as pd
from build_sp500_daily_dataset import read_csv, write_csv, digest
import sp500_daily_validation as research
import verify_sp500_daily_research as verifier

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/sp500-free-followup-20261027'
BASE=ROOT/'output/sp500-validation-20261027/dataset'


def main():
    candidates=[]
    sources={r['symbol']:r for r in json.loads((OUT/'secondary_acquisition.json').read_text())}
    for path in sorted((OUT/'secondary').glob('*.html')):
        item=dict(symbol=path.stem,sha256=digest(path),path=str(path),rows=0,
                  suspect_volume_rows=0,status='STAGED_NOT_INTEGRATED_PRICE_BASIS_AND_IDENTITY_UNVERIFIED')
        source=sources[path.stem]
        assert item['sha256']==source['sha256']
        item['requested_url']=source['url'];item['final_url']=source.get('final_url','')
        if item['final_url'].rstrip('/')!=item['requested_url'].rstrip('/'):
            item['status']='REDIRECTED_SECURITY_REQUIRES_IDENTITY_VERIFICATION'
        try:
            tables=pd.read_html(io.StringIO(path.read_text(encoding='utf-8')))
            table=next(t for t in tables if 'Volume' in t.columns and 'Open' in t.columns)
            item['rows']=len(table)
            volumes=pd.to_numeric(table['Volume'].astype(str).str.replace(',',''),errors='coerce')
            item['suspect_volume_rows']=int((volumes.isna() | (volumes<=1)).sum())
        except (ValueError,StopIteration):
            item['status']='NO_READABLE_PRICE_TABLE'
        candidates.append(item)
    write_csv(OUT/'secondary_candidate_audit.csv',candidates,list(candidates[0]))
    # Resolve provenance through the provider row; aliases can include newly fetched rows.
    lineage=read_csv(OUT/'added_price_lineage.csv.gz')
    incoming={(r['symbol'],r['trade_date']):r['source_sha256'] for r in lineage
              if r['method']=='EXACT_SECURITY_MISSING_ONLY'}
    parent_hash=digest(BASE/'inputs/prices.csv.gz')
    for r in lineage:
        if r['method']=='OFFICIAL_CONTINUITY_ALIAS':
            r['source_sha256']=incoming.get((r['provider_symbol'],r['trade_date']),parent_hash)
    write_csv(OUT/'added_price_lineage.csv.gz',lineage,list(lineage[0]))
    research.DATA=OUT/'dataset';research.OUT=OUT
    research.main()
    verifier.DATA=research.DATA;verifier.OUT=OUT;verifier.BASE=BASE
    verifier.main()


if __name__=='__main__':
    main()
