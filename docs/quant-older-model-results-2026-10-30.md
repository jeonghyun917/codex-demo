# Older Quant research windows and remaining input gaps

## Result

The recovered ANSS price history and filing-bounded ANSS/MDB/ILMN financial inputs allow
**11 additional full-population decision dates**, July31,2024 through May30,2025, for the
unchanged **V2–V8** formulas. Together with the eight previously completed dates, these models
now have **19 decision dates and 14 nonoverlapping holding periods**, rather than eight dates
and six holding periods. The earliest accepted entry moves from August1,2025 to **August1,2024**;
the final accepted exit remains March30,2026. The sample is sparse, not a continuous monthly strategy.

**V9 is not extended:** each older date still lacks valid historical market-cap inputs for4–6
securities. Partial95–97-security V9 output is retained only for diagnostics; the entire date is
excluded from evaluation. V9 keeps its prior eight complete decision dates and six holding periods.

Accepted forecasts total **14,241**: 19×101×7 for V2–V8, plus8×101 for V9. The new full-population
contribution is7,777 forecasts; another1,052 partial V9 forecasts are deliberately excluded.
No model parameters, operating data, original experiments, membership records or promotion
rules were changed. The saved strict evaluation remains insufficient/zero valid months.

## Retained free evidence

The preceding interrupted acquisition preserved six successful original SEC responses for
ANSS(CIK1013462), MDB(CIK1441816), and ILMN(CIK1110803), totaling **9,374,220 bytes**.
Companyfacts and submissions files, URLs, HTTP statuses, retrieval times and original SHA256
are in `tmp/quant-older-inputs-20261018/manifest.json`. Existing TEAM/AZN/GFS raw responses
were reused rather than redownloaded. This continuation began on October30; acquisition
timestamps remain their actual original timestamps, not the resumption date.

