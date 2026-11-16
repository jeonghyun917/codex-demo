# March13 input recovery and SOLS follow-up — 2026-10-31

## Verified outcome

Recovered the14 previously missing predictor rows for the reconstructed Nasdaq100 population
on2026-03-13: ALNY, ARM, ASML, CCEP, FER, INSM, MELI, MRVL, MSTR, PDD, SHOP, TEAM, TRI and ZS.
All101 members now receive forecasts from the unchanged V2–V9 formulas:808 forecasts.
These are current research reconstructions, not contemporaneously saved or LIVE predictions.

Combined with the preserved experiments, each model now has20 complete101-member decision
dates rather than19:160 model/date groups and16,160 distinct admitted forecasts. The newly
recovered window enters2026-03-16 and exits2026-04-14. It lies inside the already covered
calendar span and overlaps existing cash-to-cash holdings. Consequently the nonoverlapping
holding-period count stays14, not15; prior compounded accounting is unchanged for every model.
This fills an internal diagnostic gap, not an additional independent month or a new calendar end.

## Sources newly obtained versus reused

- Six new original public SEC JSON files for CCEP/FER/TRI:3,072,656 bytes, HTTP200, issuer CIK,
  SHA-256, byte size, headers, UTC request/retrieval dates and annual submissions matching verified.
  The sandbox DNS failure and authorized normal-network retry are distinguished in the manifest.
  No fee, account, entitlement, proxy, host substitution or access-control bypass was used.
- Reused the archived Yahoo OHLCV originals for all14 names, verified their existing hashes and
  at least80 pre-decision candles. The model helper independently checks all21 supplied benchmark
  holding sessions against each recovered security's actual candle dates, not just endpoints.
- Reused prior-filed financial rows exported read-only from the existing DB for10 names, and
  the original TEAM companyfacts. The export records its actual current retrieval time. It is
  not a new raw SEC acquisition or proof of historical delivery/receipt timestamps.
- TEAM's265,505,542 actual Class A+B shares measured2025-12-31, filed2026-02-06, supply the one
  missing market row using its archived decision close. Manual text retains weak V9 provenance,
  never SEC_XBRL/free-float/index-capitalization status. The100 native market rows are preserved
  with their existing source limitations; exact daily capitalization is not certified here.

See `docs/quant-march-foreign-evidence-2026-10-31.md` and its machine-readable source manifest.
Current public availability does not establish redistribution/production usage rights.

## Unit, period and missing-data safeguards

- Only `filedAt < decision` and `endDate < decision` financials qualify. Same-day filings and later
  comparative history are not backdated. Actual annual durations must be330–380days and actual
  standalone quarters70–110days. TRI's Canadian40-F annual is explicitly recognized. Half-year
  results are not quarterly data. CCEP/FER have no recent usable standard standalone quarters.
- ASML, CCEP and FER keep consistent EUR amounts for within-period financial ratios; their EUR
  EPS is omitted from USD-price valuation without dated FX evidence. The unchanged model's
  explicit missing-factor policy applies, not an invented converted observation.
- ARM's primary annual cover establishes one ordinary share per ADS. PDD's establishes four.
  PDD's reported diluted USD2.60 per ordinary share becomes derived rounded USD10.40 per ADS;
  the rounding and convenience-translation origin remain explicit, not claimed as a directly
  observed precise ADS EPS. No later annual results or assumed FX are introduced.
- FER's new operating-income-like tag lacks an independently verified semantic mapping, so its
  current-year operating income remains null. Stale prior-accession values are not joined onto it.
- Existing missing analyst/news/institution policies, unobserved recent foreign quarters and
  UNKNOWN sector fallbacks for the newly reconstructed names remain documented limitations.
  Current data availability is not evidence of official point-in-time factor availability.

## Errors caught and excluded before final delivery

Independent validation found that the existing DB's previous-annual lookup could select ALNY
FY-labeled2025-09-30 with273days. The inventory now walks back to actual duration-valid periods,
rejecting the273/181/90day FY-labeled rows before accepting the real preceding annual. A regression
test failed before this fix and passed afterward. This is a guard in the offline recovery path;
the production DB rows and shared mapper have **not** been migrated or repaired in this task.

