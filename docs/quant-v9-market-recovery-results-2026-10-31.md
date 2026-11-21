# V9 historical market-input recovery — 2026-10-31

## Outcome and scope

The 59 previously missing symbol/date market-input keys for ANSS, AZN, GFS, ILMN, MDB and
TEAM have been filled in a separate offline research experiment. Each is a historical decision
close multiplied by actual common shares reported publicly **before** that decision. These are
latest-prior-reported company-equity capitalization proxies, not exact daily share registers or
float-adjusted index capitalizations. No current-share substitution or interpolation was used.

All eleven older dates now have 101 eligible V9 forecasts each. The 1,111 recalculated V9
forecasts reproduce all 1,052 previously available forecasts without changes, excluding only
the separate research namespace and actual calculation timestamp. The 59 holes are filled;
the previously partial groups are now eligible as complete research populations.

| Research coverage | Before | After |
| --- | ---: | ---: |
| V9 complete decision dates | 8 | 19 |
| V9 nonoverlapping 20-session holding periods | 6 | 14 |
| Combined V2–V9 complete model/date groups | 141 | 152 |
| Combined distinct admitted forecasts | 14,241 | 15,352 |

Added decisions: 2024-07-31, 08-30, 09-30, 10-31, 11-29, 12-31; 2025-01-31, 02-28,
03-31, 04-30 and 05-30. The combined selected holding windows extend from entry 2024-08-01
through exit 2026-03-30. This is a set of verified research windows, **not** an uninterrupted
monthly backtest over that calendar interval. Other missing dates remain excluded.

## Evidence and normalization

- ANSS, ILMN, GFS and MDB use the original saved SEC companyfacts and submissions JSON.
  Their original manifests/hashes were checked before reuse. Only actual outstanding common
  shares are admitted: not issued shares, treasury shares, float, or EPS weighted averages.
- Of 59 recovered keys, 37 use genuine SEC XBRL and 22 use manually curated primary SEC text
  (11 TEAM and 11 AZN). Manual reader observations remain explicitly distinct from acquired
  original filing bytes; they were not relabeled XBRL or given a trust upgrade.
- TEAM sums economically participating Class A and B actual outstanding shares, without
  multiplying Class B by voting power. MDB uses outstanding counts, not issued counts or a
  hypothetical legacy second class. Details: `docs/quant-v9-dual-share-evidence-2026-10-30.md`.
- GFS's 553,548,190-share DEI observation is retained rather than averaging it with the rounded
  553,548,000-share IFRS observation from the same filing.
- AZN uses ordinary-share counts unscaled because the archived Yahoo closes are already
  ordinary-equivalent after the retrospective 2026-02-02 ADS conversion. This is not proof of
  historical executable ADS prices. Details: `docs/quant-v9-azn-share-evidence-2026-10-30.md`.
- Both share measurement date and filing date must be strictly before the decision date.
  Same-day filings are excluded, and cover-page observation dates are never backdated to
  fiscal period ends. The oldest selected measurement is 366 days old; no daily freshness
  claim is made. ILMN's reported rounding is preserved.
- Decision prices come from the archived decision-day candles, not future holding endpoints.
  ANSS's original ChartExchange CSV hash is pinned independently. Adjustment/data-use rights
  limitations remain; acquisition of free public evidence does not establish redistribution rights.

### Resume defect resolved

An unrestricted extraction encountered ten zero-count ANSS facts filed in 2014, outside the
research period. Extraction is now explicitly scoped to filings from 2023-01-01 inclusive to
2025-05-30 exclusive. This does not waive validity: invalid or conflicting in-scope counts still
fail closed. A new regression test failed before the change and passed afterward.

## Unchanged model, bounded execution

`scripts/QuantV9HistoricalMarketRecovery.java` reads the saved V8 forecasts and the actual
native market rows for 2024-07-01 through 2025-05-30. It adds only the 59 absent keys. It does
not overwrite the original 1,052 native market rows, rerun V2–V8, tune formulas or change the
production V9 service. Every forecast write and diagnostic calibration write is captured in
memory, never delegated to the real database mapper.

