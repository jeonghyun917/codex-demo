package com.kingyurina.demo.stock.evaluation;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.kingyurina.demo.stock.*;

class ReconstructedReturnDiagnosticsTest {
    private static final String INDEX = "RESEARCH_SP500", MODEL = "EXPECTED_RETURN_V9";
    private static final LocalDate SIGNAL = LocalDate.of(2025, 1, 2);
    private static final LocalDateTime RECORDED = LocalDateTime.of(2026, 10, 18, 1, 30);
    private final ReconstructedReturnDiagnostics diagnostics = new ReconstructedReturnDiagnostics();

    @Test void denominatorIncludesMissingConstituentsRatherThanOnlyPredictionSurvivors() {
        var report = diagnostics.evaluate(INDEX, MODEL, List.of(prediction("A")), List.of(outcome("A")),
                benchmark(), List.of(member("A", SIGNAL), member("B", SIGNAL)), SIGNAL.plusDays(40));
        assertEquals(2, report.targetCount()); assertEquals(1, report.usableRows());
        assertEquals(50.0, report.coveragePct());
        assertEquals(1, report.exclusionCounts().get("MISSING_PREDICTION"));
        assertEquals(1, report.exclusionCounts().get("MISSING_OUTCOME"));
        assertEquals(50.0, report.metricsByMonth().get("2025-01").coveragePct());
        assertEquals(0.0, report.metrics().maePct(), 1e-9);
    }
    @Test void historicalCalculationTimestampIsPreservedAndNeverMakesResearchPromotable() {
        var prediction = prediction("A");
        var report = diagnostics.evaluate(INDEX, MODEL, List.of(prediction), List.of(outcome("A")),
                benchmark(), List.of(member("A", SIGNAL)), SIGNAL.plusDays(40));
        assertEquals(1, report.usableRows()); assertEquals(RECORDED, prediction.getCalculatedAt());
        assertEquals(RECORDED, report.firstCalculatedAt()); assertEquals(RECORDED, report.lastCalculatedAt());
        assertEquals("RECONSTRUCTED_RESEARCH_NOT_PIT", report.classification());
        assertEquals("PRICE_INDEX_NOT_TOTAL_RETURN", report.benchmarkDefinition());
        assertFalse(report.promotionAllowed());
        assertEquals(SIGNAL, report.firstSignalDate()); assertEquals(SIGNAL.plusDays(21), report.lastLabelEndDate());
        assertTrue(report.warnings().stream().anyMatch(s -> s.contains("overlap")));
    }
    @Test void incompleteBenchmarkHoldingPeriodCannotProduceUsableMetrics() {
        var gap = new ArrayList<>(benchmark()); gap.remove(10);
        var report = diagnostics.evaluate(INDEX, MODEL, List.of(prediction("A")), List.of(outcome("A")),
                gap, List.of(member("A", SIGNAL)), SIGNAL.plusDays(40));
        assertEquals(1, report.targetCount()); assertEquals(0, report.usableRows());
        assertEquals(1, report.exclusionCounts().get("INCOMPLETE_BENCHMARK"));
        assertEquals(0, report.metrics().sampleCount());
    }
    @Test void immatureMembersAreReportedSeparatelyWithoutInflatingMatureDenominator() {
        var report = diagnostics.evaluate(INDEX, MODEL, List.of(prediction("A")), List.of(outcome("A")),
                benchmark(), List.of(member("A", SIGNAL), member("B", SIGNAL.plusDays(15))), SIGNAL.plusDays(30));
        assertEquals(1, report.targetCount()); assertEquals(1, report.immatureTargets());
        assertEquals(1, report.usableRows()); assertEquals(100.0, report.coveragePct());
        assertFalse(report.exclusionCounts().containsKey("MISSING_PREDICTION"));
    }
    @Test void duplicatesAndMalformedPredictionFieldsAreExcludedWithoutChoosingArbitraryRows() {
        var invalid = prediction("B"); invalid.setCalibratedUpsideProbabilityPct(BigDecimal.valueOf(101));
        var report = diagnostics.evaluate(INDEX, MODEL, List.of(prediction("A"), prediction("A"), invalid),
                List.of(outcome("A"), outcome("A"), outcome("B")), benchmark(),
                List.of(member("A", SIGNAL), member("B", SIGNAL)), SIGNAL.plusDays(40));
        assertEquals(2, report.targetCount()); assertEquals(0, report.usableRows());
        assertEquals(1, report.exclusionCounts().get("DUPLICATE_PREDICTION"));
        assertEquals(1, report.exclusionCounts().get("DUPLICATE_OUTCOME"));
        assertEquals(1, report.exclusionCounts().get("INVALID_PREDICTION"));
    }
    @Test void duplicateMembershipAndEntryOnSignalDayCannotSilentlyEnterResearchMetrics() {
        var bad = outcome("B"); bad.setEntryTradeDate(SIGNAL);
        var report = diagnostics.evaluate(INDEX, MODEL, List.of(prediction("A"), prediction("B")),
                List.of(outcome("A"), bad), benchmark(),
                List.of(member("A", SIGNAL), member("A", SIGNAL), member("B", SIGNAL)), SIGNAL.plusDays(40));
        assertEquals(2, report.targetCount()); assertEquals(0, report.usableRows());
        assertEquals(1, report.exclusionCounts().get("DUPLICATE_MEMBERSHIP"));
        assertEquals(1, report.exclusionCounts().get("INVALID_OUTCOME"));
    }
    @Test void unrelatedNamespacesAndNonMembersDoNotCreateTargetsAndEmptyEvidenceHasNoCoverage() {
        var foreign = member("B", SIGNAL); foreign.setIndexCode("SP500");
        var inactive = member("C", SIGNAL); inactive.setMember(false);
        var report = diagnostics.evaluate(INDEX, MODEL, List.of(prediction("A")), List.of(outcome("A")),
                benchmark(), List.of(foreign, inactive), SIGNAL.plusDays(40));
        assertEquals(0, report.targetCount()); assertEquals(0, report.usableRows());
        assertEquals(0, report.coveragePct()); assertNull(report.firstSignalDate());
        assertTrue(report.metricsByMonth().isEmpty());
    }
    private static StockExpectedReturnSnapshot prediction(String symbol) {
        var row = new StockExpectedReturnSnapshot(); row.setIndexCode(INDEX); row.setModelVersion(MODEL);
        row.setSignalDate(SIGNAL); row.setSymbol(symbol); row.setHorizonDays(20); row.setCalculatedAt(RECORDED);
        row.setExpectedReturnPct(BigDecimal.valueOf(5)); row.setExpectedExcessReturnPct(BigDecimal.valueOf(5));
        row.setCalibratedUpsideProbabilityPct(BigDecimal.valueOf(75));
        row.setExcessP10Pct(BigDecimal.valueOf(-3)); row.setExcessP90Pct(BigDecimal.valueOf(10)); return row;
    }
    private static StockBacktestResult outcome(String symbol) {
        var row = new StockBacktestResult(); row.setSignalDate(SIGNAL); row.setSymbol(symbol); row.setHorizonDays(20);
        row.setEntryTradeDate(SIGNAL.plusDays(1)); row.setExitTradeDate(SIGNAL.plusDays(21));
        row.setForwardReturnPct(BigDecimal.valueOf(5)); return row;
    }
    private static StockIndexMembershipSnapshot member(String symbol, LocalDate day) {
        var row = new StockIndexMembershipSnapshot(); row.setIndexCode(INDEX); row.setSymbol(symbol);
        row.setSnapshotDate(day); row.setMember(true); row.setSource("COMMUNITY_RECONSTRUCTION"); return row;
    }
    private static List<StockBenchmarkReturn> benchmark() {
        var rows = new ArrayList<StockBenchmarkReturn>();
        // Synthetic consecutive sessions make the twenty-session boundary independently explicit.
        for (int i = 1; i <= 45; i++) {
            var row = new StockBenchmarkReturn(); row.setIndexCode(INDEX); row.setTradeDate(SIGNAL.plusDays(i));
            row.setReturnPct(BigDecimal.ZERO); row.setSource("PRICE_INDEX"); rows.add(row);
        }
        return rows;
    }
}
