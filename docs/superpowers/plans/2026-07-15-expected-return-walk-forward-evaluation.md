# Expected Return Walk-Forward Evaluation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a leakage-safe, reproducible 20-trading-day SP500 excess-return evaluation pipeline that qualifies `EXPECTED_RETURN_V9` and gates future candidate models.

**Architecture:** Add a focused `stock/evaluation` package of immutable contracts and pure calculators, then connect it to existing expected-return, backtest-result, benchmark, and PIT membership data through `StockBacktestMapper`. Persist immutable run/window results and expose only the latest completed summary through the existing DB-backed Quant dashboard payload.

**Tech Stack:** Java 21, Spring Boot 4, JUnit 5, MyBatis, MariaDB, Thymeleaf

## Global Constraints

- Contract version is `SP500_EXCESS_20D_V1`.
- Universe is point-in-time S&P 500 membership; horizon is 20 trading days; rebalance is monthly.
- Evaluation uses expanding windows, a three-year minimum training period, and a 20-trading-day label embargo.
- Page rendering remains DB-only and must not execute evaluation or call external APIs.
- `EXPECTED_RETURN_V9` remains unchanged in this cycle.
- No live trading, Railway changes, Python ML stack, or Optimized v5 behavior changes.
- Every production behavior is introduced through a failing test first.

---

### Task 1: Prediction contract and walk-forward windows

**Files:**
- Create: `src/main/java/com/kingyurina/demo/stock/evaluation/ExpectedReturnPredictionContract.java`
- Create: `src/main/java/com/kingyurina/demo/stock/evaluation/ExpectedReturnEvaluationRow.java`
- Create: `src/main/java/com/kingyurina/demo/stock/evaluation/WalkForwardWindow.java`
- Create: `src/main/java/com/kingyurina/demo/stock/evaluation/WalkForwardWindowGenerator.java`
- Test: `src/test/java/com/kingyurina/demo/stock/evaluation/WalkForwardWindowGeneratorTest.java`

**Interfaces:**
- Produces: `ExpectedReturnPredictionContract.sp500Excess20dV1()` and `List<WalkForwardWindow> generate(List<ExpectedReturnEvaluationRow>)`.
- `WalkForwardWindow` exposes `trainRows`, `testRows`, `testFrom`, `testTo`, and `status`.

- [ ] **Step 1: Write failing contract and window tests**

```java
@Test void contractFixesSp500TwentyDayRules() {
    var contract = ExpectedReturnPredictionContract.sp500Excess20dV1();
    assertEquals("SP500_EXCESS_20D_V1", contract.version());
    assertEquals(20, contract.horizonTradingDays());
    assertEquals(1000, contract.minimumTrainingRows());
}

@Test void generatorKeepsDatesTogetherAndEmbargoesUnfinishedLabels() {
    var windows = new WalkForwardWindowGenerator(contract).generate(rowsAcrossFortyMonths());
    assertTrue(windows.stream().allMatch(w -> w.trainRows().stream()
        .allMatch(r -> r.labelEndDate().isBefore(w.testFrom()))));
    assertTrue(windows.stream().allMatch(w -> datesDoNotCrossTrainAndTest(w)));
}
```

- [ ] **Step 2: Run tests and verify RED**

Run: `mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=WalkForwardWindowGeneratorTest test`
Expected: compilation failure because the evaluation types do not exist.

- [ ] **Step 3: Implement immutable contract, row, window, and generator**

```java
public record ExpectedReturnPredictionContract(String version, String indexCode, String baselineModel,
        int horizonTradingDays, int minimumTrainingYears, int minimumTrainingRows,
        int minimumTestRows, int minimumValidMonths, double minimumCoveragePct) {
    public static ExpectedReturnPredictionContract sp500Excess20dV1() {
        return new ExpectedReturnPredictionContract("SP500_EXCESS_20D_V1", "SP500",
                "EXPECTED_RETURN_V9", 20, 3, 1000, 100, 12, 70.0);
    }
}
```

