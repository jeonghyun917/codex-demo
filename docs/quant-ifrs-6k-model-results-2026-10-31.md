# IFRS/6-K model-input support and measured20D error

Native input support and the supported historical statements are now actually integrated. The measured20D error did **not** improve; no model weights were changed or promoted.

## Actual same-cohort result

Definitive replay: `tmp/quant-ifrs-replay-all20-20261031-v3/`. Same20previously inspected dates,101members per date,2,020outcomes; source financial panels changed while historical training, price/outcome labels, market, macro and nonfinancial factors stayed fixed. This is an input-repair sensitivity, **not** a fully rebuilt historical-training walk-forward or a new independent holdout.

| MAE, percentage points | Before native IFRS | After native IFRS | After−before |
| --- | ---: | ---: | ---: |
| Absolute20Dreturn | 7.912899969 | 7.913521403 | +0.000621434 |
| Benchmark-excess20Dreturn | 7.664448799 | 7.665493386 | +0.001044586 |

Affected foreign subset:65symbol-dates. AbsoluteMAE5.532706468→5.560874217; excessMAE6.624367101→6.646061198. Zero-return baseline on the full cohort has absoluteMAE7.900103088 and excessMAE7.718620286. The current model does not beat the zero baseline on absoluteMAE. A tiny direction-rate increase in excess does not override worse primary error metrics.

All16,160before/control forecasts (V2–V9×20dates×101names) reproduced all saved fields except calculation timestamp exactly. The source/database fingerprints were unchanged by replay. Perturbing all101current target returns to−50% on2026-03-13 left all808model outputs invariant apart from timestamp. That checks this leakage mode, not every possible historical receipt or membership issue. Full evidence, source/code hashes and code snapshots are retained; earlier V1/V2 are superseded provenance stages, not additional independent experiments or holdouts.

`scripts/summarize_quant_ifrs_replay.py` recomputes MAE from individual realized returns, requires complete unique names and finite values, preserves affected/full populations, and reports nonoverlap dates without calling them a new holdout. An independent reviewer recomputed the arithmetic and the control/DB invariants. No statistical significance or lower-future-error claim is made.

### Continuing the work: paired rolling repaired-panel training

Rather than stop at target-input sensitivity, a second file-only experiment also repaired the recovered panels **in subsequent training**. Definitive output: `tmp/quant-ifrs-rolling-training-20261031-v2/`. Both arms add/replace the same prior101-member panels, with the same realized returns/entry/exit windows; only native versus baseline financial features differ. An outcome is available only when both its signal and actual exit dates are strictly before the next decision. Each arm also rolls its own already-generated prior model forecasts forward for subsequent model stages. Current outcomes are never used for their own forecasts; March target-label perturbation still leaves808forecast fields invariant.

| Rolling repaired-panel MAE, pp | Baseline financials | Native IFRS financials | Native−baseline |
| --- | ---: | ---: | ---: |
| Absolute20Dreturn | 7.914532391 | 7.915635821 | +0.001103430 |
| Benchmark-excess20Dreturn | 7.665019525 | 7.665597203 | +0.000577678 |

Again20dates/2,020outcomes,16,160forecasts per arm; both errors worsen slightly. The first-date baseline exactly matches the preceding frozen comparison because no earlier recovered panel has yet been added. This is **not** an all-dates unchanged-control assertion: subsequent baseline/control forecasts intentionally change as their own prior repaired panels/predictions become training input. Summary metadata distinguishes that first-date check from the frozen comparison's16,160all-field controls.

All per-date audit frontiers have `maxTrainingExit < decision`; training counts in that audit include the supplied5/20/60-day source rows, with actual20Dmodel training using its horizon filter. Pre-panel history remains frozen; this is a bounded chronological repair experiment, not complete regenerated historical/PIT training, not a new holdout, and not a parameter search. Independent review found no remaining scopedCritical/Important issue. The negative result remains the conclusion; expanding consistent coverage did not produce a promotion candidate.

## Supported native path

`SecEdgarService` now includes11explicit `ifrs-full` concepts and6-K/6-K/A. `SecFinancialStandardService` maps eight financial fields with explicit taxonomy, currency and exact-duration coherence. Owner-attributable profit/equity are preferred where available; no arbitrary concept-name similarity, FX or ADS scaling is used.

- CalendarFY requires actualJanuary1–December31dates; quarter requires actual standalone quarter dates and matching source `CYyyyyQn` frame.
- Null6-Kfiscal metadata is replaced by proven nonnull canonical keys before persistence. The original source object remains intact; filing-year labels are not mistaken for comparative observation years.
- Half-year/nine-month values and unproved/unframed quarters are not quarterized. Noncalendar fiscal years remain unsupported, rather than guessed.
- Instant balances attach only to a proved same-issuer/accession/filing/end-date duration; legacy persisted instant `start=end` keys remain unchanged, avoiding nullable-key duplicate regressions.
- Foreign `USD/shares` remains **not** USDquoted-security EPS. The existing price/EPS guard still blocks unproved quote/ADR compatibility. Same-currency accounting ratios do not require invented conversion.

Native persistence uses a shared INSERTbody plus `ON DUPLICATE KEY UPDATE id=id`. New keys add rows; an existing key's entire values, raw evidence and mapped timestamp remain unchanged, even if a later partial read has another currency or missing fields. Unsupported or empty input cannot erase historical standards. Nonempty domesticUSlegacy behavior was not redefined. A later correction to an existing native key needs a separate reviewed, versioned repair, not silent overwrite.

