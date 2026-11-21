"""Find existing offline raw observations for missing member dates; never blend price bases."""
import csv
import gzip
import hashlib
import json
from collections import defaultdict, Counter
from datetime import datetime, timezone
from pathlib import Path

from build_sp500_daily_dataset import read_csv, write_csv, price_flags

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/sp500-daily-dataset-20261018'


def main():
    missing = defaultdict(set)
    with gzip.open(OUT / 'daily_panel.csv.gz', 'rt', encoding='utf-8', newline='') as f:
        for r in csv.DictReader(f):
            if r['quality_flags'] == 'MISSING_BAR':
                missing[r['symbol']].add(r['trade_date'])
    inventory, candidates = [], []
    raw_root = ROOT / 'tmp/pit-acquisition/actions/raw'
    for symbol, dates in sorted(missing.items()):
        path = raw_root / f'{symbol}.json'
        available = set()
        status, checksum = 'NO_LOCAL_RAW_FILE', ''
        if path.exists():
            payload = path.read_bytes()
            checksum = hashlib.sha256(payload).hexdigest()
            data = json.loads(payload)
            result = data.get('chart', {}).get('result')
            status = 'LOCAL_RAW_WITHOUT_USABLE_MATCH'
            if result:
                chart = result[0]
                quotes = chart['indicators']['quote'][0]
                for i, timestamp in enumerate(chart.get('timestamp', [])):
                    day = datetime.fromtimestamp(timestamp, timezone.utc).date().isoformat()
                    if day not in dates:
                        continue
                    bar = {f'{k}_price':quotes[k][i] for k in ('open','high','low','close')}
                    bar['volume'] = quotes['volume'][i]
                    if not price_flags(bar):
                        available.add(day)
                        candidates.append(dict(symbol=symbol, trade_date=day, **bar,
                                               source_path=str(path), source_sha256=checksum,
                                               status='RAW_CANDIDATE_REQUIRES_PRICE_BASIS_RECONCILIATION'))
                if available:
                    status = 'LOCAL_RAW_RECOVERY_CANDIDATE'
        inventory.append(dict(symbol=symbol, missing_sessions=len(dates),
                              local_valid_candidates=len(available),
                              unresolved_sessions=len(dates-available), status=status,
                              source_path=str(path) if path.exists() else '', source_sha256=checksum))
    write_csv(OUT / 'local_recovery_inventory.csv', inventory, list(inventory[0]))
    write_csv(OUT / 'local_recovery_candidates.csv.gz', candidates,
              ['symbol','trade_date','open_price','high_price','low_price','close_price','volume','source_path','source_sha256','status'])
    summary = dict(missing_sessions=sum(map(len, missing.values())),
                   local_raw_candidate_sessions=len(candidates),
                   unresolved_sessions=sum(r['unresolved_sessions'] for r in inventory),
                   symbol_statuses=dict(Counter(r['status'] for r in inventory)),
                   policy='Candidates are not silently merged; reconcile adjustment basis and identity first.')
    (OUT / 'local_recovery_summary.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
    print(json.dumps(summary, indent=2))


if __name__ == '__main__':
    main()
