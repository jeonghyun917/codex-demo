import json
import acquire_sp500_free_followup as client
from acquire_sp500_extension import OUT


def main():
    client.OUT=OUT
    target=OUT/'supplement_acquisition.json'
    if target.exists():raise ValueError('Refusing overwrite')
    sources=[('fred-sp500-csv','https://fred.stlouisfed.org/graph/fredgraph.csv?id=SP500&cosd=2026-05-01&coed=2026-10-26','csv'),
             ('skydance-completion','https://ir.paramount.com/news-releases/news-release-details/paramount-completes-acquisition-warner-bros-discovery-creating','html'),
             ('skydance-sec','https://www.sec.gov/Archives/edgar/data/2041610/000110465926113076/tm2626659d3_8k.htm','html')]
    records=[]
    for name,url,extension in sources:
        r=client.fetch(url,OUT/'raw'/f'{name}.{extension}');r['name']=name;records.append(r)
        print(name,r.get('status'),r.get('bytes'),r.get('error',''),flush=True)
    target.write_text(json.dumps(records,indent=2),encoding='utf-8')


if __name__=='__main__':main()
