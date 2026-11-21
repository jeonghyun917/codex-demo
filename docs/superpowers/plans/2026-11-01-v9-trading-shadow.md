# V9 Trading Shadow Implementation Plan

Goal: Compare archived V9 rankings with probability rankings under identical
15% target, 5% stop, inclusive 20-session, next-open, cash-inclusive execution.
Architecture: Add offline scripts only; reuse the frozen S&P account engine.
No production model, DB, paper capture, or UI writes. No outcome-based tuning.
Tech stack: Python, pandas, existing validated price/account artifacts.

- [ ] Test exact-key joins, no stale forecast carry, ranking/filter order and exclusions.
- [ ] Freeze inputs and run paired common-universe accounts, then independent reconciliation.
- [ ] Document coverage, source limitations, results and data-to-product roadmap.
- [ ] Set a quiet daily research watcher without duplicating collection or production writes.

Primary source: quant-ifrs-rolling-training-20261031-v2/result.json.
This is reconstructed NASDAQ100 V9 with financial-standard/IFRS repairs and
rolling repaired prior panels, NOT the latest EPS recovery cohort.
Latest EPS V9 source is 2017-2020 and does not overlap the S&P dataset.
Do not claim that the archived V9 has proven improved forecasting performance.

Primary paired experiment: every available archived V9 date intersected by
(symbol,date) with frozen S&P eligible probabilities. Full S&P volatility 75th
percentile is retained. Top ceil(20%) is calculated within the common universe
for both arms. No extrapolation to all S&P500 or daily forecasts.
Primary: probability versus repaired V9 absolute return.
Secondary: repaired V9 excess return and pre-IFRS V9 absolute return.
Context only: full S&P probability, both same sparse dates and every date.
All use the same continuous account dates through 20 sessions after last signal.
Costs: 10bp and 20bp per side. Initial cash100k, max10 positions, max2 new/day,
10% prior NAV, whole shares. No positive-score cutoff, no forward-fill.
All periods previously inspected; not an untouched test. No model promotion.
