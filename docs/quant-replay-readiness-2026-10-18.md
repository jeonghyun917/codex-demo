# Prediction/backtest replay readiness — 2026-10-18

Follow-through update, 00:55:52 KST: the separately authorized maturity-queue fix and bounded corrected-outcome drain have now run. The eligible queue is zero; corrected outcomes increased from 6,885 to 119,371, including historical 5D/20D/60D counts 19,433/19,433/19,432. CPB on 2026-03-27 remains missing 60D because only 52 future closes exist, not the required 61. Legacy results were unchanged. Detailed implementation/test/SQL evidence is in `tmp/quant-mature-outcome-drain-report-20261018.md`. The investigation and baseline counts below describe the **pre-drain** state, not a claim that the queue remains unfixed. Replay remains research-only; strict PIT limitations below are unchanged.

Read-only local DB/source inspection, early 2026-10-18 KST. Counts can change during other batch work. No Java, schema, model, or database state was changed by this workstream. No Maven run.

## Conclusion

The immediate correctable bottleneck is outcome backfill starvation, not a need to relabel reconstructed predictions as point-in-time. All 19,433 historical reconstructed signals currently have **zero corrected v2 outcomes**. The default pending query repeatedly selects the newest 5,000 snapshots, including immature horizons, so historical snapshots never reach the outcome generator.

After fixing the pending selection and draining mature outcomes, existing corrected V2→V9 historical methods can generate a **research reconstruction**. Corrected community membership and FRED benchmark data improve the population/label inputs but do not establish historical feature availability or convert research reconstructions into recorded-at-decision predictions. Keep the strict evaluation gate distinct and fail closed.

## Observed prediction/signal provenance

| Dataset | Rows and dates | Recorded timing |
| --- | --- | --- |
| Historical reconstructed signal snapshots | 19,433; signal dates 2023-03-31–2026-03-27; version `quant-v2-hist` | Calculated 2026-06-16–2026-06-27; zero calculated by signal-day end |
| Older LIVE signals | 25,285; 2026-06-14–2026-10-17; `quant-v2` | Same-calendar-day records, but older logic/data policies |
| Corrected LIVE signals | 1,036 across 2026-10-17 and 2026-10-18; `q3-price-20261017` | Same-calendar-day records; none has a matured 20/60-day outcome yet |
| Legacy V9 historical predictions | 69,951: 65,052 `...SEC_PIT` plus 4,899 `...CURRENT_PROXY_SHARES`; 2023-04-28–2026-06-25 | All calculated 2026-06-27; zero by signal-day end |
| Legacy V9 LIVE predictions | 75,939: 70,224 SEC-tagged, 5,640 current-proxy, 75 alias-share; 2026-06-27–2026-10-17 | Same-calendar-day, but legacy uncorrected model |
| Corrected V9 predictions | 2,000, exclusively LIVE; 1,000 each on October 17/18 | Zero historical/research rows; 932 SEC-source, 66 proxy, 2 alias per day |

The corrected model is `EXPECTED_RETURN_V9_CORRECTED_20261017`; older `EXPECTED_RETURN_V9` rows must not be silently substituted for it. Across the currently joinable legacy V9 LIVE predictions and corrected v2 outcomes, 8,452 joined rows were recorded before their exits, none after exit. This is **not** an independent model validation: joins can repeat predictions across index context, the version is uncorrected, and this is not complete feature/training/calibration availability evidence.

Other legacy historical model counts: V1 53,907; historical V2 57,154; V3 54,004; V4 66,232; V5/V6/V7 66,483 each; V8 70,287. They were created after their historical signal dates. A `WALK_FORWARD` or `SEC_PIT` source string does not override that fact or validate their old implementation.

## Outcome starvation: specific mechanism

Observed `stock_backtest_result_v2`:

- 5-day: 3,961 rows, signal dates 2026-08-07–2026-09-22.
- 20-day: 2,924 rows, signal dates 2026-08-07–2026-08-24.
- 60-day: zero rows.
- Historical reconstructed snapshots with zero results: 19,433/19,433.
- LIVE snapshots with fewer than three results: 26,321/26,321; 22,360 have none.

`StockBacktestMapper.xml:findSnapshotsNeedingResults` selects snapshots with fewer than three results, stale results, or missing institution scores. It orders by `signal_date DESC, symbol` and takes `LIMIT`. It does **not** require that the missing horizon has enough future trading candles before inclusion. The actual current top 5,000 span 2026-08-09–2026-10-18 and exclude all historical reconstructed dates. `StockSignalRefreshService` always invokes `refreshCompletedResults(5_000)`, so repeating it does not drain the old queue.

`StockBacktestService.refreshCompletedResults` correctly requires `horizon+1` future closes, enters at the next close, and exits N later closes. That means immature latest snapshots can remain pending for weeks while continuing to occupy the same newest-first limit. A maturity-aware eligible-work query should select snapshots for which **some missing/stale horizon is currently computable**, exclude already-up-to-date horizon work, and allow bounded continuation. Do not weaken the N+1-close contract to fill the queue.

`HistoricalBacktestSeedService.seedMonthly` already calls `refreshCompletedResults(max(50_000, snapshots*3))`, which would cover today's 45,754-snapshot queue, but reseeding all features solely to get this side effect is unnecessary and can overwrite research snapshots. Prefer an explicit mature-outcome drain after the query fix.

## Feature evidence and limitations

