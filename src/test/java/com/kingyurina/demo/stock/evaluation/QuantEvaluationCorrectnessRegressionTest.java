package com.kingyurina.demo.stock.evaluation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.kingyurina.demo.stock.*;
import tools.jackson.databind.ObjectMapper;

class QuantEvaluationCorrectnessRegressionTest {
    private static final LocalDate SIGNAL = LocalDate.of(2024, 1, 2);
    private final ExpectedReturnEvaluationDatasetService assembler = new ExpectedReturnEvaluationDatasetService();

    @Test
    void rejectsPostOutcomePredictionEvenWhenEvaluationRunsMuchLater() {
        var prediction = prediction("A", SIGNAL, 3);
        prediction.setCalculatedAt(LocalDateTime.of(2024, 2, 15, 12, 0));
        var dataset = assemble(List.of(prediction), benchmarks(SIGNAL), List.of(member("A", SIGNAL)));
        assertEquals(0, dataset.rows().size());
        assertEquals(1, dataset.pitViolationCount());
    }

    @Test
    void doesNotInventPredictionAvailabilityOrTrustReconstructionSourceNames() {
        var prediction = prediction("A", SIGNAL, 3);
        prediction.setCalculatedAt(null);
        assertEquals(0, assemble(List.of(prediction), benchmarks(SIGNAL), List.of(member("A", SIGNAL))).rows().size());
        prediction.setCalculatedAt(SIGNAL.atTime(12, 0));
        prediction.setSource("PIT_HISTORICAL_SCORE_SECTOR_QUANTILE_CALIBRATION");
        assertEquals(0, assemble(List.of(prediction), benchmarks(SIGNAL), List.of(member("A", SIGNAL))).rows().size());
    }

    @Test
    void missingBenchmarkDayCannotProduceTwentyDayLabel() {
        var incomplete = new ArrayList<>(benchmarks(SIGNAL));
        incomplete.remove(8);
        var dataset = assemble(List.of(prediction("A", SIGNAL, 3)), incomplete, List.of(member("A", SIGNAL)));
        assertEquals(0, dataset.rows().size());
        assertTrue(dataset.exclusions().stream().anyMatch(e -> e.code() == ExpectedReturnEvaluationExclusion.Code.MISSING_BENCHMARK));
    }

    @Test
    void duplicateBenchmarkDateAndMixedSourceWindowAreUnavailable() {
        var duplicate = new ArrayList<>(benchmarks(SIGNAL));
        duplicate.set(8, duplicate.get(7));
        assertNull(CompleteBenchmarkReturn.compound(duplicate, "SP500", SIGNAL.plusDays(1), SIGNAL.plusDays(21), 20));
        var mixed = benchmarks(SIGNAL);
        mixed.get(10).setSource("OTHER_PRICE_BASIS");
        assertNull(CompleteBenchmarkReturn.compound(mixed, "SP500", SIGNAL.plusDays(1), SIGNAL.plusDays(21), 20));
    }

    @Test
    void incompleteConstituentCoverageCannotMasqueradeAsCompleteBenchmark() {
        var benchmark = benchmarks(SIGNAL);
        benchmark.forEach(row -> { row.setConstituentCount(500); row.setCoverageCount(500); });
        benchmark.get(10).setCoverageCount(100);
        assertNull(CompleteBenchmarkReturn.compound(benchmark, "SP500", SIGNAL.plusDays(1), SIGNAL.plusDays(21), 20));
    }

    @Test
    void walkForwardNeverUsesRowsWhoseAvailabilityIsMissingOrLate() {
        var contract = new ExpectedReturnPredictionContract("C", "SP500", "M", 20, 0, 1, 1, 1, 0);
        var missing = new ExpectedReturnEvaluationRow("C", "SP500", "M", SIGNAL, null,
                SIGNAL.plusDays(20), "A", 1, 1, 60, -5, 15, 1, 0, "T", "NEUTRAL");
        var late = new ExpectedReturnEvaluationRow("C", "SP500", "M", SIGNAL,
                SIGNAL.plusDays(2).atStartOfDay(), SIGNAL.plusDays(20), "B", 1, 1, 60, -5, 15, 1, 0, "T", "NEUTRAL");
        var windows = new WalkForwardWindowGenerator(contract).generate(List.of(missing, late, row(SIGNAL.plusMonths(1), 1)));
        assertTrue(windows.stream().allMatch(window -> window.trainRows().isEmpty()));
    }

    @Test
    void invalidProbabilityAndInvertedIntervalsAreExcludedNotClamped() {
        var prediction = prediction("A", SIGNAL, 3);
        prediction.setCalibratedUpsideProbabilityPct(BigDecimal.valueOf(110));
        assertEquals(0, assemble(List.of(prediction), benchmarks(SIGNAL), List.of(member("A", SIGNAL))).rows().size());
        prediction.setCalibratedUpsideProbabilityPct(BigDecimal.valueOf(60));
        prediction.setExcessP10Pct(BigDecimal.valueOf(16));
        assertEquals(0, assemble(List.of(prediction), benchmarks(SIGNAL), List.of(member("A", SIGNAL))).rows().size());
    }

