# Complete-window recovery: supporting audit notes

Historical price-only checkpoint below. Subsequent TEAM-input work now runs the existing V2–V9
formulas for3full101-member dates, file-only and research-not-PIT. Latest results and limitations:
[TEAM model recovery](C:/dev/codex-demo/docs/quant-team-model-recovery-results-2026-10-18.md).
The older portable HTML remains the earlier price-only report, not the new Quant results.

Primary reader artifact: `output/quant-complete-windows-20261018/report.html`.
Canonical report input: `output/quant-complete-windows-20261018/artifact.json`.
This document is a supporting technical checkpoint, not another report surface.

## Status and decisions

**PARTIAL_PRICE_RECOVERY; EXISTING_QUANT_FULL_POPULATION_BACKTEST_STILL_BLOCKED.**

User requested more free historical prices/membership and verification of complete windows without
questions. Work stayed on main, preserving all unrelated dirty files; no Git staging/commit/push,
deployment, DB writes, model replay or web-server restart occurred.

Root-cause inventory found all2,426 missing V9 member/date keys also lacked a prediction; the
`primary_gap` column deliberately prioritizes missing predictions over overlapping price/outcome
gaps. It does not mean all2,426 have the same root cause. Recent Nasdaq Jan30/Feb27/Mar27 gaps
were TEAM, with no canonical candles or historical signal snapshots. Many former constituents
had valid archived provider prices that had never entered the canonical price table.

Ruling: validate all-member price-only equal-weight data/accounting baselines separately rather
than inserting neutral or synthetic Quant predictions. Cost: Quant performance verification
remains blocked even where prices are complete. No user model acceptance criteria were relaxed.

Ruling: keep existing source datasets frozen; stage additional closes in files only. Require every
canonical/provider overlap to match within relative1e-5 before adding missing dates; canonical
values always win. Empty canonical history may use exact-symbol archived provider history as
research-only evidence. A reused ticker's current company is not substituted for another symbol.

## Actual saved results

- `scripts/quant_complete_window_gaps.sql` exported original missing keys to
  `tmp/quant-complete-window-gaps-20261018.tsv`.
- Main staging code: `scripts/quant_complete_windows.py`;9 fail-closed tests in
  `src/test/python/test_quant_complete_windows.py` (observed RED then GREEN).
- 44symbols /32,114 additional close observations, within2023-04-28–2026-04-28, are staged in
  `tmp/quant-complete-windows-recovered-20261018/additional-closes.csv`. These are not32,114
  newly fetched observations or price rows all used in final complete periods. No DB import.
- Sources554 archived Yahoo files passed full identity/OHLC parsing and hash/path checks.
  HUBB/WBD raw files failed OHLC validation; valid existing canonical data were retained instead.
  AVB/PSKY/EQR/EA incoming data lacked any overlap with existing canonical coverage, so merging
  was rejected and existing canonical data retained. No rebasing or interpolation occurred.
- Nasdaq10 full-price dates out of37, S&P0/37; both Quant-V9 full-forecast dates0/37.
  Nasdaq full-price decision dates:2025-07-31,08-29,09-30,10-31,11-28,12-31;
  2026-01-30,02-27,03-13,03-27. Populations101 except2025-10-31 temporary102security set.
  These counts come from community history, not a certified index universe.
- Nonoverlap policy selects6 decisions in chronological order:2025-07-31,08-29,09-30,10-31,
  12-31,2026-02-27. No cohort overlaps, no selecting dates based on realized return.
- All-member equal-weight price baseline: gross1.2158843180%, net0.0089148090%, same-window
  FRED Nasdaq price benchmark6.2009299805%; cost10bps per side, no return while in cash gaps.
  This is not the existing Quant strategy or total-return backtest. Annualized metrics from the
  generic evaluator are preserved in raw JSON but not promoted as meaningful investment evidence
  from6 sparse periods. Corporate distributions and terminal settlement remain outside this baseline.
- Independent Java `scripts/QuantCompleteWindowCheck.java` checked all complete holdings against
  exact full exported membership sets, rederived each return using BigDecimal endpoints and used
  the unchanged `ExpectedReturnPortfolioEvaluator(10000)` to include all members, not top20.
  Python and Java periodCount/gross/net/benchmark match within1e-9.
- FRED calendar vectors require all21 stock observations and positive benchmark levels at every
  session. A separate provider AAPL date vector exactly matches each tested window. This is not
  independent official exchange calendar certification. Missing Nasdaq2026-10-16 level remains
  an explicit calendar slot and does not invalidate unrelated earlier windows or become zero.

Initial raw-only profiling was incomplete because raw EA was absent and WBD failed validation.
Preserved artifact `tmp/quant-complete-windows-20261018/` is superseded by the recovered directory;
it is not the final report. No artifact was deleted or silently overwritten.

## Genuinely new free source acquisitions

BNY official continuity notice and Yahoo BNY history were newly downloaded with HTTP200:

- Notice URL: https://www.bny.com/corporate/global/en/about-us/newsroom/press-release/bny-announces-planned-change-of-stock-ticker-symbol-to-bny-130465.html
  SHA256 `83067962a04ab76fe3c473e9940fbf2c10a083ad65285ce115237368fcd450f8`.
- Chart URL and retrieval instants in `tmp/quant-complete-window-new-prices-20261018/manifest.json`;
  raw SHA256 `5d6faabf2b425a73547f3cda47755cd9562cc86b3456ebb776d456f3f5f4a491`.
- Chart1,707observations2020-01-02–2026-10-16;752within target research price span. Price parser
  checked symbolBNY/USD/EQUITY/local-exchange dates/OHLC. It never substitutes adjusted-close.
- Official May11 notice says BK common stock would trade asBNY onMay21, with unchanged capital
  structure/CUSIPs/rights. Preferred shares are mentioned separately and were not confused with
  common shares. This is later retrospective identity evidence, not a past forecast input.
- `mapped-BK-closes-candidate.csv` SHA256
  `238b39df8080376fe7d21792295b99c0e7fdb0a2303cdbd8ded3b8935e6c48c0`.
  Candidate only; no automatic alias/import or claim that historical execution basis is certified.
- Acquisition/verifier: `scripts/acquire_quant_window_prices.py`; `--verify` is offline.

Independent membership worker saved3 additional successful public Nasdaq responses plus1 error
body, separately distinguished. Full history roster count remains0; two official event groups
match the reconstruction and the three recent export rosters each contain101unique symbols.
Read `docs/quant-complete-window-membership-evidence-2026-10-18.md`. Restricted/login data and
the explicitly unsuitable 871(m) source were not integrated. Public readability is not a free
redistribution or production-data license; original corpora were not published.

Additional bounded web checks: official Hologic completion/issuer acquisition page states private
on2026-04-07; official Devon completion page states Coterra merger2026-05-07. Nasdaq historical
pages for CTRA/HOLX redirected to generic market activity and supplied no daily history. No
shareholder recovery or conditional entitlement was inferred as a daily price. These checks do
not close the remaining CTRA/HOLX gaps. Recent S&P full-price gaps after staging remain BK/CTRA/HOLX,
and also DAY for2026-01-30. BK candidate is deliberately not silently merged into that count.

## Verification and bounded review

- Python full suite43/43 passes,1.713s, `tmp/quant-complete-window-python-suite-20261018.log`.
- Additional price verification after HTML entity normalization passed, membership offline hashes
  and event/roster consistency verification passed. Missing quote dates are not forward-filled.
- New Java helper compiled and ran successfully without starting Spring/DB; only existing product
  evaluator code was used. No product Java code changed; reuse the preceding200/200 Maven test
  evidence rather than rerunning it for source/report changes.
- Scoped fresh reviewer found no Critical/Important issue in main staging code and9tests. It
  explicitly limited conclusions to supplied-calendar reconstructed-population price coverage.
- Frozen baseline/operating macro/outcome invariants remain byte-identical to
  `tmp/quant-macro-before-20261018.tsv`; after file
  `tmp/quant-complete-window-after-invariants-20261018.tsv`. No DB writes in this step.
- Report packaging validated the canonical artifact and produced self-contained HTML. Receipt
  `tmp/quant-complete-window-report-delivery-20261018.log`: validation/packagepassed,
  verification **structural_only**. No compatible headless Chromium was available, so viewport,
  source-dialog interaction, light/dark browser QA and chart SVG extraction were not performed.
  Semantic fallback and exact embedded artifact passed. No browser was installed. Do not claim
  interactive/visual QA or browser testing passed.

Report audience: product stakeholders; HTML local-file delivery in Codex. Structure maps title,
Executive Summary, definitions/findings, recommendations, open questions and limitations.
Chart contract: comparison/ranking bar, four same-unit discrete counts (price/Quant by index),
denominator37per index, no trend/causality. Single-root native styling, xlabels/non-color identity,
no redundant legend, exact0/10counts and nearby interpretation. No income-return trend plotted:
only6 sparse noncontiguous holding periods and an unproven universe, not a continuous equity history.
SQL provenance in source metadata is the actual export SQL; subsequent transformations are the
named Python script. Raw local absolute paths/credentials are not embedded in the portable report.

## Next safe work

1. Recover historic TEAM predictor inputs and generate an explicitly separate reconstructed model
   version with chronological training cutoffs; do not copy another company's forecast or fill50%.
2. Compare BNY candidate against an independent historical source and confirm symbol/CUSIP chain
   before canonical alias import; acquire CTRA/HOLX dated history and relevant event settlement.
3. Acquire a dated official Nasdaq baseline plus complete change chain with usable rights; retain
   publication timestamps separately from effective dates. Existing event checks are not a substitute.
4. Only then recompute affected model/window stages in a new namespace; don't replay prior entire
   models or modify already verified research namespaces just to resume work.