The one fresh-context reviewer then found an Important cross-currency defect: PDD FY2024 USD
revenue was compared with FY2023 CNY revenue, producing spurious -78.21% revenue growth.
The final fix excludes the incompatible immediately preceding annual from the comparison;
it neither invents FX nor jumps to an older nonadjacent USD year. PDD's growth factor now uses
the model's explicit missing-comparison score50 instead of the erroneous14. The input retains
the rejected prior currency/end date and reason. A paired-period currency regression test was
observed RED→GREEN, and all73 tests passed after the fix.

The final run is **v3**. Initial unversioned and v2 March model directories are superseded and
must not be used as final results. No earlier19-date experiments were overwritten or replayed.
One requested review and one fix pass were used; no second independent-review claim is made.

## Verification

-73/73 Python tests passed, log `tmp/quant-march-python-final-20261031.log`.
-All808 final forecasts are identical under a separate -50% target-return perturbation, apart
  from actual calculation timestamps. Metrics change, so the test perturbation is effective.
-Independent Python checks cover causal financial periods/currencies/units, whole101-member
  populations, endpoint returns, benchmark levels, forecast MAE/Brier/direction/rank IC, top20
  baskets,10bps per-side costs, complete-group admission and nonoverlap compounding.
-Separate Java accounting agrees on160 groups/16,160 forecasts and14 nonoverlap periods.
  Prior combined accounting is exactly unchanged because the additional decision overlaps.
-All DB invariant exports before/after the final v3 run are byte-identical. The research namespace
  has zero DB forecast rows. Strict saved evaluation record5 remains INSUFFICIENT_DATA/0months.
  Captured model, factor and diagnostic calibration writes never delegate to the real mapper.
-The existing Java formulas were not edited. Offline helpers compiled; deprecated-API compiler
  notes remain nonfatal. No unrelated full Maven replay, schema migration, Git operation,
  deployment, UI mutation or web-server restart was performed.

Review exclusions retained as limitations: UNKNOWN historical sector classifications, legacy
factor availability, weak TEAM share provenance, absent FER tag mapping/EUR FX conversion,
rounded PDD ADS conversion, unresolved SOLS, overlap accounting and frozen earlier training.
These are not assertions of official PIT validity or proven predictive/investment usefulness.

## SOLS: still not eligible

The bounded follow-up found no stronger contemporaneous Nasdaq index-entry/removal notice.
The new Honeywell issuer text confirms regular-way trading at October30,2025 market open;
it does not establish Nasdaq100 inclusion. A raw issuer download returned403 and was stopped.
The error body is preserved separately, not treated as acquired source evidence; the readable
primary observation remains explicitly curated. No new successful original index notice.

Nasdaq's retrospective October20/November10 interval and the secondary October30/November6
claim remain unresolved. Both candidate intervals include October31, so they do not solve its
predictor cold start. Even the earlier when-issued history has at most10 weekday sessions,
below the unchanged80-candle threshold. No HON history substitution, missing-member deletion,
threshold relaxation or official-PIT membership promotion was made.

Details: `docs/quant-sols-followup-2026-10-31.md`. The prior primary-source investigation remains
valid alongside this follow-up, not superseded by a plausible unproven date interpretation.

## Authoritative artifacts and resume commands

- Native export: `tmp/quant-march-inputs-20261031/native-inputs-v2.json`.
- Final45 financial rows/14symbols,101holdings and TEAM market input:
  `tmp/quant-march-inputs-20261031/inputs-v3.json`.
- Final forecasts: `tmp/quant-march-model-v3-final-20261031/result.json`.
- Counterfactual: `tmp/quant-march-model-v3-perturbed-20261031/result.json` (regression only).
- Final directory: `independent-verification.json` and `combined-java-accounting.json`.
- DB evidence: `tmp/quant-march-before-20261031.tsv`, `tmp/quant-march-after-v3-20261031.tsv`.

```powershell
python -X utf8 scripts/verify_quant_march_recovery.py
python -X utf8 tmp/quant-march-foreign-evidence-20261031/verify_evidence.py
python -X utf8 tmp/quant-sols-followup-20261031/verify_evidence.py
```

Do not rerun completed models or overwrite experiment artifacts just to resume. Official complete
historical rosters/receipt times, compatible prior PDD monetary history, recent foreign quarters,
FX evidence, native-market input quality, ANSS acquisition settlement and source usage rights
remain open. Existing strict product eligibility stays unchanged; `promotionAllowed=false`.