V9 is an existing provenance-adjustment wrapper around V8. Positive capitalization enables
its coverage check; the **magnitude of capitalization does not drive its predictions**. Genuine
XBRL retains its existing source category; curated text retains the existing weak-provenance
return/probability shrinkage. This recovery demonstrates input coverage and reconstruction
consistency, not a newly discovered capitalization alpha or an improved fitted model.

## Verification evidence

- Python suite: **65/65 passed**, log `tmp/quant-v9-python-tests-20261031.log`.
- Existing V9 source and the new offline helpers compiled successfully. Compiler emitted a
  deprecated-API note for the Jackson helper APIs; no compiler errors remained.
- All 1,111 forecasts are identical in a separate run where the current target return is changed
  to -50%, except calculation timestamps. Diagnostic metrics change, confirming the perturbation
  is effective. Target-label calibration is captured/discarded and never fed into later forecasts.
- All 1,052 prior known V9 forecasts match their preserved outputs, excluding namespace/time.
- Independent Python checks reconcile 152 complete model/date groups to the reconstructed
  membership sets, endpoint returns, benchmark levels, MAE, Brier score, directional accuracy,
  rank IC, top-20 baskets, 10-bps-per-side costs and nonoverlap selection.
- A separate Java accounting calculation and Python compounding agree. V2–V8 accounting is
  identical to the previously saved results; no earlier experiment artifacts were overwritten.
- V9's descriptive cumulative price return after assumed costs is 45.551051%, compared with
  the same-window price benchmark's 33.065389%, over 14 selected periods. These numbers are
  conditional research arithmetic, not live performance or a claim of proven investability.
- Fresh before/after DB invariant exports are byte-identical. The new research namespace has
  zero DB forecast rows; strict saved evaluation record 5 remains `INSUFFICIENT_DATA` with zero
  valid months. No model promotion, application mutation, schema migration or Git operation.

Scoped fresh-context code/data review completed with no Critical, Important or Minor findings.
The reviewer independently checked focused tests, selected observations, forecast comparisons,
all recorded input/report hashes and the accounting population/nonoverlap logic. Review was
read-only and did not independently reacquire external filings or access the DB. Main-session
DB checks above cover the latter; external authenticity, exact daily capitalization and native
input quality remain bounded by the supplied evidence and explicit research limitations.

## Artifacts and replay without recalculating models

- Inputs and source hashes: `tmp/quant-v9-market-inputs-20261030/inputs.json`.
- Normal run: `tmp/quant-v9-historical-market-final-20261031/result.json`.
- Counterfactual: `tmp/quant-v9-historical-market-perturbed-20261031/result.json` (regression only).
- Combined accounting and independent verification: normal-run directory
  `combined-java-accounting.json` and `independent-verification.json`.
- DB evidence: `tmp/quant-v9-before-20261031.tsv`, `tmp/quant-v9-after-20261031.tsv`.

```powershell
python -X utf8 scripts/verify_quant_v9_market_recovery.py
```

Do not rerun completed model stages or overwrite the preserved normal/counterfactual outputs.

## Remaining exclusions and limits

2025-06-30 remains blocked by ANSS's acquisition-crossing holding window; no delisted price
bridge was fabricated. 2025-10-31 remains blocked by SOLS's unresolved constituent effective
date and fewer than 80 prior candles; no missing security was dropped or replaced with HON.
2026-03-13 still lacks 14 historical predictor rows. Those are separate next acquisition targets.

Official complete historical constituent provenance, contemporaneous input receipt timestamps,
former-member training coverage, corporate-action settlement/total-return evidence and applicable
data-use rights remain incomplete. Existing missing-factor policies and frozen/reconstructed V8
training context remain unchanged. Consequently all outputs stay **research only**, never
official point-in-time certification or LIVE forecasts, and `promotionAllowed=false`.