All 19,433 reconstructed signals have valid source-freshness JSON. Their saved latest-candle dates and non-null annual/quarter SEC filed dates never exceed signal date. JSON null must be excluded from comparisons: lexical `"null" > "2023-..."` would falsely flag missing filings as future data.

The JSON records candle date, annual/quarter filed/end dates, analyst recommendation period, institution report quarter and a fixed 45-day availability lag. 1,562 reconstructed rows contain non-null recommendation-period fields. `HistoricalBacktestSeedMapper.findRecommendationAsOf` checks only `period_date <= asOfDate`, not actual release/fetch availability. The 13F fixed lag is likewise not the actual filing/availability timestamp for each report, and current/revised source rows may have been fetched later. These are material reconstruction limitations.

`stock_market_snapshot` has 798,885 rows spanning 2021-06-21–2026-09-21, with zero recorded by snapshot-day end. `stock_macro_feature_snapshot` has 3,933 rows spanning 2021-06-22–2026-09-21, also rebuilt later, all currently labeled `FRED_MACRO_FEATURE_V2_PIT_STRICT`. Macro computation does read vintage snapshots; the source label alone does not prove the underlying vintage inventory is complete or independently verified. `stock_metric_snapshot` has 5,612 records dated only 2026-05-24–2026-10-17; it cannot supply a historical 2023 valuation-feature archive. Historical seed explicitly excludes latest Finnhub metrics and lacks full historical shares.

The current seed looks up membership on the historical date but falls back to current members if no membership rows exist. Therefore dates outside verified membership coverage must be excluded or visibly treated as proxy research; corrected community membership is not blanket authorization for current-universe fallback.

## Available corrected replay pipeline

1. `StockPortfolioBacktestService.refreshExpectedReturnSnapshots` generates corrected V2 research snapshots from `researchResults`; V3–V7 have corresponding historical methods. Training rows require `signalDate < decisionDate` **and `exitTradeDate < decisionDate`**. Complete benchmark periods are attached by `CompleteBenchmarkReturn.compound`. Missing benchmark coverage remains missing rather than becoming zero.
2. `StockExpectedReturnV8Service.refreshHistorical` requires corrected V7 snapshots and floor-date macro features; the macro refresh reads vintage snapshots. Corrected FRED coverage must exist for the actual target dates.
3. `StockExpectedReturnV9Service.refreshHistorical` requires corrected V8 and floor-date market snapshots with positive market cap. It applies provenance shrinkage and stamps research sources, e.g. `RESEARCH_V9_SEC_SOURCE_CUTOFF_LT_YYYYMMDD`; this is not a LIVE source. The SEC-source string is not full historical input certification.
4. `StockExpectedReturnBatchRunner` orchestrates V2→V9 in that order, including macro refresh. `date-limit` selects the **latest** available target dates, so the default 80 may omit much of the desired historical sample. Bound it to the intended monthly/live date inventory rather than assuming it means years.

The strict evaluation dataset requires a prediction recorded on its decision calendar date, a LIVE prediction source, and membership provenance `OFFICIAL_PIT` or `LIVE_CAPTURE`. Community history and regenerated research rows intentionally do not satisfy that strict contract. Do not backdate `calculated_at`, rename research rows to LIVE, or relabel community sources as official to obtain nonzero counts.

## Concrete next execution sequence for the main workstream

First fix pending selection and invoke `StockBacktestService.refreshCompletedResults` in bounded eligible-work batches until the mature historical queue is drained. Verify new 5/20/60-day counts, entry/exit indexing, and complete benchmark label coverage. This independent investigation has not executed that mutation.

Then, **after compiling/verifying the main workstream's current sources**, this runner shape invokes the existing research pipeline without altering LIVE predictions:

```powershell
$cp = 'target/classes;' + (Get-Content tmp/quant-runtime-classpath.txt -Raw).Trim()
& 'C:/Program Files/Microsoft/jdk-21.0.11.10-hotspot/bin/java.exe' -cp $cp com.kingyurina.demo.CodexDemoApplication `
  --spring.profiles.active=mariadb --spring.main.web-application-type=none `
  --app.signal.refresh.enabled=false --app.signal.refresh.run-on-startup=false `
  --app.batch.expected-return.enabled=true --app.batch.expected-return.exit-on-complete=true `
  --app.batch.expected-return.index-codes=SP500,NASDAQ100,DOW30 `
  --app.batch.expected-return.result-limit=200000 --app.batch.expected-return.date-limit=1000 `
  --app.batch.expected-return.live-enabled=false `
  --app.batch.expected-return.v3-live-enabled=false --app.batch.expected-return.v4-live-enabled=false `
  --app.batch.expected-return.v5-live-enabled=false --app.batch.expected-return.v6-live-enabled=false `
  --app.batch.expected-return.v7-live-enabled=false --app.batch.expected-return.v8-live-enabled=false `
  --app.batch.expected-return.v9-live-enabled=false
```

This is a proposed command, not an executed or verified batch invocation. Use the main workstream's approved DB configuration and disable any unrelated enabled runners. The checked-in JAR is dated 2026-08-05 and is stale: do not use `run-historical-backtest-seed.cmd`'s old JAR as proof the corrected code ran. Existing classpath file is available, but compiled classes must be freshly verified by the main workstream.

For results, publish separate research diagnostics with all exclusions/coverage and retain strict evaluation as insufficient until actual eligible LIVE observations mature. A meaningful nonzero research sample is useful; it is not a reason to weaken the strict promotion gate.
