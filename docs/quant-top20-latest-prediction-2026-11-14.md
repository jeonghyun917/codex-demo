# Original-model TOP20 publication, 2026-11-14

## Result

- Decision session: 2026-11-13.
- Frozen at: 2026-11-14T11:39:06.834160Z.
- Next entry session/open: 2026-11-16T14:30:00Z (23:30 Korea time).
- Current S&P500 members: 503. Original-model predictions: 495.
- Original probability shortlist: 99 (top 20%). Final eligible candidates: **0**.
- Index close / trailing 50-session mean minus one: -1.3797293659%.
- Volatility75 threshold: 3.1595013464% daily standard deviation.
- The unchanged index-above-MA50 condition rejects all candidates. No padding or relaxed filter.
- Published to existing SP500_PAPER_WEB_V1 DB snapshot, with backup and exact JSON readback verification.
- Quant desktop/mobile both display NO_CANDIDATES, HTTP 200, no page JavaScript errors.

## Data and model contract

All 503 current members received new data. 502 existing members passed unchanged OHLC overlap checks; OKTA received additional June-November history, with independently requested recent/longer windows agreeing. The latest 503 bars pass the existing basic price checks.

New dataset: `output/sp500-top20-20261114/dataset`. Parent: `output/sp500-paper-start-20261031/dataset`, retained unchanged. The independent verifier passed 688,596 membership rows and 1,368 sessions. Parent and new input hashes were checked before capture.

OKTA replaces KVUE effective before the Nov4 open, not the Oct30 announcement date used in the community history table. The effective date was verified from S&P's official PDF through the web tool. Direct PDF download returned 403 and was not retried. Structured review and the failed request are retained separately.

Official evidence: https://www.spglobal.com/spdji/en/documents/indexnews/announcements/20261030-1485650/1485650_kvue-ramp546.pdf

Latest FRED request timed out. New benchmark closes use Yahoo GSPC with more than 15 overlapping sessions checked against the prior verified FRED/Yahoo series. This is not fresh two-provider verification. Historical membership and adjusted-price/executability limitations remain; this is research paper data, not certified live execution data.

No model code, runtime version, probability formula, filter, fee, allocation or exit rule changed. Frozen protocol content hash:
`ed2e411eb55d867663781f7ec77c080ba2db6ce0108c732024cd4421ff9b00f9`.

Training cutoff 2026-11-02, calibration begins 2026-05-04. Latest fitting-label maturity 2026-04-30; latest calibration-label maturity 2026-10-28. No future outcomes enter the decision.

## Excluded or unavailable

- DOC, VMRK: existing fixed exclusions.
- APP: Nov5 large-move quarantine in its causal history.
- GNRC: Sep17 large-move quarantine.
- MRNA: Aug19 large-move quarantine.
- PTC: Oct5 large-move quarantine.
- SKYD, VYLR: insufficient continuous clean historical observations under existing feature rules.

No threshold was altered to release these names. Missing KVUE Nov2-3 bars remain missing after its index removal; they are not fabricated or used as current candidates.

## Preserved state and evidence

Only the new Nov13 decision/receipt and a new account evaluation were appended to the paper ledger. No skipped decision date was backfilled. Both fee cases remain all cash with no trades; this does not establish strategy performance.

- Decision: `data/sp500-paper-ledger/decisions/2026-11-13.json`.
- Receipt: `data/sp500-paper-ledger/receipts/2026-11-13.json`.
- Publication proof, replayed filters, frozen ledger hashes and DB backup hashes: `output/sp500-top20-20261114/publication-verification.json`.
- Old/new projection backups: `output/sp500-top20-20261114/paper-web-before.json`, `paper-web-after.json`.
- Acquisition/lineage/membership review and action records are in the same output directory.
- Tests: 2 latest dataset helper tests, 9 original ledger tests, 2 overlap tests and 9 existing projection tests passed.
- No commit, push, scheduler change or broker order was performed.

Updated application: http://127.0.0.1:8081/quant
