# Quant correctness remediation implementation plan

> **For agentic workers:** Use systematic-debugging, test-driven-development and verification-before-completion. Independent domains may use dispatching-parallel-agents; integration and final review remain coordinated.

**Goal:** Correct all 16 audit categories without concealing missing data or presenting unvalidated models as investment evidence.

**Architecture:** Keep DB-backed read paths. Use coherent, dated price inputs; fail closed on unproven historical provenance; train only on matured observations; evaluate non-overlapping capital deployments and eligible out-of-sample windows. Preserve original datasets and old model versions.

**Tech stack:** Java 21, Spring Boot, MyBatis, MariaDB, Maven.

**Spec:** `docs/quant-audit-2026-10-17.md`, approved by user on 2026-10-17.

## Constraints and rulings

- Work in the existing `main` working copy because relevant fixes and user work are already uncommitted here. Preserve unrelated changes; no automatic commit/push or destructive data rewrite.
- User explicitly requested immediate execution without approval gates; this plan is a record, not a new approval request.
- Finance correctness work uses focused RED/GREEN tests, then one integrated suite and independent review. Do not repeat full suites for each subtask.
- Separate file ownership for concurrent workers; serialize Maven runs with Windows named mutex `Local\CodexQuantMaven`.
- Missing point-in-time membership, share counts, corporate actions or model provenance must remain disclosed/excluded, never guessed. No paid data purchase or fabricated backfill.
- Runtime validation uses an isolated application profile/port with scheduled collection disabled, then only the relevant local service may be restarted if safe. No unrelated process termination.

## Review focus

- Recent valid quotes versus stale quotes and split-adjusted daily histories; never mix price bases silently.
- Future outcome mutations must not affect earlier trained predictions or calibration.
- Incomplete benchmark windows and missing predictions must lower coverage rather than disappear.
- Overlapping 20/60-day holdings must not reuse the same capital.
- Stale materialized payloads or partial batch failure must not appear fresh; GET paths must not collect or persist.

## Task 1 — Price and ingestion integrity

Owner: price/data worker. Files: `StockCacheService`, `StockAnalysisService`, `StockInfoViewService`, `StockDataQualityService`, `StockSignalService`, Yahoo/Finnhub/Stooq collectors and candle/constituent mappers; focused stock tests.

- [ ] Add failing tests for stale APH quote versus candles, valid newer quote, single candle, inconsistent adjustment bases, invalid/empty provider results and source-switch continuity.
- [ ] Select coherent date/source/price tuples for list, detail and Signal; expose basis metadata for UI integration.
- [ ] Make detail reads DB-only and avoid persisting empty quality records from GET.
- [ ] Count validated persisted input, retain last good cache, and prevent partial incompatible source histories from becoming canonical.
- [ ] Run focused tests and report remaining external-data limitations explicitly.

## Task 2 — Training, historical universe and legacy portfolio

Owner: model worker. Files: `StockPortfolioBacktestService`, V8/V9 services, `StockBacktestMapper.java/xml`, new helper/test classes.

- [ ] RED: labels that exit after target time, future calibration changes, benchmark mismatch, overlapping holdings and current-only historical population.
- [ ] Require matured labels; construct as-of calibration; align label benchmark with evaluator; use dated historical membership, exclude unverified proxies from certified evaluation.
- [ ] Version corrected outputs; retain old records. Prevent model generations from silently overwriting certified old semantics.
- [ ] Correct legacy portfolio overlap/annualization and move cold request computation to controlled background refresh.
- [ ] Fix evaluation SQL target denominator (do not require prediction existence) in coordination with Task 3.
- [ ] GREEN: regression tests including future mutation invariance.

## Task 3 — Evaluation and capital accounting

Owner: evaluation worker. Files: `stock/evaluation/**` and its tests; request shared mapper edits from Task 2.

- [ ] RED: late/missing prediction timestamps, incomplete benchmark horizon, excluded windows leaking into final aggregate, absent predictions in denominator, overlapping returns.
- [ ] Validate point-in-time contract; use eligible out-of-sample rows only; distinguish insufficient/unsupported provenance from valid data.
- [ ] Implement non-overlapping deploy/hold/rebalance accounting with actual elapsed-time annualization and explicit costs/metric scope.
- [ ] Verify with hand-derived fixtures, deterministic walk-forward regression and audit probe.

## Task 4 — Snapshot, freshness and UI integration

Owner: parent. Files: `StockDashboardViewSnapshotService`, `StockSignalRefreshService`, `StockQuantModelHealthService`, `StockApiDataSourceService`, `StockMarketViewService`, `StockHeatmapViewService`, relevant views/templates/tests, `StockBacktestService`.