Generate one calendar-month test window at a time. A training row is eligible only when its `labelEndDate` is before the test month and its `signalDate` is at least three years before the test month. Mark undersized windows `INSUFFICIENT_DATA` instead of dropping them.

- [ ] **Step 4: Run focused and full tests**

Run focused test, then `mvn.cmd "-Dmaven.repo.local=.m2/repository" test`; expect PASS.

- [ ] **Step 5: Commit**

Stage only Task 1 files and commit with `feat: add leakage-safe walk-forward windows`.

### Task 2: Prediction and portfolio metrics

**Files:**
- Create: `ExpectedReturnMetrics.java`, `ExpectedReturnMetricsCalculator.java`, `ExpectedReturnPortfolioMetrics.java`, and `ExpectedReturnPortfolioEvaluator.java` under the evaluation package.
- Test: `ExpectedReturnMetricsCalculatorTest.java` and `ExpectedReturnPortfolioEvaluatorTest.java`.

**Interfaces:**
- Consumes valid evaluation test rows.
- Produces immutable prediction and top-20 equal-weight portfolio metrics.

- [ ] **Step 1: Write failing metric tests**

```java
@Test void perfectRankingHasOneIcAndZeroBrierForCertainCorrectProbabilities() {
    ExpectedReturnMetrics metrics = calculator.calculate(perfectRows());
    assertEquals(1.0, metrics.rankIc(), 1e-9);
    assertEquals(0.0, metrics.brierScore(), 1e-9);
    assertEquals(100.0, metrics.directionalAccuracyPct(), 1e-9);
}

@Test void topTwentyPortfolioDeductsTurnoverCost() {
    ExpectedReturnPortfolioMetrics metrics = evaluator.evaluate(twoMonthlyRebalances(), 10.0);
    assertTrue(metrics.netReturnPct() < metrics.grossReturnPct());
}
```

- [ ] **Step 2: Verify RED** by running both new test classes and observing missing classes.
- [ ] **Step 3: Implement date-first Spearman IC with average tie ranks, MAE, direction accuracy, Brier, weighted calibration error, P10-P90 coverage, quintile spread, and coverage.**
- [ ] **Step 4: Implement top-20 equal-weight monthly portfolio metrics** including turnover cost, annualized excess return, Sharpe, Sortino, MDD, beat rate, and tail loss.
- [ ] **Step 5: Run focused/full tests and commit** with `feat: calculate expected return evaluation metrics`.

### Task 3: Versioned promotion policy and decisions

**Files:**
- Create: `ExpectedReturnPromotionPolicy.java`, `ExpectedReturnPromotionResult.java`, and `ExpectedReturnPromotionGate.java`.
- Test: `ExpectedReturnPromotionGateTest.java`.

**Interfaces:** Consumes baseline/candidate prediction and portfolio metrics plus data sufficiency and PIT violations; produces a decision and named threshold checks.

- [ ] **Step 1: Write failing boundary tests**

```java
@Test void pitViolationAlwaysRejectsCandidate() {
    var result = gate.evaluateCandidate(qualifiedBaseline(), strongCandidate(), 1);
    assertEquals(REJECT, result.decision());
    assertFalse(result.check("NO_PIT_VIOLATIONS").passed());
}

@Test void insufficientSamplePrecedesPerformanceJudgment() {
    var result = gate.qualifyBaseline(metrics(), portfolio(), 11, 2999, 90.0, 0);
    assertEquals(INSUFFICIENT_DATA, result.decision());
}
```

- [ ] **Step 2: Verify RED.**
- [ ] **Step 3: Implement all approved thresholds in `ExpectedReturnPromotionPolicy.sp500Excess20dV1()`.** Evaluate sufficiency first, hard rejection second, and promotion/hold last. Persist check name, expected, actual, and pass state.
- [ ] **Step 4: Run focused/full tests and commit** with `feat: add expected return promotion gate`.

### Task 4: Dataset assembly and immutable evaluation persistence

