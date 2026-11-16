# Free-data correction and reconstructed Quant replay — 2026-10-18

> 후속 작업(10:05 KST): 무료 공식 자료 276개를 추가 확보했고 첫 접속 지연을
> 70.961초에서 5.734초로 단축했습니다. 아래 최초 실행 기록의 지연 미해결 문구는 당시 상태입니다.
> 최신 결과: [무료 자료·첫 접속 수정 보고서](quant-followup-first-request-fix-2026-10-18.md).

## 재개 작업 결과 요약

- S&P와 나스닥의 V2–V9 재계산을 모두 끝냈습니다. 저장된 S&P V2는 재실행하지 않았습니다.
- 수정된 과거 수익률 자료는 119,393행입니다. 가격·날짜·수익률 산술 검증과 모델 검증을 구분합니다.
- 최신 V9의 20거래일 연구 결과는 아래와 같습니다. 이는 실제 당시 생성된 예측이 아니라 과거 재구성 결과입니다.

| 연구 대상 | 성숙한 전체 대상 | 사용 가능 | 자료 충족률 | 방향 일치율 | 평균 일별 순위 IC | Brier |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| S&P | 41,247 | 38,318 | 92.90% | 50.20% | 0.021818 | 0.249939 |
| 나스닥 | 8,542 | 7,575 | 88.68% | 50.35% | 0.005583 | 0.252043 |

방향 일치율은 초과수익률의 부호 일치 비율이며 수익률이나 투자 성공률이 아닙니다.
Brier는 작을수록 좋고, 항상 50%를 예측하는 기준값은 0.25입니다. S&P의 차이는 매우 작고
나스닥은 그 기준보다 나쁩니다. 이 결과로 실전 투자 우위가 입증되었다고 결론내리지 않습니다.

기존의 엄격한 실전 검증은 **INSUFFICIENT_DATA(자료 부족)**입니다. 평가 대상 33,983건 중
검증 가능한 PIT 자료는 0건, 유효 검증 기간은 0개월입니다. 판정 기준을 완화하거나 연구 결과를
실전 성능으로 승격하지 않았습니다. 현재 변경 코드의 전체 Java 검증은 **188/188 통과**했습니다.

두 연구 대상의 총 16개 모델에 대해 별도 SQL의 표본 수·Brier·평균 절대오차·방향 일치율이
Java 결과와 허용 오차 내 일치했습니다. 수익률 119,393행의 잘못된 가격·날짜·산술 결과는
각각 0건입니다. 서버는 8080에서 실행 중입니다. 다만 재시작 직후 `/quant` 첫 요청의
45초 초과 지연은 남아 있고, 이후 재접속은 HTTP 200 / 0.56초였습니다. 이 지연은 미해결입니다.

## Scope and interpretation

This follow-through corrects acquired community membership data against acquired official notices,
imports it separately from the operating universes, backfills matured corrected outcomes, and replays
the existing corrected expected-return models. It does not certify historical point-in-time (PIT)
features, total returns, or profitable live forecasts. No paid data, external page-request calls,
retroactive LIVE labels, backdated model timestamps, production-universe replacements, or Git push.

## Membership corrections

The original downloaded files remain unchanged. `scripts/correct_quant_membership.py` produces a
new hash-verified dataset and per-change evidence manifest in `tmp/free-quant-corrected-2026-10-18/`.

| Security | Restored membership dates (inclusive) | Reason |
| --- | --- | --- |
| GPS | 2022-02-02 | Community removal was earlier than official effective open |
| MBC | 2022-12-15–18 | Missing temporary spin-off membership before official removal |
| VNO | 2023-01-04 | Community removal was early |
| DXC | 2023-10-02 | Community removal was early |
| VFC | 2024-04-01–02 | Community removal was early |
| XRAY | 2024-04-02 | Community removal was early |

Six bounded overrides repair seven mismatched official transitions, affecting ten security-calendar
days. All 135 comparable official S&P 500 actions within coverage now match (previously 128).
Four later actions remain outside the acquired S&P community baseline, whose coverage stops at
2026-08-18. The script does not extrapolate that baseline to October. Nasdaq's unchanged community
baseline matches all 56 checked annual-announcement actions from 2021–2025; that is not a complete
official event-chain certification.

