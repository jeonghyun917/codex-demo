# Fixed ranking stability comparison

User delegated parameter decisions. Research only; no production model promotion.

1. Add tests for causal EMA, score-band persistence, missing-session reset, deterministic ties and excluded securities.
2. Implement isolated research ranking policies: RAW, EMA5, BAND05, EMA5_BAND05. EMA alpha=1/3. Bands are anchored at each remaining highest score, width=.005 probability; prior-day order breaks within-band ties, then ticker. Keep top ceil(20% N).
3. Exclude FBIN, BBWI, DOC/PEAK, PSKY, AVB, EQR, VMRK entirely from training and candidates. Preserve input data. Record this retrospectively fixed research-universe limitation.
4. Refit the existing monthly expanding price-only model with the same weekly samples, maturity purges and calibration windows. No outcome-driven parameter search.
5. Compare three existing periods, fees .001/.002, unchanged +15%/-5%/20-session next-open whole-share accounting. Check CASH reference, return, MDD, cash allocation and trades.
6. For every policy and period run fixed seeds 0..4 with probability noise SD .0001 and .001 before smoothing/ranking; report range/std/worst MDD and trade Jaccard turnover. These are finite sensitivity probes, not statistical confidence bounds.
7. Reconcile cash, closed-trade PnL, barrier timing, exclusion and causal tests. Produce Korean report and complete CSV evidence. No claim of untouched holdout or globally optimal strategy.