**Files:**
- Create evaluation package DTOs/services: `ExpectedReturnEvaluationExclusion`, `ExpectedReturnEvaluationDataset`, `ExpectedReturnEvaluationDatasetService`, `ExpectedReturnEvaluationRun`, `ExpectedReturnEvaluationWindowResult`.
- Modify: `StockBacktestMapper.java`, `StockBacktestMapper.xml`, `schema-mariadb.sql`.
- Test: `ExpectedReturnEvaluationDatasetServiceTest.java`.

**Interfaces:** Load snapshots, 20D realized results, PIT membership, and daily benchmark returns for explicit dates; return valid rows plus exclusions; append immutable run/window results.

- [ ] **Step 1: Write failing dataset tests** for missing prediction/result/benchmark/membership, PIT violation, duplicates, and invalid numbers. Verify benchmark daily returns are compounded rather than approximated with constituent averages.
- [ ] **Step 2: Verify RED.**
- [ ] **Step 3: Implement dataset assembly** using prediction `calculatedAt`, result `exitTradeDate`, PIT membership, and compounded benchmark series. Require explicit `fromDate`, `toDate`, and `asOfDate`.
- [ ] **Step 4: Add append-only tables** `stock_expected_return_evaluation_run`, `stock_expected_return_evaluation_window`, and `stock_expected_return_evaluation_exclusion_count`. A run may transition only from `RUNNING` to a terminal state.
- [ ] **Step 5: Run focused/full tests and commit** with `feat: assemble and persist expected return evaluations`.

### Task 5: Evaluation orchestration and batch execution

**Files:**
- Create: `ExpectedReturnEvaluationService.java`, `ExpectedReturnEvaluationBatchRunner.java`.
- Modify: `application.yml`.
- Test: `ExpectedReturnEvaluationServiceTest.java`.

- [ ] **Step 1: Write failing orchestration tests** proving RUNNING creation, per-window persistence, gate application, terminal completion, PIT rejection, and FAILED transition on unexpected errors.
- [ ] **Step 2: Verify RED.**
- [ ] **Step 3: Implement `evaluateBaseline(String indexCode, LocalDate asOfDate)`** and disabled-by-default batch properties under `app.batch.expected-return-evaluation`.
- [ ] **Step 4: Run focused/full tests and commit** with `feat: run expected return evaluation batch`.

### Task 6: DB-backed Quant dashboard summary

**Files:**
- Create: `ExpectedReturnEvaluationSummaryView.java`, `ExpectedReturnEvaluationSummaryService.java`.
- Modify: `StockDashboardViewPayload.java`, `StockDashboardViewSnapshotService.java`, `dashboard.html`, `main.css`.
- Test: `ExpectedReturnEvaluationSummaryServiceTest.java`.

- [ ] **Step 1: Write failing tests** for explicit Not evaluated and completed summary states.
- [ ] **Step 2: Verify RED.**
- [ ] **Step 3: Implement DB-only summary** with contract, model, as-of date, samples, coverage, IC, Brier, net excess, decision, and failed checks. Increment dashboard snapshot version; never evaluate from a page request.
- [ ] **Step 4: Run focused/full tests and commit** with `feat: show expected return evaluation on quant dashboard`.

### Task 7: Final verification and operating documentation

**Files:**
- Modify: `docs/stock-backtest.md`
- Create: `run-expected-return-evaluation-batch.cmd`

- [ ] **Step 1: Document contract, thresholds, tables, and this command**

```powershell
java "-Dspring.profiles.active=mariadb" -jar target\codex-demo-0.0.1-SNAPSHOT.jar --spring.main.web-application-type=none --app.signal.refresh.run-on-startup=false --app.batch.expected-return-evaluation.enabled=true --app.batch.expected-return-evaluation.exit-on-complete=true --app.batch.expected-return-evaluation.index-code=SP500
```

- [ ] **Step 2: Run `mvn.cmd "-Dmaven.repo.local=.m2/repository" clean test`** and require zero failures/errors.
- [ ] **Step 3: Run `git diff --check`** and require no whitespace errors.
- [ ] **Step 4: Commit** with `docs: explain expected return evaluation workflow`.

