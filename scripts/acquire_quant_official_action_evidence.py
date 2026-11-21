"""Acquire a small, explicit official-source evidence sample, not full coverage."""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import urllib.request

REPO = Path(__file__).resolve().parents[1]
ROOT = REPO / 'tmp/pit-acquisition/actions'
SPECS = [
    ('aph-history', 'Amphenol', None, 'https://investors.amphenol.com/stock-information/dividend-and-stock-split-history/default.aspx'),
    ('aph-2021-announcement', 'Amphenol', '2021-01-27', 'https://investor.amphenol.com/news-and-events/news-details/2021/Amphenol-Reports-Record-Fourth-Quarter-2020-Results-and-Announces-2-for-1-Stock-Split/default.aspx'),
    ('aph-2024-announcement', 'Amphenol', '2024-05-20', 'https://investor.amphenol.com/news-and-events/news-details/2024/Amphenol-Corporation-Announces-Two-for-One-Stock-Split/default.aspx'),
    ('aph-2026-announcement', 'Amphenol', '2026-08-06', 'https://investors.amphenol.com/news-and-events/news-details/2026/Amphenol-Announces-Two-for-One-Stock-Split-and-Third-Quarter-2026-Dividend/default.aspx'),
    ('ge-vernova-announcement', 'GE', '2024-02-29', 'https://www.ge.com/news/press-releases/ge-board-of-directors-approves-spin-off-of-ge-vernova-ge-vernova-and-ge-aerospace-to'),
    ('ge-vernova-completion', 'GE Vernova', '2024-04-02', 'https://www.gevernova.com/news/articles/ge-vernova-completes-spin'),
    ('atvi-completion', 'Microsoft', '2023-10-13', 'https://blogs.microsoft.com/blog/2023/10/13/welcoming-the-legendary-teams-at-activision-blizzard-king-to-team-xbox/'),
    ('atvi-sec-merger-terms', 'SEC / Microsoft filing', '2023-10-13', 'https://www.sec.gov/Archives/edgar/data/789019/000119312523255762/d537928d8k.htm'),
    ('vmw-completion', 'Broadcom', '2023-11-22', 'https://investors.broadcom.com/news-releases/news-release-details/broadcom-completes-acquisition-vmware'),
    ('vmw-sec-merger-terms', 'SEC / Broadcom filing', '2023-11-22', 'https://www.sec.gov/Archives/edgar/data/1730168/000114036123054354/ef20015100_form8k.htm'),
]


def main():
    folder = ROOT / 'official'
    folder.mkdir(parents=True, exist_ok=True)
    sources, failures = [], []
    for sid, publisher, published, url in SPECS:
        target = folder / (sid + '.html')
        if target.exists():
            raise ValueError('Refusing to overwrite existing source snapshot: ' + str(target))
        retrieved = datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z')
        try:
            request = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 quant-research (public issuer verification)'})
            with urllib.request.urlopen(request, timeout=20) as response:
                raw = response.read()
            target.write_bytes(raw)
            sources.append(dict(id=sid, publisher=publisher, publishedDate=published, url=url,
                                retrievedAt=retrieved, sha256=hashlib.sha256(raw).hexdigest(), path='official/' + target.name))
            print(sid, 'OK', len(raw))
        except OSError as exc:
            failures.append(dict(id=sid, url=url, attemptedAt=retrieved, error=str(exc)))
            print(sid, 'FAILED', str(exc))
    ids = {s['id'] for s in sources}
    events = []
    if 'aph-history' in ids:
        for year, day in [('2021', '2021-03-05'), ('2024', '2024-06-12'), ('2026', '2026-09-03')]:
            events.append(dict(sourceId='aph-history', symbol='APH', action='SPLIT', effectiveDate=day,
                               dateBasis='EX_DATE', numerator=2, denominator=1, amount=None, currency='USD', amountBasis=None,
                               ratioBasis='OFFICIAL_SHARE_RATIO',
                               announcementSourceId='aph-' + year + '-announcement',
                               priceBasis='CURRENT_PROVIDER_OHLC_ALREADY_SPLIT_ADJUSTED_DO_NOT_REAPPLY'))
    if 'aph-2026-announcement' in ids:
        events.append(dict(sourceId='aph-2026-announcement', symbol='APH', action='DIVIDEND', effectiveDate='2026-10-14',
                           dateBasis='PAYMENT_DATE', numerator=None, denominator=None, amount=0.125, currency='USD',
                           amountBasis='POST_SPLIT_PER_SHARE', ratioBasis=None, recordDate='2026-09-22'))
    bundle = dict(sources=sources, events=events, coverage='PARTIAL_OFFICIAL_EVENTS', failedSources=failures,
                  warning='Issuer event sample only, not all-symbol coverage, not PIT predictor proof. Announcement precision is calendar date only.')
    (ROOT / 'official-bundle.json').write_text(json.dumps(bundle, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
