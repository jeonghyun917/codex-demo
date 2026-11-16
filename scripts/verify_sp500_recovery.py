"""Independent original-preservation and raw-quote lineage checks for recovered version."""
import json
import math
from collections import Counter
from datetime import datetime
from zoneinfo import ZoneInfo

from build_sp500_daily_dataset import ROOT, digest
from verify_sp500_daily_dataset import rows

OUT = ROOT / 'output/sp500-daily-dataset-20261027'
BASE = ROOT / 'output/sp500-daily-dataset-20261018'
KEYS = ('open_price','high_price','low_price','close_price','volume')


def main():
    provenance = json.loads((OUT/'inputs/recovery_provenance.json').read_text())
    assert digest(BASE/'manifest.json') == provenance['base_manifest_sha256']
    assert digest(BASE/'inputs/prices.csv.gz') == digest(OUT/'inputs/original_prices.csv.gz')
    merged = {(r['symbol'],r['trade_date']):r for r in rows(OUT/'inputs/prices.csv.gz')}
    original_keys = set()
    for r in rows(OUT/'inputs/original_prices.csv.gz'):
        k = r['symbol'],r['trade_date']
        assert merged[k] == r, k
        original_keys.add(k)
    raw = {}
    for name, sha in provenance['raw_sha256'].items():
        path = OUT/'raw_recovery'/name
        assert digest(path) == sha
        c = json.loads(path.read_bytes())['chart']['result'][0]
        assert c['meta']['symbol']+'.json' == name
        assert c['meta']['currency'] == 'USD' and c['meta']['instrumentType'] == 'EQUITY'
        quote = c['indicators']['quote'][0]
        for i,t in enumerate(c['timestamp']):
            day = datetime.fromtimestamp(t,ZoneInfo('America/New_York')).date().isoformat()
            raw[c['meta']['symbol'],day] = tuple(quote[field][i] for field in ('open','high','low','close','volume'))
    added, member_count = set(), 0
    for r in rows(OUT/'recovery_lineage.csv.gz'):
        k = r['symbol'],r['trade_date']
        assert k not in original_keys and k not in added
        assert r['raw_sha256'] == provenance['raw_sha256'][r['symbol']+'.json']
        for i,field in enumerate(KEYS):
            assert float(r[field]) == float(raw[k][i]) == float(merged[k][field]), (k,field)
        assert merged[k]['source'] == 'yahoo-chart-local-recovery'
        added.add(k)
        member_count += int(r['fills_member_gap'])
    assert set(merged) == original_keys | added
    old_keys = {(r['symbol'],r['trade_date']):r['quality_flags']=='MISSING_BAR' for r in rows(BASE/'daily_panel.csv.gz')}
    new_keys, actual_recovered = set(), 0
    for r in rows(OUT/'daily_panel.csv.gz'):
        k = r['symbol'],r['trade_date']
        new_keys.add(k)
        if old_keys[k] and r['quality_flags'] != 'MISSING_BAR':
            assert k in added
            actual_recovered += 1
    assert new_keys == set(old_keys)
    assert actual_recovered == member_count
    summary = json.loads((OUT/'recovery_summary.json').read_text())
    assert len(added) == summary['added_history_rows']
    assert actual_recovered == summary['recovered_member_rows']
    assert sum(int(r['missing_member_sessions']) for r in rows(OUT/'additional_prices_required.csv')) == summary['remaining_missing_rows']
    result = dict(status='PASS', unchanged_original_rows=len(original_keys), verified_added_rows=len(added),
                  recovered_member_rows=actual_recovered, member_denominator_unchanged=len(new_keys),
                  raw_hashes_verified=len(provenance['raw_sha256']),
                  note='Verifies lineage and transformation, not independent historical security identity or corporate actions.')
    (OUT/'recovery_verification.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result,indent=2))


if __name__ == '__main__':
    main()