    @Test
    void membershipTargetsWithoutPredictionsOrOutcomesStayInDenominator() {
        var dataset = assemble(List.of(prediction("A", SIGNAL, 3)), benchmarks(SIGNAL),
                List.of(member("A", SIGNAL), member("B", SIGNAL)));
        assertEquals(2, dataset.targetRowCount());
        assertEquals(50, dataset.coveragePct(), 1e-9);
        assertTrue(dataset.exclusions().stream().anyMatch(e -> e.symbol().equals("B")
                && e.code() == ExpectedReturnEvaluationExclusion.Code.MISSING_REALIZED_RETURN));
    }

    @Test
    void currentMembershipProxyIsNotCertifiedHistoricalMembership() {
        var member = member("A", SIGNAL);
        member.setSource("CURRENT_MEMBERSHIP_PROXY");
        assertEquals(0, assemble(List.of(prediction("A", SIGNAL, 3)), benchmarks(SIGNAL), List.of(member)).rows().size());
    }

    @Test
    void overlappingTwentyDayReturnsCannotReuseCapital() {
        var metrics = new ExpectedReturnPortfolioEvaluator(1).evaluate(List.of(row(SIGNAL, 10), row(SIGNAL.plusDays(1), 10)), 0);
        assertEquals(1, metrics.periodCount());
        assertEquals(10, metrics.grossReturnPct(), 1e-9);
    }

    @Test
    void annualizationUsesActualElapsedDaysAndChargesEntryAndExit() {
        var annualRow = new ExpectedReturnEvaluationRow("C", "SP500", "M", SIGNAL,
                SIGNAL.atTime(12, 0), SIGNAL.plusDays(365), "A", 10, 10, 60, -5, 15, 10, 0, "T", "UNKNOWN");
        var metrics = new ExpectedReturnPortfolioEvaluator(1).evaluate(List.of(annualRow), 10);
        // A fully liquidated independent holding pays 10bps each side.
        assertEquals(0.2, metrics.transactionCostPct(), 1e-9);
        assertEquals(9.8, metrics.netReturnPct(), 1e-9);
        assertEquals(9.8, metrics.annualizedExcessReturnPct(), 1e-9);
    }

