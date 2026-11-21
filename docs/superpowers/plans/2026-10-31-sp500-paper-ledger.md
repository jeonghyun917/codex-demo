# S&P500 fixed-filter paper ledger

Scope: local research ledger, no broker orders, production DB changes, V9 changes,
website changes or automatic scheduler installation. Existing user work stays untouched.

1. Add tests for session cutoffs (including DST/early close), stale inputs,
   immutable records, filter equivalence and realized TP/SL/20-session accounting.
2. Add scripts/sp500_paper_ledger.py with init, capture, evaluate and status commands.
   Reuse quant_forward_validation timestamp/seal/calendar helpers and existing
   sp500 scoring, filters and account engine. Bind protocol, calendar, exclusions,
   source code and input file hashes. No CLI backdated capture timestamp.
3. Capture full eligible cross-section plus filtered ranks and training audit only
   after the latest session close and strictly before the next open. Require dataset
   latest session to equal anchor and verify input hashes before and after scoring.
   Exclusive file creation prevents overwrites; account evaluations are new versions.
4. Replay only captured decisions using approved OHLC, report actual fills, predicted
   probabilities, pending positions, missed captures, cash-inclusive NAV and drawdown.
   Preserve old evaluations and report price-version changes, never rewrite forecasts.
5. Run focused tests and regression suite, initialize a real empty ledger, execute
   readiness/capture preflight. Do not manufacture forecasts from stale datasets.

The existing V9 evaluator is not reused for payoff logic: it uses next close / 20D
close returns rather than next open / +15% TP / -5% SL / inclusive 20-session exits.
Local hashes detect accidental corruption, not tampering by a local administrator.
