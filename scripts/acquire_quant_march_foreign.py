"""Six bounded public SEC responses; no database access or authentication."""
import hashlib
import json
import sys
import urllib.error
import urllib.request
from datetime import date, datetime, timezone
from pathlib import Path

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('tmp/quant-march-foreign-evidence-20261031')
ISSUERS = {'CCEP': 1650107, 'FER': 1468522, 'TRI': 1075124}
CUTOFF = '2026-03-13'


def now():
    return datetime.now(timezone.utc).isoformat()


def profile(payload):
    result = {}
    for namespace, facts in payload['facts'].items():
        for tag, fact in facts.items():
            if not any(x in tag.lower() for x in ('revenue', 'earnings', 'profitloss', 'assets', 'liabilities', 'equity', 'cashflows', 'sharesoutstanding')):
                continue
            for unit, rows in fact.get('units', {}).items():
                eligible = [r for r in rows if r.get('filed', '9999') < CUTOFF and r.get('end', '9999') < CUTOFF]
                if not eligible:
                    continue
                annual, quarters, instant = [], [], []
                for r in eligible:
                    if not r.get('start'):
                        instant.append(r)
                        continue
                    days = (date.fromisoformat(r['end']) - date.fromisoformat(r['start'])).days + 1
                    if r.get('form') in ('20-F', '20-F/A', '40-F', '40-F/A', '10-K', '10-K/A') and 330 <= days <= 380:
                        annual.append(r)
                    elif r.get('form') in ('6-K', '6-K/A', '10-Q', '10-Q/A') and 70 <= days <= 110:
                        quarters.append(r)
                def latest(items):
                    return sorted(items, key=lambda r: (r['end'], r['filed'], r['accn']), reverse=True)[:4]
                result[f'{namespace}:{tag}|{unit}'] = dict(eligibleObservations=len(eligible), annualCount=len(annual), standaloneQuarterCount=len(quarters), latestAnnual=latest(annual), latestStandaloneQuarter=latest(quarters), latestInstant=latest(instant))
    return result


def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    if (ROOT / 'manifest.json').exists():
        raise ValueError('Refuse completed evidence overwrite')
    sources, profiles = [], {}
    denied = False
    for symbol, cik in ISSUERS.items():
        for kind, prefix in [('facts', 'api/xbrl/companyfacts/'), ('submissions', 'submissions/')]:
            if denied:
                continue
            url = f'https://data.sec.gov/{prefix}CIK{cik:010d}.json'
            headers = {'User-Agent': 'QuantLocalResearch/1.0', 'Accept': 'application/json', 'Accept-Encoding': 'identity'}
            item = dict(symbol=symbol, cik=cik, kind=kind, url=url, requestedAt=now(), requestHeaders=headers)
            try:
                with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=25) as response:
                    item.update(status=response.status, finalUrl=response.url, responseHeaders=dict(response.headers.items()))
                    if response.status != 200:
                        raise ValueError('Require HTTP200')
                    body = response.read(12_000_001)
                    if len(body) > 12_000_000:
                        raise ValueError('Size budget exceeded')
                payload = json.loads(body)
                if int(payload['cik']) != cik:
                    raise ValueError('Wrong CIK')
                if kind == 'facts' and not isinstance(payload['facts'], dict):
                    raise ValueError('Invalid companyfacts schema')
                if kind == 'submissions' and not isinstance(payload['filings']['recent']['accessionNumber'], list):
                    raise ValueError('Invalid submissions schema')
                path = ROOT / f'{symbol}-{kind}.json'
                with path.open('xb') as stream:
                    stream.write(body)
                item.update(path=str(path), bytes=len(body), sha256=hashlib.sha256(body).hexdigest(), validatedCik=cik)
                if kind == 'facts':
                    profiles[symbol] = profile(payload)
            except urllib.error.HTTPError as exc:
                item.update(status=exc.code, error=str(exc), responseHeaders=dict(exc.headers.items()))
                denied = exc.code == 403
            except (OSError, ValueError, KeyError) as exc:
                item['error'] = str(exc)
            item['retrievedAt'] = now()
            sources.append(item)
    for filename, payload in [('manifest.json', dict(classification='FILING_DATE_BOUNDED_RESEARCH_NOT_PIT', cutoffExclusive=CUTOFF, sources=sources, stoppedFor403=denied, databaseWrites=0)), ('coverage.json', profiles)]:
        with (ROOT / filename).open('x', encoding='utf-8') as stream:
            json.dump(payload, stream, indent=2)
    print(json.dumps([dict(symbol=x['symbol'], kind=x['kind'], status=x.get('status'), bytes=x.get('bytes'), error=x.get('error')) for x in sources]))


if __name__ == '__main__':
    main()
