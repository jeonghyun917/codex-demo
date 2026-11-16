"""Bounded free corporate-action evidence; no access-control workarounds."""
import json
import acquire_sp500_free_followup as client
from acquire_sp500_extension import chart_url
from pathlib import Path

OUT=Path(__file__).resolve().parents[1]/'output/sp500-actions-20261028'
SOURCES=[
 ('skyd-warrants','https://www.sec.gov/Archives/edgar/data/2041610/000110465926115294/tm2626659d12_ex99-2.htm'),
 ('skyd-exdate','https://cms-corp.paramount.com/news/press/skydance-provides-details-on-separate-trading-of-skyd-class-b-shares-and-warrants'),
 ('vmrk-merger','https://investors.avalonbay.com/sec-filings/all-sec-filings/content/0001104659-26-097833/tm2623381d1_8k.htm'),
 ('vylr-distribution','https://www.sec.gov/Archives/edgar/data/2128626/000119312526391356/ck0002128626-ex99_1.htm'),
 ('vylr-completion','https://www.sec.gov/Archives/edgar/data/2128626/000119312526417083/d332956d8k.htm')]


def main():
    client.OUT=OUT;(OUT/'raw').mkdir(parents=True,exist_ok=True)
    if (OUT/'acquisition.json').exists():raise ValueError('Refusing overwrite')
    partial=OUT/'acquisition.partial.json'
    records=json.loads(partial.read_text()) if partial.exists() else []
    done={r['name'] for r in records}
    for name,url in SOURCES+[(f'history-{s}',f'https://stockanalysis.com/stocks/{s.lower()}/history/') for s in ('SKYD','VMRK','VYLR')]+[(f'yahoo-{s}',chart_url(s)) for s in ('SKYD','VMRK','VYLR')]:
        ext='json' if name.startswith('yahoo') else 'html'
        if name in done:continue
        r=client.fetch(url,OUT/'raw'/f'{name}.{ext}');r['name']=name;records.append(r)
        partial.write_text(json.dumps(records,indent=2),encoding='utf-8')
        print(name,r.get('status'),r.get('bytes'),flush=True)
    (OUT/'acquisition.json').write_text(json.dumps(records,indent=2),encoding='utf-8')


if __name__=='__main__':main()
