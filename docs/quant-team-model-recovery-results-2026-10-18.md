# TEAM inputs and existing Quant model verification

## Result

The missing TEAM feature row is now reconstructed from archived prices and filing-date-bounded
SEC financial information. Existing V2–V9 formulas produced **2,424 distinct forecasts**:
3 decision dates × 8 models × the entire 101-security reconstructed Nasdaq population.
These are newly calculated **research forecasts**, not predictions recorded before the events.
The unchanged model policies still neutralize missing factors; full forecast-row coverage does
not mean every underlying factor was historically observed.

Decision dates were the three latest previously identified complete-price month-end candidates:
**2026-01-30, 2026-02-27 and 2026-03-27**. They were not selected for their returns.
Each date has all 101 members, complete supplied 20-session price/benchmark windows and no
missing-member exclusion. The price-only baseline from the preceding work remains a different
experiment; this run actually invokes the existing Quant prediction services.

**No operating or frozen research database was modified. No model promotion, live rollout,
commit, push or web-server restart occurred.** The strict saved evaluation remains
`INSUFFICIENT_DATA`, zero valid months. This bounded experiment closes an execution/coverage
gap, not the official historical population or investment-validity gap.

## Newly recovered public inputs

- SEC [TEAM company facts](https://data.sec.gov/api/xbrl/companyfacts/CIK0001650372.json)
  and [filing metadata](https://data.sec.gov/submissions/CIK0001650372.json) were saved as original
  response bytes, with URL, retrieval time and SHA-256 in `tmp/quant-team-inputs-20261018/manifest.json`.
  CIK is 1650372. These current downloads contain historical filing facts, not a historical
  receipt-time archive. Only filings strictly before each decision are eligible; later amendments
  and same-day filings are excluded. Missing values remain null, not zero.
- Latest annual input for all three dates: fiscal year ended 2025-06-30, filed 2025-08-15,
  revenue USD 5,215,304,000. January uses quarter ended 2025-09-30, filed 2025-10-31,
  revenue USD 1,432,553,000. February/March use quarter ended 2025-12-31, filed 2026-02-06,
  revenue USD 1,586,315,000. Quarter extraction requires an actual 70–110-day duration;
  six-/nine-month YTD values are not treated as standalone quarters.
- Actual common shares from SEC [September quarter filing](https://www.sec.gov/Archives/edgar/data/1650372/000165037225000068/team-20250930.htm)
  and [December quarter filing](https://www.sec.gov/Archives/edgar/data/1650372/000165037226000011/team-20251231.htm):
  Class A plus Class B totals **263,297,496** and **265,505,542** shares respectively.
  These are actual quarter-end outstanding shares, not weighted-average EPS denominators.
  The public web reader supplied the count/rights observations; direct original HTML returned
  403 and was not bypassed. The saved share JSON is explicitly manually curated SEC text,
  **not raw SEC HTML or SEC XBRL**. SEC submissions independently confirm accession, form,
  report date, filing date and primary document, but do not independently prove the transcribed counts.
- V9 receives decision-date Class A close × latest eligible A+B common shares. Equal dividend
  and liquidation rights support an explicitly labeled company-equity-value proxy, not an
  observed Class B price, float-adjusted capitalization or index-weight input. V9's existing
  weak-provenance shrinkage remains active; no trust upgrade was introduced.
- TEAM OHLCV uses the previously archived exact-symbol Yahoo chart response, validated against
  its pinned original hash. It was not fetched again, replaced by adjusted total-return prices,
  or inserted into the canonical candle table.

## Additional constituent evidence

A complete 101-security table was recovered from the SGX-hosted
[Macquarie supplemental listing document](https://links.sgx.com/FileOpen/.NDX_26%2C000_Put_SLD.ashx?App=Prospectus&FileID=69863).
It was published **2026-05-04** and explicitly describes membership effective **2026-04-20**.
It is an issuer republication, not a Nasdaq-authenticated historical constituent delivery.

Reversing only Nasdaq's documented April 20 SNDK-for-TEAM change gives exact set agreement
with all 101 securities at each of the three earlier research snapshots: 303 compared rows,
zero missing/extra symbols. This is retrospective corroboration, **not proof that no other
changes intervened or that the information was available at the forecast cutoffs**. It was
not supplied to the predictive feature computation as contemporaneous information.
Complete official target-date full rosters acquired: **0**. Rights to production/model use or
redistribution are not established by public access; the raw offering document remains local.
See [roster evidence](C:/dev/codex-demo/docs/quant-model-input-roster-evidence-2026-10-18.md).

## Bounded existing model results

Each date ranks all 101 securities and selects the existing top-20 basket. Costs are 10 bps on
purchase plus 10 bps on sale; the capital is fully reserved until liquidation. January's position
runs February 2 to March 3, so the February 27 decision is excluded from sequential portfolio
compounding, although its full 101 forecasts remain in prediction diagnostics. The second
position runs March 30 to April 28. Cash gaps earn zero in both strategy and benchmark.

The following are **two selected, sparse reconstructed holding periods**, not a continuous
year-to-date return or a statistically established edge. Returns are price returns, not
dividend-reinvested total returns. The benchmark is the matching Nasdaq-100 FRED price index.

| Existing model | Sequential periods | Net compounded return | Matching benchmark |
| --- | ---: | ---: | ---: |
| V2 | 2 | 14.8701% | 13.0963% |
| V3 | 2 | 17.9072% | 13.0963% |
| V4 | 2 | 17.9072% | 13.0963% |
| V5 | 2 | 20.9189% | 13.0963% |
| V6 | 2 | 20.9203% | 13.0963% |
| V7 | 2 | 20.9203% | 13.0963% |
| V8 | 2 | 20.9203% | 13.0963% |
| V9 | 2 | 20.9203% | 13.0963% |

There is no basis to select or promote a model using this short sample. For example, V9's
cross-sectional rank IC is -0.0715, +0.0562 and +0.2020 on the three dates; its Brier scores
are 0.2551, 0.2516 and 0.2397. The first two are worse than the simple constant-50% Brier
reference of 0.25. Positive basket returns do not establish calibrated or stable forecasts.
Annualized figures from these two periods are deliberately not presented as evidence of edge.

## Verification evidence

- Standalone Java adapter compiled and both normal/counterfactual runs exited successfully.
  It calls existing historical feature and V2–V9 services, with DB reads and in-memory captured
  writes only. Unknown mapper operations fail closed. Output namespace has **zero DB rows**.
- All 2,424 normal/counterfactual predictions match on **every field except actual calculation
  timestamp** when current decision future outcomes are changed to -50%. Evaluation values
  change, proving the perturbation reached the outcome path. This tests current-target-label
  leakage; it does not establish the validity of every historical source or earlier training row.
- An independent Python verifier checked full membership keys, date/filing boundaries, input
  hashes, share arithmetic, prediction ranges, MAE, Brier, direction, tied-rank IC, top-20 basket
  accounting and nonoverlap compounding against separate holdings and benchmark exports.
  All 24 model/date groups matched, with numeric tolerance for six-decimal stored outcomes.
  A deliberately altered prediction is rejected by the verifier's negative control.
- A source-object isolation guard initially caught current ranking mutating shared outcome
  objects across decision runs. Deep copies now isolate each decision; both final runs verify
  original outcome serialization is unchanged. The earlier exploratory output is superseded
  and must not be used for reporting. No database object was altered by that in-memory bug.
- **48 Python tests passed**, including five new financial extractor RED→GREEN tests.
  The first full run hit two sandbox TEMP directory permission errors; rerunning with TEMP
  inside the workspace passed all 48. No tests were removed or weakened for that environment issue.
- A fresh read-only scoped review found no directly relevant Critical/Important defect.
  Production Java was unchanged, so the previously verified 200-test Maven evidence was
  not redundantly rerun for local scripts/data. No new application build/GUI QA is claimed.
- Frozen original prediction row counts, last calculation times and value sums; operating
  macro feature counts/times; and all corrected outcome count/return sums match the prior
  checkpoint exactly. The strict evaluation record is unchanged at insufficient/zero months.

## Remaining limitations and safe continuation

TEAM analyst, institution and news history is still absent where the original builder uses its
documented neutral-factor policy. Negative/unusable EPS also retains the original valuation
policy; its generic missing-EPS explanation should not be read as proof the SEC fact was absent.
The original builder compares the latest available standalone quarters; no missing Q4 is
fabricated, so a comparison need not be adjacent-quarter growth. Its existing high data-quality
label is not an independent certification of complete observed predictors.

Earlier training outcomes/ranks remain the frozen reconstructed context, including prior missing
former-constituent history. TEAM older training labels were not added. Historical calibration
forecasts are also retrospective, not contemporaneously recorded predictions. Several other
members retain current-share proxies. These constraints and unproven target-date official
membership prevent any official PIT or live-performance claim.

Next useful work is independently confirmed BNY/BK identity-price history, CTRA/HOLX history and
event settlement for earlier incomplete windows, plus a dated official Nasdaq baseline with a
complete intervening change chain and appropriate usage rights. Do not rerun completed model
namespaces or weaken population rules to manufacture more months. Preserve these completed
three-date results as a research checkpoint rather than promoting them into the live product.

## Reproduction and saved artifacts

Normal output: `tmp/quant-team-model-recovery-final-20261018/result.json`.
Independent checks: same directory `independent-verification.json` (includes each selected basket).
Counterfactual: `tmp/quant-team-model-recovery-perturbed-20261018/result.json`, **never a performance source**.
Final run logs: `tmp/quant-team-model-final-run-20261018.log` and
`tmp/quant-team-model-perturb-run-20261018.log`.
Input manifests and normalized facts: `tmp/quant-team-inputs-20261018/`.
Curated shares: `tmp/quant-team-share-evidence-20261018/`.
Original/post invariants: `tmp/quant-macro-before-20261018.tsv` and
`tmp/quant-team-after-invariants-20261018.tsv`.

Run file-only verification, without DB writes or new downloads:

```powershell
python -X utf8 scripts/verify_quant_team_models.py
python -X utf8 tmp/quant-team-share-evidence-20261018/acquire_verify_shares.py
python -X utf8 tmp/quant-model-input-roster-20261018/acquire_roster_evidence.py
```

The acquisition/normalization/model scripts refuse overwriting completed inputs/results. Reuse
the saved outputs on resume; an intentional new experiment needs a new directory/version.
