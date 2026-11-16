# Complete research-window recovery

**Goal:** Recover free missing history/membership evidence and evaluate earliest data-complete
20-session research portfolio windows without omitting constituents or changing frozen results.

**Architecture:** Diagnose exact missing keys first, then acquire only relevant public primary/provider
sources into a new evidence folder. Preserve hashes/time/source identity and corporate-action basis.
Stage recovered outcomes and membership corroboration separately from frozen namespaces. Run the
existing portfolio evaluator only on full-population periods, with visible research-not-PIT caveats.

**Constraints:** User explicitly requests immediate execution without questions. Use main, preserve
dirty files, no paid data/accounts/license acceptance/access bypass, no Git writes or deployment.
No external calls on page GET. Never synthesize forecasts, zero recovery or missing price endpoints.
If evidence cannot close a window, identify the exact remaining gap rather than certify it.

- [x] Export per-date/per-symbol missing prediction/outcome/window/price diagnostics with
  `scripts/quant_complete_window_gaps.sql`. Rank by number of missing keys, never by realized return.
- [x] Search/download bounded free price and official membership evidence for the closest windows.
  Keep exact raw files/URLs/retrieval times/hashes and reject ticker identity/adjustment ambiguity.
- [x] Add tested, fail-closed offline staging/validation only as required by observed gaps. No
  overwrite of already verified research inputs or operating universe.
- [x] Validate full supplied membership, all 21 benchmark/stock sessions, input/exit timing and prediction availability;
  evaluate nonoverlapping cash-to-cash portfolios at 10bps per side only when complete.
- [x] Independently cross-check resulting coverage/accounting, run focused tests and one relevant
  suite if product logic changes. Save report, source inventory and resume checkpoint.

Review focus: date aliases versus permanent security identity; corporate-action price basis;
full index population versus top20 selection; future publications versus effective dates;
incomplete periods versus zero returns. Existing strict promotion gate is unchanged.

## Execution ledger / limitations

Finished bounded pass about17:22 KST. New price/official identity sources2 responses, BNY1707
observations (752mapped-BK candidates). Additional official membership sources3responses, but
complete official historical rosters0. Staged existing free observations44symbols/32114closes,
without DB writes. Nasdaq10price-complete dates /6nonoverlap equal-weight price baseline periods;
existing Quant full-forecast periods0, S&P full-price periods0. Missing TEAM forecasts are not
invented. The price-only baseline is a data/accounting control, not user Quant strategy completion.

Ruling: preserve frozen DB data and stage recovery offline; existing canonical prices win and
all provider overlap must match. No new worktree/commit/approval gate, per explicit user preference.
Ruling: isolate price-only baseline from unavailable Quant model backtest; do not promote community
membership or missing predictor inputs. Follow-up remains needed for full requested Quant outcome.

Verification:9new fail-closed tests RED→GREEN; Python43/43 passed; Java helper independently
matches6period accounting. Fresh scoped review noCritical/Important. Product Java unchanged,
reuse previous200Maven tests. Report structural validation passed; no compatible headless browser,
so visual/browser QA not claimed. No DB/model/Git writes. Supporting notes/checkpoint saved.