## Separate research datasets actually imported

| Namespace | Membership keys | Decision dates | Copied existing market context | Benchmark rows |
| --- | ---: | ---: | ---: | ---: |
| `SP500_FREE_20261018` | 41,247 | 82 | 30,715 | 2,514 |
| `NASDAQ100_FREE_20261018` | 8,845 | 87 | 6,424 | 2,514 |

Membership is expanded only on actual stored signal decision dates, with inclusive interval bounds.
Positive membership intervals form a new complete research namespace, not an overlay on today's
survivors. Raw intervals and source/publication/effective-time evidence are preserved by the manifest.
Operating `SP500` / `NASDAQ100` / `DOW30` memberships are unchanged.

Benchmark levels are the acquired [FRED S&P 500](https://fred.stlouisfed.org/series/SP500) and
[FRED Nasdaq-100](https://fred.stlouisfed.org/series/NASDAQ100) close series. These are price-index
benchmarks, not dividend-inclusive total returns. Return units are percentage points:
`100 * (level_t / level_previous_session - 1)`.

The calendar combines nonmissing S&P index dates with independently stored canonical equity dates
having at least 100 symbols, so a FRED source hole is not silently deleted from its own calendar.
Missing levels invalidate that session and the next return: no zero filling, interpolation, or gap
bridging. Nasdaq's last acquired observation is October 15; October 16 remains missing. The first
return in each series is also missing by construction.

V9 requires market-cap provenance context. Only already stored, same-symbol/same-date context is
copied for actual research members. Every price, share/cap source, value and calculated timestamp is
retained, including `BASIS_UNVERIFIED` flags; no historical cap is invented or provenance upgraded.
All copied close/cap/source/timestamp comparisons with source rows were identical. Missing context
remains missing. Dataset registry manifests include input hashes, boundaries, correction evidence,
session dates, and explicit research-only/promotion-disabled claims. Repeat import verifies counts,
including market context, and returns `ALREADY_IMPORTED` instead of overwriting data.

## Corrected outcome queue and backfill

Root cause: the old queue returned recent predictions repeatedly whenever any horizon remained
missing. Unmatured 60-day predictions occupied the limit and starved older missing outcomes.
The query now tests each missing/stale horizon against the actual required future close count:
6 / 21 / 61 closes for 5 / 20 / 60 close-to-close intervals. Indexed existence probes replace
unbounded row counting; current complete rows are not requeued.

The bounded drain finished in 17 loops with no eligible work left:

| Population | 5D labels | 20D labels | 60D labels |
| --- | ---: | ---: | ---: |
| Historical reconstruction | 19,433 | 19,433 | 19,432 |
| Stored LIVE signals | 24,198 | 23,117 | 13,758 |

Corrected outcomes increased from 6,885 to 119,371 (+112,486). The legacy table remains at 87,105
rows with unchanged checksum aggregates. All corrected stored returns match their entry/exit
prices within 1e-6 percentage points; no nonpositive endpoints or invalid date ordering.
One historical 60D outcome (CPB, signal 2026-03-27) has only 52 later closes instead of 61 and is
correctly not synthesized. Other recent immature outcomes are deliberately not treated as errors.

### CPB gap recovered before the final replay

Follow-up traced the remaining CPB gap to stale collection: canonical history stopped on June 11,
although the acquired and freshly checked free Yahoo chart contained later dates. All 1,260 stored
OHLCV rows matched the fresh archive exactly at database precision (including volume). The explicit
`QuantAppendCompatibleCandles` dry-run verified the complete overlap, then the apply transaction
appended **88 dates (June 12–October 16)** through the ordinary cache's source/overlap safeguards.
No earlier OHLCV values changed, no older dates were backfilled, and the archive SHA and retrieval
metadata were recorded as provider evidence, not official corporate-action or PIT proof.

The follow-up outcome drain made 24 upserts, adding 22 rows: final corrected count **119,393**
(+112,508 from the starting 6,885), with all three historical horizons now **19,433 each**. CPB's
March 27 decision enters March 30 at $22.18 and exits June 25 at $22.35 for a **0.766456% price
return** over 60 intervals. All 119,393 stored returns still pass the 1e-6 percentage-point arithmetic
check. Raw audit: `tmp/cpb-gap-20261018/cpb-gap-audit.json`; apply log:
`tmp/quant-cpb-append-apply-20261018.log`.

The initial, still-uncommitted model replay was explicitly stopped and rolled back before this
append. Zero research predictions survived that rollback. The final replay starts only after CPB
prices and outcomes are complete, so stages do not mix different input cuts.

## Replay and validation

At the earlier user-requested shutdown, S&P V2 had committed 107,412 predictions and the unfinished
V3 transaction was rolled back. On resuming at 08:26 KST, saved counts and hashes matched the
checkpoint and no transaction was active. The saved V2 was retained, not recomputed.

S&P V3–V9 finished at 09:08:31 KST in 2,523.7 seconds. V2–V8 each contain 107,412 predictions;
V9 contains 106,946, over 81 decision dates. Nasdaq V2–V9 finished in 396.5 seconds: V2–V8 each
contain 21,164 predictions and V9 contains 21,108. Counts include 5/20/60D horizons; the descriptive
metrics below assess 20D only. All stages committed successfully; no replay remains in progress.

### S&P descriptive reconstruction

S&P has 41,247 matured membership targets and no immature targets in the acquired coverage period.
Missing members remain in the denominator. All models use actual calculation timestamps.

| Model | Usable 20D | Coverage | Mean daily rank IC | Brier (lower is better) |
| --- | ---: | ---: | ---: | ---: |
| V2 | 38,473 | 93.27% | 0.021554 | 0.250866 |
| V3 | 38,473 | 93.27% | 0.022312 | 0.252030 |
| V4 | 38,473 | 93.27% | 0.022254 | 0.252030 |
| V5 | 38,473 | 93.27% | 0.025347 | 0.251920 |
| V6 | 38,473 | 93.27% | 0.024616 | 0.250382 |
| V7 | 38,473 | 93.27% | 0.023763 | 0.249934 |
| V8 | 38,473 | 93.27% | 0.023763 | 0.249934 |
| V9 | 38,318 | 92.90% | 0.021818 | 0.249939 |

V9 direction agreement is 50.1957%; excess-return MAE is 6.622908 percentage points. Observed
P10–P90 interval coverage is only 34.3677%, not the nominal 80%, so its uncertainty interval must
not be described as well calibrated. The Brier advantage over constant 50% is about 0.000061,
not evidence of statistically reliable or executable profitability. All 38,318 usable predictions
were calculated after the historical decision dates. The first usable signal is 2023-05-31 and the
last is 2026-08-10; final included label ends 2026-09-09.

V9 exclusion reasons overlap: 2,913 missing predictions, 1,875 missing outcomes, 16 invalid holding
windows and 16 incomplete benchmarks. Distinct excluded targets: 2,929. Missing rows were not
silently removed from the target denominator or filled with synthetic prices or returns.

### Nasdaq descriptive reconstruction

Nasdaq's descriptive **20D** reconstruction has8,542matured membership targets and303immature
targets. All missing members remain in the denominator. Results are not LIVE or PIT evidence.

| Model | Usable20D | Coverage | Mean daily rankIC | Brier(lower is better) |
| --- | ---: | ---: | ---: | ---: |
| V2 | 7,594 | 88.90% | -0.024863 | 0.254509 |
| V3 | 7,594 | 88.90% | -0.009244 | 0.254943 |
| V4 | 7,594 | 88.90% | -0.009373 | 0.254931 |
| V5 | 7,594 | 88.90% | 0.007177 | 0.254661 |
| V6 | 7,594 | 88.90% | 0.006373 | 0.252913 |
| V7 | 7,594 | 88.90% | 0.005974 | 0.252025 |
| V8 | 7,594 | 88.90% | 0.005974 | 0.252025 |
| V9 | 7,575 | 88.68% | 0.005583 | 0.252043 |

V9 direction agreement is50.3498%, excess-return MAE8.557325percentage points. These observations
do not establish an investment edge. Every Brier value is worse than the constant50% probability
baseline of0.25 on the same binary excess-return target. No profitability, independent-trade count,
statistical-significance, or total-return claim follows from these overlapping monthly groups.
V7 andV8 coincide because strict-vintage macro evidence is absent in this namespace, so the
documented no-macro behavior is used rather than later revised macro data.

All 7,575 usable V9 predictions were calculated after their historical decision dates. The source
universe is community reconstruction; historical feature availability remains unproven. Nasdaq's
descriptive output does not mark the separate strict production gate as passed.

V9 exclusions overlap:960missing predictions,503missing outcomes,7invalid holding windows and
7incomplete benchmarks. Distinct excluded targets=967. Of503missing-outcome keys,481have no
stored historical signal snapshot and22have a signal but no completed20D outcome. Warm-up and
missing input evidence cannot be repaired by filling returns or substituting current constituents.

Independent SQL uses **FRED endpoint index levels**, unlike Java's compounded stored daily returns.
All8model usable counts, Brier, MAE and directional accuracy agree within explicit tolerances
(Brier1e-10, MAE1e-5percentage points, rounded direction0.0001percentage points). RankIC is covered
by the Java metric/regression tests, not independently recomputed by this SQL check.

The S&P-only independent audit also completed successfully in 220.38 seconds. All eight S&P
models matched, for a total of 16 matched model reports across both namespaces. Maximum S&P
Brier difference was 3.89e-15 and maximum MAE difference was 8.09e-10 percentage points. The
integrity query found 119,393 corrected outcomes, zero invalid endpoints, zero invalid date orders
and zero arithmetic mismatches. All research model provenance/cutoff/date/value flags are zero;
no P10 > P50 or P50 > P90 ordering failures exist. Legacy rows remain 87,105 with unchanged
last-calculated timestamp (2026-10-17 22:09:46) and return sum (126767.450705).

Artifacts:

- `tmp/quant-research-validation-20261018.json` — merged saved reports for both namespaces (16
  model reports) plus the strict production result; assembled without recomputing model metrics.
- `tmp/quant-research-validation-sp500-20261018.json` — S&P metrics, exclusions and strict result.
- `tmp/quant-research-validation-nasdaq-20261018.json` — all monthly/model metrics and exclusions.
- `scripts/quant_research_validation_checks.sql` — read-only integrity and independent numerical path.
- `scripts/Compare-QuantResearchDiagnostics.ps1` — exact count and tolerance checks, rejects nonfinite metrics.
- `tmp/quant-research-independent-nasdaq-keyed-20261018.tsv` and
  `tmp/quant-research-crosscheck-nasdaq-keyed-20261018.json` — completed keyed SQL comparison.
- `tmp/quant-research-independent-sp500-20261018.tsv` and
  `tmp/quant-research-crosscheck-sp500-20261018.json` — completed S&P comparison, `MATCHED`.
- `tmp/quant-research-final-counts-20261018.tsv` — committed model counts, legacy aggregates
  and quantile-order check.

### Unchanged strict production gate

The strict S&P baseline evaluation ran once after replay, at 09:12 KST: evaluation ID 5,
contract `SP500_EXCESS_20D_V2_CORRECTED_20261017`, baseline `EXPECTED_RETURN_V9_CORRECTED_20261017`.
Status and decision are **INSUFFICIENT_DATA**, with no execution error, zero valid rows and zero
valid months. The real population count is `checksJson.datasetTargets = 33,983`; the summary's
`targetRowCount = 0` denotes the resulting eligible out-of-sample-window population, not the raw
dataset size. Do not report the raw population as empty.

Overlapping raw-population exclusions: 33,983 unproven membership provenance, 33,983 missing
eligible predictions, 21,850 missing benchmarks and 2,790 missing realized outcomes. None of the
minimum 12 valid months, 3,000 valid rows, 70% coverage, known PIT regime or complete portfolio
period checks passed. Research namespaces were deliberately not substituted for official PIT
production evidence. The gate correctly withholds promotion; this is not a completed proof of
live predictive performance.

### Validation query performance correction

The evaluation population's symbol/date outcome join examined about1,733same-date candidates per
member in the real MariaDB plan. It now uses the existing signal identity plus outcome PRIMARY
key. A parenthesized outer join preserves one null member row when signal versions lack outcomes;
genuine duplicate outcomes remain visible for strict rejection. All119,393actual outcomes have
zero orphan/mismatched source identities. No table/index migration or model mathematics changed.

Four real temporary-table regressions cover identity lookup, missing members, unused versions and
genuine duplicates. The old source-spelling assertion was superseded by these behavioral cases.
Changed application tree verification: **188tests passed,0failures/errors/skips**,08:43:44KST,
`tmp/quant-resume-final-full-tests-20261018.log`. The earlier unchanged-tree focused rerun was23/23.

Web smoke: warm `/quant`200 in0.43s, CPB detail and backtest200. Cold `/quant` still exceeded45s
during concurrent model processing; this existing limitation is not declared fixed by this query change.

Final server restart loaded the corrected mapper (source/runtime SHA256 identical), app PID 3764,
port 8080, SQL initialization and external startup refresh disabled. The post-restart smoke again
timed out on cold `/quant` after 45.23s; the subsequent warm request was HTTP 200 in 0.56s.
CPB detail returned 200 in 0.58s and backtest returned 200 in 0.19s. No application exception was
found in the filtered final server log. These checks establish warm availability, not acceptable
cold-start latency. Evidence: `tmp/quant-research-final-web-smoke-20261018.json` and
`tmp/quant-research-final-quant-warm-20261018.json`.

Reproduction tools:

- `scripts/import_quant_research.py` — offline, hash-checked, atomic namespace import.
- `scripts/QuantResearchReplay.java` — existing corrected V2→V9, chronological label-maturity
  filtering, actual calculation timestamps, no LIVE generation. Each model stage is transactional.
- `scripts/QuantResearchValidate.java` — descriptive 20D diagnostics for every model and namespace,
  plus the unchanged strict production evaluator. No relaxation of PIT or promotion requirements.
- `ReconstructedReturnDiagnostics` — all matured members remain in the coverage denominator;
  absent predictions/outcomes, duplicate keys, date mismatches and incomplete benchmark windows
  are excluded and counted. Monthly statistics are descriptive, not independent trades or CAGR.

## Tests already executed

- Current application tree, Maven with `QUANT_EVIDENCE_DB_TEST=true`: **188 passed, 0 failures,
  errors or skips**, completed 08:43:44 KST. Includes real MariaDB population-join and maturity-queue
  regressions. Evidence: `tmp/quant-resume-final-full-tests-20261018.log`.
- Before the PC shutdown: **185 passed** after the exact-semantics local neighborhood reuse
  change (earlier stable tree: 178). No repeated full run is needed solely for report updates.
- Python discovery: **23 passed** (workspace TEMP/TMP used for restricted-shell compatibility).
- DB replay/import diagnostics are additional data checks, not substitutes for PIT evidence.

## Remaining evidence limits

Input-version boundary: the 119,393 completed outcomes belong to the pre-existing `quant-v2`
LIVE signals (61,094 labels) and `quant-v2-hist` reconstructed signals (58,299 labels). The new
`q3-price-20261017` signals currently have **zero matured outcomes**. The CORRECTED V2–V9
model names denote corrected model/label computation; they do not certify that all historical
input scores were regenerated with q3 or that those old features were available on decision day.
The retrospective metrics must not be presented as measured live q3 performance.

Community membership, corrected only where supported, is not `OFFICIAL_PIT`. Reconstructed signals
were calculated after their historical decision dates; analyst-feature publication availability is
not fully proven, and historical outcome sector labels can come from current profiles. Rebased
vendor OHLCV is not guaranteed original-date executable pricing. Delisted/missing constituents must
stay in coverage denominators; excluding them would create survivor bias. Missing strict macro
vintages are not filled from revised data. These limits prohibit a live-performance/promotion claim.
