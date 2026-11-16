# Quant next-session review TOP20

## Delivered

- Quant first screen reads the existing DB-backed SP500_PAPER_WEB_V1 projection.
- Original +15% before -5% probability, maximum 20 trading sessions.
- Persisted original selected/interest flags, original rank order, maximum 20 rows.
- No V9 substitution, ranking changes, order generation, ledger writes, provider calls or DB publication on GET.
- Existing probability-top-20%, index MA50 and volatility75 filters remain unchanged.
- Fewer than 20 eligible symbols stay fewer than 20. No padding or older-day fallback.
- A decision expires at its recorded next-open instant. Missing, invalid or future-recorded snapshots fail closed.
- Other index tabs do not substitute the S&P500 strategy population.
- Historical baseline/10bp closed-trade bins are separate from predicted probabilities and forward paper results.
- Rounded probability-bin boundaries do not get an ambiguous historical-bin join.
- Frozen projection has no reference price: show missing rather than mix another date or price basis.

## Verification

Focused Maven suite, 27 tests, zero failures/errors:

`QuantBuyCandidatesServiceTest,QuantBuyCandidatesRenderTest,PaperLedgerViewServiceTest,HomeControllerForwardValidationTest,ForwardValidationPanelRenderTest,QuantDashboardThreeMonthHighContractTest,QuantReadIntegrityTest`

Run with `mvn.cmd "-Dmaven.repo.local=.m2/repository" "-Dtest=<above classes>" test`.

Playwright/Edge: actual DB-backed `/quant` HTTP 200 at 1440x1000 and 390x844; candidate section has no horizontal overflow; historical evidence disclosure works; no page JavaScript errors.

Screenshots: `tmp/quant-top20-1440.png`, `tmp/quant-top20-390.png`.

Existing port 8080 left alone. Updated app started at http://127.0.0.1:8081/quant with SQL initialization and signal refresh disabled. No collection batch was run.

## Current data limitation and next work

Observed latest original-model saved decision: 2026-10-30, entry planned 2026-11-02. It is expired and had no passing candidates. The TOP20 screen therefore correctly shows waiting, not a fabricated current shortlist.

The interface is ready; current recommendations still require a newly validated original-model decision and publication through the existing pipeline. Do not substitute the separate V9 forward cohorts. Preserve the paper protocol, avoid retroactive decisions, verify source timestamps and the before-next-open capture contract. Adding an immutable same-basis decision-date price to a future projection is separate follow-up work; do not backfill existing records.

No model, strategy, paper ledger, trading logic or Git commit/push was changed by this task. Existing unrelated dirty-tree edits were preserved.