## Actually committed supported statements

Sources: original archived SEC companyfacts/submissions plus independently matched primary GFS filings. Registry `tmp/quant-ifrs-basis-20261031-v1/` reverified507source observations/16accessions/75references and67primary inline-XBRL matches.106actual-quarter observations include89exact-frame-proved and17unframed observations; the latter stay blocked. These source counts are not statement/import counts.

Native scoped parser output has331records:327IFRSfacts and4ancillaryDEIordinary-share observations. Only IFRSfinancial values can fill financial fields. The raw bundle remains file evidence and was **not** inserted into `stock_sec_fact`.

Actual INSERT-only standard append:

| Issuer | New statements |
| --- | ---: |
| AZN | 9 |
| CCEP | 3 |
| FER | 3 |
| GFS | 16 |
| TRI | 4 |
| Total | 35 |

All244non-null selected financial fields were checked against exact source values, units, periods, accession/filing/form and original object. These were five previously empty standard scopes. Before commitment, a full-table65,216-row backup/preimage and source/code hashes were pinned; real InnoDB partial-insertion failure and full-insertion rollback probes both restored the exact old state. The final plain INSERT transaction verified all35new rows and unchanged prior rows, then committed.

- Plan: `tmp/quant-ifrs-standard-append-plan-20261031-v1/manifest.json`.
- ManifestSHA256: `9d409bc5af17329f6f3a33f44e8e85ba250366221215abb8aff206c83767b0d5`.
- Prior standard preimage: `b54d6884d3a34d2082f5e6326cbca6aeb4e605843a836e4b3a75c7f12a636a84`.
- Actual DB totals: standards**65,251**, raw facts**706,039**unchanged.
- Receipts: `verify-partial-rollback.json`, `verify-rollback.json`, `apply.json`; apply committed35/rawWrites0.
- Actual production MyBatis as-of lookup check:260comparisons,144non-null reads and116matching unavailable reads, all source-field comparisons passed,0DBwrites. Receipt `db-model-lookup-check.json`.

Do **not** reapply the append plan: its scopes are no longer empty. Do not source an old full-table backup over the current state. Native source receipts/mappedAt are actual current processing time, not historical arrival proof. The standards contain their selected original evidence; source files/manifest must remain available because the raw DB was deliberately not bulk-refilled.

Preparation initially stopped before DB writes on two representation assumptions: padded CIKs versus integer CIKs, and the parser bundle's four ancillaryDEIshare rows. Source-only normalization/whitelisting was corrected with tests; malformed/wrong identifiers and non-whitelisted tags still fail closed. No financial taxonomy or source-match guard was removed.

## Automatic collection checked and continued

Existing app automation `market-data-hourly-guard` remains ACTIVE at15:10KSTdaily. Its durable prompt now records the actual IFRS append, exact manifest, remaining limits and negative accuracy result, and directs further work toward consistent historical training inputs/new periods rather than retuning the same20days. No duplicate schedule was installed.

Actual manual smoke: `data/quant-daily/20261031T081246Z-b5645c18/result.json`, completed with duplicate collection skipped. Yahoo batch49unchanged, RUNNING0, last24hAPIerrors0, existing V9cohorts30/101/498unchanged, all629pending and0mature outcomes. First20Dcohort entryNovember2close/exitDecember1close. The new daily schedule was set after today's scheduled time, so this proves configuration/manual execution—not that a future scheduled run has already fired. LocalPC/Codex/project/DB availability remains required.

After the actual35-row append and the additional rolling experiment, a final manual smoke also completed: `data/quant-daily/20261031T085441Z-e492322c/result.json`. Duplicate collection stayed skipped; all629pending/0mature remain, Yahoo batch49/RUNNING0, raw706039/standards65251 unchanged. The automation addendum now records both completed experiments and directs work to pre-panel history/new periods, not repeating the same20days.

## Verification and remaining accuracy work

- Final reviewed production tree: Maven278total,251executed successfully,27skipped,0failures/errors;15.391seconds. `tmp/quant-ifrs-maven-reviewed-final-20261031.log`.
- Python31+91+117=239passed. Operator standalone:35statements/244fields,11financial corruption rejections plus CIK/bundle/source-membership checks. Real partial/full rollback and actual MyBatis lookup checks passed. Separate rolling tests cover strict maturity/equal-day exclusion, key replacement, recovered-name addition, unchanged frozen source, duplicate rejection and all-factor change detection.
- One independent scoped review found twoImportant issues (same-key sparse replacement; annual fiscal-year/range validator), both reproduced and fixed. No remaining scopedCritical/Important findings. Unrelated prior audit/UI changes, enablingADR/FX and a future scheduled-fire guarantee were not declared verified.

The next accuracy work is **pre-panel** training-feature reconstruction and additional free, time-aligned periods/official historical rosters, with a pre-separated purge/embargo-aware evaluation and untouched future observations. The first comparison froze training; the additional rolling experiment repairs the recovered panels consistently but cannot settle complete historical/PIT coverage. Historical analyst/news missingness and unframed/noncalendar foreign periods remain gaps; they must not be filled with invented zero/neutral data. Do not keep retuning these already inspected20days. The goal remains lower real20Derror, not more source rows or a retrospective fit.
