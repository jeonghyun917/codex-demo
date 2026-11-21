# Historical candle reconciliation and free-data acquisition

User instruction: find free alternatives and fix the eight discrepant symbols immediately without questions.

1. Establish affected date ranges and official/provider adjustment semantics for KLAC, CTVA, CRWD, APH, MNST, DD, SPGI and HON. Independently acquire pinned free community constituent histories, preserving their actual provenance.
2. Add a tested offline reconciliation path: verify archived source hashes, symbol/currency/date/OHLC validity and complete overlap with canonical history. Require one complete provider snapshot per symbol. Compare at persisted six-decimal precision; never infer a multiplier or substitute adjusted-close for OHLC close.
3. In one transaction, lock affected symbols/rows, save before/after values and source hashes, and update only changed existing dates. Keep the normal collector's quarantine rule. Dry-run first, then apply the already authorized repair. Preserve unrelated files and all original evidence.
4. Re-audit all prices and affected outcomes, refresh derived calculations, run focused and full relevant tests once stable, and report actual free-data coverage and remaining performance-validation gaps.

No paid purchase, account registration, push, or external publication. Continue on the existing main workspace to preserve the resumed work. Additive audit tables provide rollback evidence for every modified candle. Raw third-party data stays local.

## Completed execution

- [x] Root-cause evidence: rolling-window seam / mixed adjustment vintages, official actions cross-checked for all eight, provider source histories structurally valid.
- [x] Complete-overlap reconciliation, SHA checks, source/canonical before images, dry-run, atomic application and idempotent replay implemented and tested.
- [x] Real repair `b2cd7ef2-7df9-4d8d-b7fa-d1977043e925`: 1,323 OHLCV rows / 1,314 closes fixed; all-source overlap audit residual 0.
- [x] Correct related stored market prices with full backups (13,273 rows), retain historical cap values and explicitly mark unverified share/price basis, rebuild existing research benchmarks without rewriting membership/shares/caps, then refresh latest models/views.
- [x] Free alternatives acquired: 43 files with hashes/pinned commits, normalized research constituent intervals and reference series; known-date defects retained in QA, not promoted to official PIT.
- [x] Java 163/163 (including real temporary-DB tests), Python 2/2, current data refresh 518/518, four HTTP200 smoke checks. Actual evaluation run4 remains INSUFFICIENT_DATA; no performance certification asserted.

Details: `docs/quant-price-repair-results-2026-10-18.md`, `docs/quant-free-data-sources-2026-10-18.md`.
