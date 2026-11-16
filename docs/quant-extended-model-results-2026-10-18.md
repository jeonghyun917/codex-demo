# Expanded historical inputs and Quant backtest verification

## Result and scope

The existing V2–V9 formula experiment now covers **8 decision dates rather than 3**, each with
the entire reconstructed 101-security Nasdaq-100 population. Five newly recovered dates add
**4,040 predictions**, for **6,464 distinct predictions** across 64 model/date groups. The
chronological cash-to-cash portfolio calculation now has **6 nonoverlapping holding periods
rather than 2**, beginning August 1, 2025, rather than February 2, 2026.

This verifies a larger **retrospectively reconstructed research** sample, not official point-in-time
membership, complete observed predictors, investable execution, or a demonstrated model edge.
Operating/frozen databases, original three-date result files, product formulas, strict promotion
rules and Git state were not changed. The saved strict evaluation is still insufficient/zero months.

## Free data recovered

The new SEC companyfacts and submissions responses for AZN (CIK901832) and GFS (CIK1709048)
were downloaded successfully as original JSON bytes. Four responses total **2,379,357 bytes**,
with original SHA-256, request/retrieval UTC times and HTTP200 in
`tmp/quant-extended-inputs-20261018/manifest.json`.

Sources: [AZN facts](https://data.sec.gov/api/xbrl/companyfacts/CIK0000901832.json),
[AZN filing metadata](https://data.sec.gov/submissions/CIK0000901832.json),
[GFS facts](https://data.sec.gov/api/xbrl/companyfacts/CIK0001709048.json),
[GFS filing metadata](https://data.sec.gov/submissions/CIK0001709048.json).

The foreign-issuer extractor accepts IFRS annual 20-F and actual 70–110-day 6-K quarters, matching
field values by accession, filing date, period and currency/unit. Untagged GFS quarter labels
are derived only for calendar-aligned three-month durations; six-/nine-month YTD is excluded.
Every filing is strictly before its decision; same-day and later amendments are unavailable.
Missing fields remain null. Existing TEAM facts were reused without another download.

| Restored symbol | Annual information available in newly tested dates | Additional basis checks |
| --- | --- | --- |
| TEAM | July uses FY2024 revenue $4.358603bn; later dates use FY2025 $5.215304bn after August15 filing | New March/June2025 actual A+B quarter-end shares, followed by already reviewed September shares |
| AZN | FY2024 revenue $54.073bn, filed February18,2025; available standalone quarter through June2025 | Ordinary-equivalent historical price series paired with ordinary EPS and ordinary share count |
| GFS | FY2024 revenue $6.750bn, filed March20,2025; available standalone Q1/Q2/Q3 after respective filings | Actual IFRS NumberOfSharesOutstanding, never weighted-average EPS shares |

AZN's current standard-tag companyfacts do not supply a later standalone 2025 quarter in these
inputs; it is not forward-filled or fabricated. The original builder therefore retains its latest
available-quarter policy, including nonadjacent-quarter comparisons. Analyst/news/institution
history remains missing where the existing documented neutral-factor policy applies.

## Share and ADR unit evidence

Newly reviewed TEAM primary filing text gives actual March31,2025 A/B shares
165,150,665 + 98,008,049 = **263,158,714** (filed May2), and June30 A/B shares
165,949,196 + 97,030,987 = **262,980,183** (filed August15).
Sources: [March filing](https://www.sec.gov/Archives/edgar/data/1650372/000165037225000022/team-20250331.htm)
and [annual filing](https://www.sec.gov/Archives/edgar/data/1650372/000165037225000036/team-20250630.htm).
The annual filing confirms equal dividend/liquidation economics, not equal voting rights.
These remain manually curated primary web-reader observations, not raw acquired HTML/XBRL.

AZN's [2024 annual financial statements](https://www.astrazeneca.ch/content/dam/az/Investor_Relations/annual-report-2024/pdf/AstraZeneca_AR_2024_Financial_Statements.pdf),
Note24, printed page192/PDFpage45, report **1,550,546,239** actual issued ordinary shares at
December31,2024. This is not a weighted-average EPS denominator or free-float/index-share count.
The issuer reports employee-benefit-trust shares separately; the derived capitalization is a
company-equity proxy, not proof of freely tradable shares. The February18 SEC annual filing date
is confirmed by the saved submissions metadata. Numeric transcription from the primary reader
is not automatically verified against original PDF bytes and is labeled accordingly.

AZN's archived Yahoo chart contains the February2,2026 **1:2 ADS-to-ordinary transition** and
already represents earlier closes in ordinary-equivalent units. For example, July31's close is
146.18, not a historical $73.09 ADS execution quote. Ordinary EPS4.50 must remain unscaled:
146.18/4.50 and 73.09/2.25 express the same PE. Dividing EPS again against the adjusted close
would falsely double PE. The adapter requires the saved split event before accepting this basis.
This is retrospective unit normalization, not proof that a later-published event was known in2025.
See the issuer's [listing transition notice](https://www.astrazeneca.com/media-centre/press-releases/2026/astrazeneca-to-complete-direct-listing-on-NYSE.html/)
and [contemporaneous annual-report ADS description](https://www.sec.gov/Archives/edgar/data/901832/000110465925014732/tm251208d3_ex99-1.pdf).

GFS counts come from original saved SEC IFRS instant facts with report-date/accession checks:
554,733,529 at March31, 554,971,650 at June30, and 555,681,705 at September30,2025, each eligible
only after its actual filing. V9 retains its existing genuine-XBRL source classification for these;
manually curated TEAM/AZN text remains weak-provenance. No model trust override was added.

## Constituent evidence and excluded dates

Two additional complete issuer-republished tables were recovered from official SGX-hosted
Macquarie offering documents, each with **101 unique securities**:

- Effective February26,2025; document published March3,2025.
- Effective August26,2025; document published September1,2025.

The table changes are -ANSS/-MDB and +TRI/+SHOP. Nasdaq's source-attributed syndicated TRI/ANSS
notice confirms July28 before-open effectiveness. The URL date is not substituted for its displayed
July18 publication time. The August list plus known events conditionally matches all ten previously
price-complete research snapshots. This is **issuer republication and conditional set consistency**,
not Nasdaq-authenticated target-date deliveries or an exhaustive contemporary change chain.
The August document was not available at the July/August forecast cutoffs. It is corroborating
evidence only, not a contemporaneous model input. Production/model-use and redistribution rights
remain unproven; raw offering documents stay quarantined locally.

An important discrepancy remains: Nasdaq's retrospective table gives SOLS October20–November10,
whereas the community intervals use October30–November5 inclusive. A corporate regular-way
trading date does not settle index membership. October31 has **102 members**, not101; it is excluded
as a whole rather than deleting SOLS. March13,2026 still lacks fourteen original predictor rows;
it is also excluded whole. Other older windows still have price/predictor gaps. No absent name was
given another company's data or a fabricated forecast.

Full source details and original hashes:
[additional roster evidence](C:/dev/codex-demo/docs/quant-extended-roster-evidence-2026-10-18.md).
Official Nasdaq-authenticated target rosters acquired in this pass: **0**.

## Evaluated dates and portfolio accounting

Forecast diagnostics include July31, August29, September30, November28 and December31,2025,
plus January30, February27 and March27,2026. Existing three-date predictions were reused unchanged,
not recalculated. Each date is a fixed full reconstructed population, not a rolling index-holdings fund.
Eligibility was determined by input availability, not by realized returns; availability selection bias
still limits generalization. Twenty-session holding windows require all supplied price/calendar slots.

Top20 baskets use the existing predicted-excess-return ranking, equal basket weights, 10bps cost
per side and terminal liquidation. Capital is reserved until exit; idle cash earns zero for strategy
and matching price benchmark. With the earlier start, January30 and March27 decisions overlap
already open positions and do not enter combined returns. The six actual sequential windows are:

| Decision | Entry | Exit |
| --- | --- | --- |
| 2025-07-31 | 2025-08-01 | 2025-08-29 |
| 2025-08-29 | 2025-09-02 | 2025-09-30 |
| 2025-09-30 | 2025-10-01 | 2025-10-29 |
| 2025-11-28 | 2025-12-01 | 2025-12-30 |
| 2025-12-31 | 2026-01-02 | 2026-02-02 |
| 2026-02-27 | 2026-03-02 | 2026-03-30 |

This is a sparse, noncontinuous research sample, not calendar-period total return. Cash gaps,
dividends, spin-off distributions, slippage, capacity and market impact are not a full execution model.
Price-return benchmark comparison is on the same selected holding intervals. The larger sample's
returns are **not directly comparable** to the preceding two-period returns: the selected windows
and denominator have changed.

| Existing formula | Net return over six holding periods | Matching price benchmark |
| --- | ---: | ---: |
| V2 | 21.4016% | 8.4440% |
| V3 | 24.2055% | 8.4440% |
| V4 | 25.5795% | 8.4440% |
| V5 | 13.8108% | 8.4440% |
| V6 | 13.8108% | 8.4440% |
| V7 | 13.6267% | 8.4440% |
| V8 | 13.6267% | 8.4440% |
| V9 | 13.8108% | 8.4440% |

Six periods and incomplete historical source guarantees do not justify choosing the best row,
claiming persistent alpha, or relaxing the promotion gate. Older training rows/ranks and prior
calibration forecasts remain the frozen reconstructed context; newly recovered past outcomes are
not rolled into later training. This avoids modifying prior experiments but is not a fully rebuilt
all-history walk-forward dataset. All forecasts were calculated now, not live at their decision dates.

## Verification and saved work

- Five foreign-input tests observed failing before implementation, then passed. Full Python
  suite: **53 tests passed**. The old TEAM extractor and saved original experiment still verify.
- New model adapter and file-only accounting checker compiled. Normal and future-target-label
  counterfactual model runs completed; current outcomes set to -50% change evaluation but leave
  all **4,040 new predictions identical on every field except calculation timestamp**.
- Each decision uses cloned outcomes; final source-object isolation guard passes. Missing keys,
  unknown mapper operations and out-of-scope writes fail closed. The mapper records model writes
  only in memory. No product Java, formula, schema or request-path changes were made.
- Independent Python checks matched all64 model/date groups for member keys, financial cutoffs,
  source hashes, share selection/arithmetic, prediction ranges, MAE, Brier, direction and tied-rank IC.
  Top20 costs/nonoverlap returns matched a separate Java application-evaluator invocation on
  both preserved experiments. Cost is1.2percentage points summed over six round trips before
  compounding, not a flat1.2point subtraction from the final gross compounded return.
- Independent evidence-script execution verified both new complete roster tables, source hashes
  and ten conditional set comparisons. An access-error body is never counted as source content.
- Read-only review found no directly related Critical/Important defect in new extraction/adapter
  and saved outputs. It did not independently review the later verification scripts. Minor deferred:
  comparative facts retain filing fiscalYear metadata, which can differ from observation year;
  actual period selection uses endDate, so this does not change current predictions.
- Fresh DB invariants match the original baseline exactly: frozen prediction counts/times/value
  sums, operating macro counts/times and corrected outcome totals. New research namespace has
  **0 DB rows**; saved strict record5 remains INSUFFICIENT_DATA/0months. No redundant full Maven
  replay was run for unchanged product code. No commit/push/merge/deployment/server restart.

Primary outputs: `tmp/quant-extended-model-recovery-final-20261018/result.json`,
`independent-verification.json`, `combined-java-accounting.json`. Counterfactual result in
`tmp/quant-extended-model-recovery-perturbed-20261018/` is **regression-only, never performance**.
Inputs/manifests: `tmp/quant-extended-inputs-20261018/`. Tests:
`tmp/quant-extended-python-tests-20261018.log`. Post-invariants:
`tmp/quant-extended-after-invariants-20261018.tsv`. Acquisition and model result scripts refuse
overwriting completed artifacts; preserve them on resume.

Offline verification without another model replay or download:

```powershell
python -X utf8 scripts/verify_quant_extended_models.py
python -X utf8 tmp/quant-extended-official-rosters-20261018/verify_evidence.py
```

Next useful evidence is SOLS's dated official index action/settlement, missing March13 predictors,
independently confirmed BNY/BK history and CTRA/HOLX prices/events for older gaps, and a complete
dated official index baseline/change chain with usable rights. Preserve the strict exclusions;
increasing a research sample does not certify live or official-PIT model performance.