- [ ] RED: stale snapshot accepted, partial refresh failure suppresses all views, stale source marked healthy.
- [ ] Add bounded snapshot freshness and read-only cold fallback; isolate downstream refresh failures without falsely declaring the failed stage successful.
- [ ] Show actual price basis/date, use USD rather than undated fixed FX, label excess probability correctly and disclose unverified model/proxy data.
- [ ] GREEN: focused tests and rendered DB-backed responses.

## Task 5 — Integration, independent review and evidence

- [ ] Run `mvn.cmd "-Dmaven.repo.local=.m2/repository" test` after all workers finish.
- [ ] Run regression/purged walk-forward fixtures and report eligible sample count; no profitability claim without sufficient live/PIT evidence.
- [ ] Start corrected build without background collection first; verify APH/latest quote cases and relevant page routes from real DB. Preserve data and old version results.
- [ ] Independent correctness review of changed financial paths; fix material findings with focused regressions.
- [ ] Write remediation matrix mapping all audit items to changes, tests and any real data blockers. Clearly distinguish code repair from demonstrated predictive performance.

## Progress

- 2026-10-17: Baseline audit completed: 61 tests pass but semantics fail audit probe. Existing dirty files recorded; no unrelated changes staged.

## 재개 후 최종 구현·검증 기록 — 2026-10-17 KST

코드 수정과 아래 검증을 마쳤다. **예측 수익성이 입증됐다는 판정은 아니다.** 실제 DB 평가는 `INSUFFICIENT_DATA`이며, 원천 증거가 부족한 결과를 검증 통과로 승격하지 않는다. 위 체크리스트는 최초 실행계획이며 실제 수행 범위와 제한은 이 기록을 우선한다.

### 수정 범위와 감사 항목 연결

| 감사 항목 | 적용한 수정 | 남는 제한 |
|---|---|---|
| 1, 16 저장본·GET | 화면 저장본 유효기간/새 버전, DB 읽기 전용 fallback, 연구 계산은 명시적 배치로 분리 | 저장본/메모리 캐시의 정해진 유효기간 내 지연 가능 |
| 2 가격 | 목록·상세·Signal의 가격 선택 정책 통일, 최신 유효 quote 또는 동일 원천 종가 묶음 사용, 기준일·출처 표시 | 저장된 가격이지 실시간 가격 보장은 아님 |
| 3, 4 미래정보 | 학습 라벨 청산일이 의사결정일 이전인 자료만 허용, 날짜별 보정과 cutoff 기록 | 과거 입력 원천의 PIT 증거 자체를 새로 만들지는 않음 |
| 5 투자 회계 | 겹치지 않는 자본 투입, 실제 경과일 연환산, 진입·청산 비용, 기간 말 위험지표 범위 표시 | 일별 mark-to-market MDD/Sharpe가 아님 |
| 6 비교 기준 | 학습·평가에 같은 완전 기간 benchmark 함수 사용, 초과확률로 명칭 수정 | 시가총액 가중 가격수익 proxy, 공식 총수익 지수 아님 |
| 7, 8 평가 | 늦거나 없는 예측시각 제외, 증명되지 않은 재구성 제외, 적격 OOS window만 최종 집계, 당시 국면만 연결 | 적격 자료가 없으면 성능 수치를 표시하지 않음 |
| 9, 14 모집단 | 당시 구성종목 기준 평가 대상과 누락 분모 보존, proxy 구성종목은 인증 평가에서 제외 | 공식 과거 편입·퇴출 이력 추가 확보 필요 |
| 10 상세 조회 | 외부 수집·품질 저장 없는 DB 조회 경로 | 새 수집은 별도 배치 필요 |
| 11 수집·실패 | 빈 응답/잘못된 값이 정상 캐시를 덮지 않음, 유효 저장량 기준 집계, 단계별 실패 보고 | 실제 공급자 장애/누락은 경고로 남음 |
| 12 기업행사 | 원천 전환·과거 OHLC 변경은 별도 원천 데이터로 격리, 조정 기준 불명 quote/고점 비율 결합 차단 | 공식 split/dividend 조정·상장폐지 회수액 이력 수집은 미구현; 전 종목 기업행사 대응 완료로 주장하지 않음 |
| 13 최신성 | QUOTE/CANDLE 실제 날짜·전체 지수 커버리지 검사, stale 원천이 있는 재계산은 정상 표시 금지 | 거래소별 휴장 달력 대신 경과일 기준 경고 |
| 15 단위 | 고정 환율 1,365원 제거, USD 및 가격 기준일 표시 | 실시간 원화 환산 기능은 제공하지 않음 |

### 최종 리뷰와 실제 저장에서 추가로 수정한 항목