    @Test
    void invalidCostsCannotSilentlyContaminatePerformance() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExpectedReturnPortfolioEvaluator(1).evaluate(List.of(row(SIGNAL, 10)), Double.NaN));
    }

    @Test
    void emptyOrOverflowedPortfolioDoesNotPublishFabricatedFinitePerformance() {
        var evaluator = new ExpectedReturnPortfolioEvaluator(1);
        assertTrue(Double.isNaN(evaluator.evaluate(List.of(), 0).annualizedExcessReturnPct()));
        var overflow = evaluator.evaluate(List.of(row(SIGNAL, Double.MAX_VALUE), row(SIGNAL.plusDays(40), Double.MAX_VALUE)), 0);
        assertTrue(Double.isNaN(overflow.netReturnPct()));
        assertTrue(Double.isNaN(overflow.sharpe()));
    }

    @Test
    void finalMetricsUseOnlyEligibleTestWindowsAndUnknownRegimeCannotQualify() {
        StockBacktestMapper mapper = mock(StockBacktestMapper.class);
        @SuppressWarnings("unchecked") ObjectProvider<StockBacktestMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        var predictions = new ArrayList<StockExpectedReturnSnapshot>();
        var actuals = new ArrayList<StockBacktestResult>();
        var memberships = new ArrayList<StockIndexMembershipSnapshot>();
        var benchmark = new ArrayList<StockBenchmarkReturn>();
        for (int month = 0; month < 38; month++) {
            LocalDate date = LocalDate.of(2020, 1, 1).plusMonths(month);
            benchmark.addAll(benchmarks(date));
            int count = month == 37 ? 10 : 100;
            for (int i = 0; i < count; i++) {
                predictions.add(prediction("S" + i, date, i / 10.0));
                actuals.add(actual("S" + i, date, month == 36 ? -i / 10.0 : i / 10.0));
                memberships.add(member("S" + i, date));
            }
            if (month == 36) memberships.add(member("MISSING", date));
        }
        when(mapper.findExpectedReturnSnapshots(anyString(), any(), any())).thenReturn(predictions);
        when(mapper.findResultsForEvaluation(anyString(), anyInt(), any(), any())).thenReturn(actuals);
        when(mapper.findEvaluationMembershipSnapshots(anyString(), any(), any())).thenReturn(memberships);
        when(mapper.findBenchmarkReturns(anyString(), any(), any())).thenReturn(benchmark);
        when(mapper.completeExpectedReturnEvaluationRun(any())).thenReturn(1);
        var run = new ExpectedReturnEvaluationService(provider, new ObjectMapper()).evaluateBaseline("SP500", LocalDate.of(2024, 1, 1));
        assertEquals("INSUFFICIENT_DATA", run.getStatus());
        assertEquals(1, run.getValidMonths());
        assertEquals(100, run.getValidRowCount());
        assertEquals(101, run.getTargetRowCount());
        assertEquals(-1.0, run.getRankIc(), 1e-9);
        assertEquals(10000.0 / 101, run.getCoveragePct(), 1e-9);
        assertNull(run.getAnnualizedExcessReturnPct(), "Missing universe members cannot be replaced by surviving labels in portfolio ranking");
        assertTrue(run.getChecksJson().contains("KNOWN_MARKET_REGIME"));
        assertFalse(regimeCheckPassed(run));

        var regime = new StockMacroRegimeSnapshot();
        regime.setIndexCode("SP500"); regime.setSnapshotDate(LocalDate.of(2023, 1, 1));
        regime.setRegimeLabel("RISK_OFF"); regime.setSource("BENCHMARK_MARKET_PROXY");
        regime.setCalculatedAt(LocalDateTime.of(2023, 1, 1, 10, 0));
        when(mapper.findMacroRegimeSnapshots(anyString(), any(), any())).thenReturn(List.of(regime));
        var known = new ExpectedReturnEvaluationService(provider, new ObjectMapper()).evaluateBaseline("SP500", LocalDate.of(2024, 1, 1));
        assertTrue(regimeCheckPassed(known));
        regime.setCalculatedAt(LocalDateTime.of(2023, 2, 1, 10, 0));
        var late = new ExpectedReturnEvaluationService(provider, new ObjectMapper()).evaluateBaseline("SP500", LocalDate.of(2024, 1, 1));
        assertFalse(regimeCheckPassed(late));
    }

    private static boolean regimeCheckPassed(ExpectedReturnEvaluationRun run) {
        for (var check : new ObjectMapper().readTree(run.getChecksJson()).path("checks")) {
            if ("KNOWN_MARKET_REGIME".equals(check.path("name").asText())) return check.path("passed").asBoolean();
        }
        fail("Missing regime sufficiency check");
        return false;
    }

    private ExpectedReturnEvaluationDataset assemble(List<StockExpectedReturnSnapshot> predictions,
            List<StockBenchmarkReturn> benchmark, List<StockIndexMembershipSnapshot> members) {
        return assembler.assemble(ExpectedReturnPredictionContract.sp500Excess20dCorrected(),
                ExpectedReturnPredictionContract.sp500Excess20dCorrected().baselineModel(), predictions,
                List.of(actual("A", SIGNAL, 10)), benchmark, members, LocalDate.of(2025, 1, 1));
    }

    private static StockExpectedReturnSnapshot prediction(String symbol, LocalDate date, double expected) {
        var row = new StockExpectedReturnSnapshot();
        row.setIndexCode("SP500"); row.setSymbol(symbol); row.setSignalDate(date); row.setHorizonDays(20);
        row.setModelVersion(ExpectedReturnPredictionContract.sp500Excess20dCorrected().baselineModel());
        row.setExpectedExcessReturnPct(BigDecimal.valueOf(expected));
        row.setCalibratedUpsideProbabilityPct(BigDecimal.valueOf(60));
        row.setExcessP10Pct(BigDecimal.valueOf(-15)); row.setExcessP90Pct(BigDecimal.valueOf(15));
        row.setCalculatedAt(date.atTime(12, 0)); row.setSource("LIVE_PROVENANCE_STABILIZED_EXPECTED_RETURN_V9");
        return row;
    }

    private static StockBacktestResult actual(String symbol, LocalDate date, double result) {
        var row = new StockBacktestResult();
        row.setSymbol(symbol); row.setSignalDate(date); row.setHorizonDays(20);
        row.setEntryTradeDate(date.plusDays(1)); row.setExitTradeDate(date.plusDays(21));
        row.setForwardReturnPct(BigDecimal.valueOf(result)); return row;
    }

    private static StockIndexMembershipSnapshot member(String symbol, LocalDate date) {
        var row = new StockIndexMembershipSnapshot();
        row.setIndexCode("SP500"); row.setSymbol(symbol); row.setSnapshotDate(date); row.setMember(true);
        row.setSource("OFFICIAL_PIT"); return row;
    }

    private static List<StockBenchmarkReturn> benchmarks(LocalDate signal) {
        var rows = new ArrayList<StockBenchmarkReturn>();
        for (int i = 2; i <= 21; i++) {
            var row = new StockBenchmarkReturn();
            row.setIndexCode("SP500"); row.setTradeDate(signal.plusDays(i)); row.setReturnPct(BigDecimal.ZERO);
            row.setSource("MARKET_SNAPSHOT_WEIGHTED"); rows.add(row);
        }
        return rows;
    }

    private static ExpectedReturnEvaluationRow row(LocalDate date, double actual) {
        return new ExpectedReturnEvaluationRow("C", "SP500", "M", date, date.atTime(12, 0),
                date.plusDays(28), "A", 10, actual, 60, -5, 15, actual, 0, "T", "UNKNOWN");
    }
}
