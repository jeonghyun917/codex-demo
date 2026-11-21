"""Create-only public evidence acquisition; no model, DB or ledger writes."""
import io
import argparse
import json
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import quote
import pandas as pd
import acquire_sp500_free_followup as client
from quant_daily_forward import Database, check_provider, collection_lock
from sp500_paper_ledger import ROOT, DEFAULT, protocol, capture_window

OUT = ROOT / 'output/sp500-top20-20261114'
ANCHOR = '2026-11-13'


def main():
    p = protocol(DEFAULT)
    capture_window(p['calendar'], datetime.now(timezone.utc), ANCHOR)
    client.OUT = OUT
    with collection_lock(ROOT / 'data/quant-daily/collection.lock'):
        db = Database()
        check_provider(db)
        OUT.mkdir(exist_ok=True)
        (OUT / 'raw').mkdir(exist_ok=True)
        manifest = OUT / 'acquisition.json'
        if manifest.exists():
            raise ValueError('Acquisition already completed; inspect existing evidence')
        partial = OUT / 'acquisition.partial.json'
        records = json.loads(partial.read_text()) if partial.exists() else []
        if any((r.get('status') in (403, 429) or r.get('blocked'))
               and r.get('name') != 'official-okta-membership' for r in records):
            raise ValueError('Prior access denial; no automatic retry')

        def fetch(name, url, extension, optional=False):
            prior = next((r for r in records if r['name'] == name), None)
            if prior:
                if not prior.get('sha256') and not optional:
                    raise ValueError('Prior failed source: ' + name)
                return
            path = OUT / 'raw' / (name + '.' + extension)
            if path.exists():
                raise ValueError('Unmanifested raw response: ' + name)
            record = client.fetch(url, path)
            record['name'] = name
            records.append(record)
            partial.write_text(json.dumps(records, indent=2), encoding='utf-8')
            print(name, record.get('status'), record.get('bytes'), record.get('error', ''), flush=True)
            if record.get('status') in (403, 429) or record.get('blocked'):
                raise ValueError('ACCESS_DENIED: ' + name)
            if not optional and (record.get('status') != 200 or not record.get('sha256')):
                raise ValueError('SOURCE_UNAVAILABLE: ' + name)

        fetch('roster', 'https://en.wikipedia.org/wiki/List_of_S%26P_500_companies', 'html')
        fetch('membership-history', 'https://en.wikipedia.org/wiki/Historical_components_of_the_S%26P_500', 'html')
        fetch('fred-index', 'https://fred.stlouisfed.org/graph/fredgraph.csv?id=SP500&cosd=2026-10-01&coed=' + ANCHOR, 'csv', optional=True)
        # The official host denied direct download. Its web-tool review is recorded separately;
        # do not retry that host or substitute the community announcement date as effective date.
        if not (OUT / 'membership-review.json').exists():
            raise ValueError('OFFICIAL_MEMBERSHIP_REVIEW_REQUIRED')
        roster = pd.read_html(io.StringIO((OUT / 'raw/roster.html').read_text(encoding='utf-8')))[0]
        symbols = sorted(set(roster.Symbol.str.replace('.', '-', regex=False)))
        if not 490 <= len(symbols) <= 510 or len(symbols) != len(roster):
            raise ValueError('INVALID_ROSTER')
        roster.to_csv(OUT / 'current_roster.csv', index=False)
        stamp = lambda day: int(datetime.fromisoformat(day).replace(tzinfo=timezone.utc).timestamp())
        for symbol in ['^GSPC'] + symbols:
            url = (f'https://query1.finance.yahoo.com/v8/finance/chart/{quote(symbol)}'
                   f'?period1={stamp("2026-10-01")}&period2={stamp("2026-11-14")}'
                   '&interval=1d&events=div%2Csplits&includeAdjustedClose=true')
            fetch('yahoo-' + ('INDEX' if symbol == '^GSPC' else symbol), url, 'json')
            time.sleep(.2)
        with manifest.open('x', encoding='utf-8') as stream:
            json.dump(records, stream, indent=2)


def acquire_warmup():
    client.OUT = OUT
    with collection_lock(ROOT / 'data/quant-daily/collection.lock'):
        check_provider(Database())
        manifest = OUT / 'supplement.json'
        path = OUT / 'raw/yahoo-OKTA-warmup.json'
        if manifest.exists() or path.exists():
            raise ValueError('Warmup evidence already exists')
        stamp = lambda day: int(datetime.fromisoformat(day).replace(tzinfo=timezone.utc).timestamp())
        url = (f'https://query1.finance.yahoo.com/v8/finance/chart/OKTA?period1={stamp("2026-06-01")}'
               f'&period2={stamp("2026-11-14")}&interval=1d&events=div%2Csplits&includeAdjustedClose=true')
        record = client.fetch(url, path)
        record['name'] = 'yahoo-OKTA-warmup'
        with manifest.open('x', encoding='utf-8') as stream:
            json.dump([record], stream, indent=2)
        print(json.dumps(record, indent=2))
        if record.get('status') != 200 or record.get('blocked'):
            raise ValueError('WARMUP_UNAVAILABLE')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--warmup', action='store_true')
    if parser.parse_args().warmup:
        acquire_warmup()
    else:
        main()
