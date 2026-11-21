"""Verify original-model capture, back up and publish its existing read projection."""
import hashlib
import json
from pathlib import Path
import pandas as pd
from acquire_sp500_top20_latest import OUT, ANCHOR
from build_sp500_daily_dataset import read_csv, digest
import sp500_paper_ledger as ledger
import publish_sp500_paper_view as publisher
from quant_daily_forward import Database


def main():
    p = ledger.protocol(ledger.DEFAULT)
    records = ledger.decisions(ledger.DEFAULT, p)
    latest = records[-1]
    if latest['anchor'] != ANCHOR:
        raise ValueError('LATEST_CAPTURE_NOT_READY')
    ledger.capture_window(p['calendar'], ledger.now_utc(), ANCHOR)
    data = OUT / 'dataset'
    ledger.checked_data(data)
    if latest['inputHashes'] != ledger.dataset_hashes(data):
        raise ValueError('CAPTURE_INPUT_CHANGED')
    _, rankings, audit = ledger.select(pd.DataFrame(latest['predictions']).assign(date=ANCHOR))
    if rankings != latest['ranks'] or audit != latest['filterAudit']:
        raise ValueError('FILTER_REPLAY_MISMATCH')
    payload = publisher.build(ledger.DEFAULT)
    day = payload['days'][0]
    if day['date'] != ANCHOR or day['candidateCount'] != len(rankings):
        raise ValueError('PROJECTION_MISMATCH')
    db = Database()
    sql = "SELECT HEX(payload_json) FROM stock_dashboard_view_snapshot WHERE index_code='SP500' AND view_version='SP500_PAPER_WEB_V1'"
    prior = db.query(sql)
    before = bytes.fromhex(prior[0][0]) if prior else b'null'
    with (OUT / 'paper-web-before.json').open('xb') as stream:
        stream.write(before)
    frozen = {str(path.relative_to(ledger.DEFAULT)): digest(path)
              for path in ledger.DEFAULT.rglob('*.json')}
    publisher.publish(payload)
    stored = bytes.fromhex(db.query(sql)[0][0])
    if json.loads(stored) != payload:
        raise ValueError('DB_READBACK_MISMATCH')
    if frozen != {str(path.relative_to(ledger.DEFAULT)): digest(path)
                  for path in ledger.DEFAULT.rglob('*.json')}:
        raise ValueError('LEDGER_CHANGED_DURING_PUBLICATION')
    with (OUT / 'paper-web-after.json').open('xb') as stream:
        stream.write(stored)
    members = {r['symbol'] for r in read_csv(data / 'daily_panel.csv.gz') if r['trade_date'] == ANCHOR}
    report = dict(status='PUBLISHED_VERIFIED', anchor=ANCHOR, nextOpenUtc=latest['nextOpenUtc'],
                  recordedAtUtc=latest['recordedAtUtc'], protocolSha256=p['contentSha256'],
                  decisionSha256=latest['contentSha256'], analyzed=len(latest['predictions']),
                  members=len(members), unavailableOrExcluded=sorted(members - {r['symbol'] for r in latest['predictions']}),
                  candidateCount=len(rankings), displayedCount=min(20, len(rankings)),
                  filterAudit=audit, top20=rankings[:20],
                  snapshotBeforeSha256=hashlib.sha256(before).hexdigest(),
                  snapshotAfterSha256=hashlib.sha256(stored).hexdigest(),
                  ledgerHashes=frozen, strategyChanged=False, retrospectiveDecisionsCreated=False,
                  limitations=['Historical source/adjustment limits remain; research only',
                               'Latest benchmark uses Yahoo with prior FRED overlap; fresh FRED download timed out'])
    with (OUT / 'publication-verification.json').open('x', encoding='utf-8') as stream:
        json.dump(report, stream, indent=2)
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
