"""Verify archived source hashes and non-use of unresolved identity prices."""
import json
from pathlib import Path
from build_sp500_daily_dataset import read_csv,write_csv,digest

OUT=Path(__file__).resolve().parents[1]/'output/sp500-free-followup-20261027'


def main():
    secondary=json.loads((OUT/'secondary_acquisition.json').read_text())
    primary=json.loads((OUT/'acquisition.json').read_text())['sources']
    verified=0
    for r in primary+secondary:
        if r.get('sha256'):
            assert digest(OUT/r['path'])==r['sha256'],r
            verified+=1
    sources={r['symbol']:r for r in secondary}
    audit=read_csv(OUT/'secondary_candidate_audit.csv')
    redirects=0
    for row in audit:
        source=sources[row['symbol']]
        row['requested_url']=source['url'];row['final_url']=source.get('final_url','')
        if row['final_url'].rstrip('/')!=row['requested_url'].rstrip('/'):
            row['status']='REDIRECTED_SECURITY_REQUIRES_IDENTITY_VERIFICATION';redirects+=1
    write_csv(OUT/'secondary_candidate_audit.csv',audit,list(audit[0]))
    flags=read_csv(OUT/'dataset/inputs/quality_overrides.csv')
    for event in read_csv(OUT/'research/events.csv'):
        assert not any(event['symbol']==f['symbol'] and f['start_date']<=event['date']<=f['end_date'] for f in flags),event
    result=dict(status='PASS',raw_hashes_verified=verified,secondary_redirects=redirects,prohibited_identity_executions=0)
    (OUT/'source_verification.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(result)


if __name__=='__main__':main()