Sources: [ANSS facts](https://data.sec.gov/api/xbrl/companyfacts/CIK0001013462.json),
[MDB facts](https://data.sec.gov/api/xbrl/companyfacts/CIK0001441816.json),
[ILMN facts](https://data.sec.gov/api/xbrl/companyfacts/CIK0001110803.json).
US financial normalization checks exact issuer identity, accession, units, annual versus standalone
quarter duration and filing date strictly before the decision. Later restatements, same-day filings,
YTD-as-quarter and zero-filled missing facts are excluded. Comparative fiscalYear metadata is not
a substitute for the actual period end used in selection.

The retained ANSS ChartExchange CSV has **370 observations from January24,2024 to July16,2025**,
with separate OHLCV, provider and symbol metadata. Its SHA256 is
`d3bd7e80e31fa9a082f82ac30caa52de017aa6a06b36e8c127068d8bf52247c1`.
StockAnalysis supplies a separate121-row comparison with provider-declared split adjustment.
Of121 common closes,120 match exactly and one differs by half a cent; the49 May6–July16 closes
match exactly. Volume differences mean the providers must not be mixed as interchangeable OHLCV.
ChartExchange's adjustment policy and usage rights remain unproven: these are **provider-close
research observations**, not certified executable prices or a licensed product-data import.
See [ANSS source evidence](C:/dev/codex-demo/docs/quant-anss-free-price-evidence-2026-10-18.md).

New staging preserves every101-member population and every21 supplied holding sessions,
checks the benchmark calendar against the existing AAPL price calendar, and requires the seed
builder's existing80-price-observation minimum. Canonical prices and prior compatible additions
are reused without replacing them. Missing ANSS quotes after July16 are not bridged through the
Synopsys acquisition: the June30,2025 decision remains excluded. Earlier dates with other
unresolved prices or short history are not declared complete.

## SOLS date findings

The primary Nasdaq retrospective table genuinely says October20 inclusion / November10 removal;
this was visually verified, not inferred from misaligned extraction. New Nasdaq exchange records
confirm SOLSV when-issued trading on October20 and regular-way SOLS on October30 with the same
CUSIP, but exchange-security status is not an index-membership notice. The community November6
reference is not an administrator notice with a separate effective timestamp.

**Exact index dates are still unresolved.** Four original public documents totaling539,872bytes
and the reused PDF pass fixed-hash checks. Neither candidate interval may silently replace the
other. Both include October31 conditionally, but even when-issued history provides at most ten
weekday sessions by then, below the unchanged80-candle minimum. Honeywell prices are not SOLS
history. October31 stays excluded as a full102-security date; SOLS is not deleted to obtain101.
See [SOLS primary-source investigation](C:/dev/codex-demo/docs/quant-sols-date-resolution-2026-10-18.md).

## Calculations and limits

The new decisions are July31, August30, September30, October31, November29 and December31,2024,
and January31, February28, March31, April30 and May30,2025. Price coverage—not realized return—
determines eligibility. Availability selection bias nevertheless remains. Inputs for originally absent
ANSS/AZN/GFS/ILMN/MDB/TEAM rows are reconstructed through the existing historical builder;
its documented missing analyst/news/institution and negative-EPS policies remain unchanged.
Complete prediction rows do not imply completely observed underlying factors.

Top20 baskets, ten-bps purchase and sale costs, terminal liquidation, and no overlapping capital
use the existing evaluator. Idle cash earns zero for both strategy and benchmark. Price returns
exclude dividends and spin-off distributions; slippage, capacity and execution are not a full trading
simulation. The14-period sample is not a continuous calendar-period total-return track record.

| Formula | Full decision dates | Sequential periods | Net selected-period price return | Matching price benchmark |
| --- | ---: | ---: | ---: | ---: |
| V2 | 19 | 14 | 54.5123% | 33.0654% |
| V3 | 19 | 14 | 60.4582% | 33.0654% |
| V4 | 19 | 14 | 62.8822% | 33.0654% |
| V5 | 19 | 14 | 43.7014% | 33.0654% |
| V6 | 19 | 14 | 43.3723% | 33.0654% |
| V7 | 19 | 14 | 44.0235% | 33.0654% |
| V8 | 19 | 14 | 44.0235% | 33.0654% |
| V9 | 8 | 6 | 13.8108% | 8.4440% |

**V9 is a different eligible interval set; its return must not be ranked against the other rows.**
Changing eligibility also means these longer-sample returns are not like-for-like improvements
over earlier two-/six-period figures. Positive returns in this selected sample do not establish a
persistent edge, calibration quality, official PIT membership or live performance.

Earlier training ranks/outcomes and retrospective calibration forecasts remain the frozen context.
Recovered older labels are not rolled into later training. Reconstructed membership, historical
receipt times and several source/price-basis assumptions remain unproven. The new recovery is
not a completed all-history walk-forward training dataset or justification to promote a model.

## Verification and correction

- Four newly resumed financial/coverage tests observed RED then GREEN; full Python suite
  **57 tests passed**. No product Java changed, so the prior product suite was not redundantly run.
- All new **8,829 raw predictions**, including1,052 ineligible partial V9 rows, are unchanged
  on every field except calculation timestamp after target future returns are set to-50%.
  Evaluation changes, showing the perturbation reached the outcome path. This is a current-label
  leakage regression, not proof of every older source's contemporaneous validity.
- Independent Python recalculation matches all **141 admitted model/date groups** on full101
  keys, financial timing, hashes, metric ranges, MAE, Brier, direction, tied-rank IC, top20 baskets,
  fees and nonoverlap compounding. Separate Java accounting through the application evaluator
  agrees. All1,111 new holdings preserve endpoint arithmetic and whole-population coverage.
- A scoped reviewer found no blocker in its first pass. Main's subsequent verification identified
  an important copied-date-range error: the initial adapter requested market inputs only from
  July2025 onward, falsely making all older V9 inputs unavailable. Correcting the range produces
  the actual95/96/97 known-member forecasts and4–6 missing names. The verifier now pins these
  expected member gaps. All7,777 V2–V8 prediction fields are identical before/after this correction.
- The correction is saved in new **v2** result directories; initial exploratory directories are
  superseded, not performance sources. No second independent-review certification is claimed.
  Only complete101-key model dates are admitted by both accounting implementations.
- Source-object isolation guards pass. Unknown mapper operations fail closed; model upserts are
  captured in memory, not delegated to the database. Fresh pre/post DB invariants are identical:
  frozen original prediction counts/times/sums, operating macro counts/times and corrected
  outcome counts/return sums. Since October18, unrelated operating data had legitimately changed;
  comparisons use the fresh October30 baseline, not stale global totals.
- The new namespace contains0DB forecast rows, and strict saved record5 remains
  `INSUFFICIENT_DATA`/0valid months. No Git staging/commit/push, migration, deployment, UI change,
  web-server restart, formula tuning, membership mutation or external publication occurred.

## Saved continuation point

Authoritative new experiment: `tmp/quant-older-model-recovery-v2-final-20261030/result.json`.
Independent audit and selected baskets: same directory `independent-verification.json`.
Separate Java calculation: same directory `combined-java-accounting.json`.
Counterfactual: `tmp/quant-older-model-recovery-v2-perturbed-20261030/`, regression-only.
Initial non-v2 older-model directories are superseded and must not be used for final results.

Staged inputs/holdings: `tmp/quant-older-staged-20261030/`. Raw SEC inputs:
`tmp/quant-older-inputs-20261018/`. Test log: `tmp/quant-older-python-tests-20261030.log`.
DB comparisons: `tmp/quant-older-before-invariants-20261030.tsv` and
`tmp/quant-older-after-invariants-20261030.tsv` (fresh verification after correction).

Offline checks, without rerunning the completed models or downloading sources again:

```powershell
python -X utf8 scripts/verify_quant_older_models.py
python -X utf8 tmp/quant-anss-free-evidence-20261018/verify.py
python -X utf8 tmp/quant-sols-resolution-20261018/verify_evidence.py
```

Next useful work: obtain actual historical share/capitalization inputs for V9's named gaps,
recover the14 missing March13,2026 predictor rows, and acquire explicit SOLS index notices.
Older corporate-action settlement and official complete constituent/usage evidence remain separate
requirements. Do not drop missing securities, substitute current shares, bridge delisted prices,
or relax the80-candle threshold merely to add months.