- 현재 예측용 Signal 조회는 `q3-price-20261017`이며 오늘 계산되고 미래 시각이 아닌 행만 사용한다. 과거/구형 행을 `CURRENT_DATE`로 바꿔 새 예측에 넣지 않는다.
- 저장 API가 예외 대신 `saved=false`를 반환해도 `failedStages`에 기록한다. 다른 지수 갱신은 계속하며 배치 종료 상태도 실패를 반영한다.
- Signal 버전명은 기존 두 `VARCHAR(20)` 칸에 맞는 17자다. 최초 실 DB 실행에서 길이 오류를 발견하고 RED/GREEN 회귀 테스트로 수정했다.
- 기존 5/20/60개 종가의 4/19/59개 수익률 간격 오류를 수정했다. 다음 거래일 종가에 진입하고 N개 간격 뒤 청산하므로 N+1개 종가를 요구한다.
- 정정 결과는 새 `stock_backtest_result_v2`에만 저장한다. 기존 `stock_signal_backtest_result` 87,105건은 보존했다. 새 테이블은 기존 구조를 `LIKE`로 복사했으며 기존 외래키는 복제되지 않는다.

### 실행 증거

- 최종 전체 테스트: `mvn.cmd "-Dmaven.repo.local=.m2/repository" test` — **135개, 실패 0, 오류 0**, 23:33:37 KST. 로그: `tmp/quant-final-verified.log`.
- 실제 DB 재계산: **요청 518 / 성공 518 / 실패 0 / failedStages=[]**. 중복 제거된 현재 3개 지수 전체 종목이며 SP500 503/503, NASDAQ100 103/103, DOW30 30/30의 수정 Signal 저장을 확인했다. 비현재 종목의 기존 행은 삭제하지 않았다. 로그: `tmp/quant-corrected-refresh-v2.log`.
- 수정 결과 6,885건: 원시 일봉의 `entry < trade_date <= exit` 개수가 horizon과 다른 경우 **0건**, `(exit_close / entry_close - 1) * 100`와 저장 수익률 차이가 0.000002%p를 넘는 경우 **0건**. 전체 역사 87,105건을 재생성한 것은 아니다.
- APH 화면: **$81.49 / 2026-10-15 종가 / yahoo-chart / 3M 90.61% / 수정 Signal 73**. 잘못된 175.06% 미표시 확인.
- CRM 화면: **$240.70 / 2026-10-17 저장 시세 / FINNHUB / 수정 Signal 62**. 다른 가격 기준의 고점비율은 `-`.
- 최종 HTTP: Quant 3개 지수, Stocks, APH 상세, Heatmap, Backtest, Portfolio **8개 경로 모두 200**. 단발 관측에서 Quant 20–181ms, Stocks/Heatmap 약 2.1–2.4초. 부하·장기 성능 검증은 아님.
- UI에서 가격 기준일, 단위, Signal, `INSUFFICIENT_DATA`를 확인했다. 상세 GET 전후 API 로그와 APH 품질 저장시각도 비교했다.
- 리뷰 후 제한 재검토에서 위 세 가지 수정과 버전명 연결의 잔여 Important/Critical 문제를 발견하지 않았다. 저장소 전체 무결성을 보증한다는 뜻은 아니다.

### 예측·백테스트 검증 판정

합성 회귀 검증에서 사후 생성 예측은 거부하고, 겹치는 두 +10% 투자는 +21%가 아닌 한 번의 +10% 투입으로 계산한다. Walk-forward 회귀는 준비 구간/표본 부족 구간을 제외한 100/101개 표본과 Rank IC -1을 독립 fixture와 대조한다.

실제 DB 정정 평가 run **2**:

- `SP500_EXCESS_20D_V2_CORRECTED_20261017`, `INSUFFICIENT_DATA`.
- 원천 대상 **33,983개**, 인증 가능한 dataset 행 **0**, 적격 OOS 표본 **0**.
- 제외: 구성종목 출처 미입증 33,983, 수정 모델 과거 예측 없음 33,983, 실현수익 누락 32,644, 비교지수 누락 33,483. **한 행에 여러 사유가 겹치므로 합산하면 안 된다.**
- 예측 로직 회귀 검증과 실제 투자 성능 검증은 별개다. 충분한 당시 원천/기업행사/구성종목 증거, 수정 모델의 과거 또는 누적 LIVE 예측과 완결된 미래 결과가 필요하다. 기존 누수 포함 결과를 새 성능으로 재사용하지 않았다.
- 로그: `tmp/quant-corrected-evaluation.log`; SQL 실행/평가 결과는 로컬 DB에도 저장됨.

### 운영 인계

- 로컬 검증 서버: `http://127.0.0.1:8080/quant` (Java PID 14416), MariaDB PID 9784. 자동 Signal 스케줄/시작 갱신은 중복 검증 배치를 피하려고 이번 프로세스에서만 비활성화했다. 원천 수집 자동화를 새로 설정하지 않았다.
- 커밋·Push·배포는 하지 않았다. 관련 없는 기존 변경과 사용자 파일을 보존했다.
