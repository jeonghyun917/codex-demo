package com.kingyurina.demo.stock.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ExpectedReturnMetricsCalculatorTest {

    private final ExpectedReturnMetricsCalculator calculator = new ExpectedReturnMetricsCalculator();

    @Test
    void perfectRankingHasOneIcZeroMaeAndZeroBrier() {
        ExpectedReturnMetrics metrics = calculator.calculate(perfectRows(), 10);

        assertEquals(10, metrics.sampleCount());
        assertEquals(1.0d, metrics.rankIc(), 1e-9);
        assertEquals(0.0d, metrics.maePct(), 1e-9);
        assertEquals(100.0d, metrics.directionalAccuracyPct(), 1e-9);
        assertEquals(0.0d, metrics.brierScore(), 1e-9);
        assertEquals(100.0d, metrics.intervalCoveragePct(), 1e-9);
        assertTrue(metrics.quintileSpreadPct() > 0.0d);
    }

    @Test
    void tiedPredictionsUseAverageRanksWithoutProducingNan() {
        List<ExpectedReturnEvaluationRow> rows = List.of(
                row("A", 1.0d, 2.0d, 80.0d),
                row("B", 1.0d, 1.0d, 80.0d),
                row("C", -1.0d, -1.0d, 20.0d),
                row("D", -1.0d, -2.0d, 20.0d));

        ExpectedReturnMetrics metrics = calculator.calculate(rows, 4);

        assertTrue(Double.isFinite(metrics.rankIc()));
        assertTrue(metrics.rankIc() > 0.8d);
    }

    private static List<ExpectedReturnEvaluationRow> perfectRows() {
        List<ExpectedReturnEvaluationRow> rows = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            double value = index - 4.5d;
            rows.add(row("S" + index, value, value, value > 0 ? 100.0d : 0.0d));
        }
        return rows;
    }

    private static ExpectedReturnEvaluationRow row(String symbol, double predicted, double actual, double probability) {
        LocalDate date = LocalDate.of(2025, 1, 6);
        return new ExpectedReturnEvaluationRow(
                "SP500_EXCESS_20D_V1", "SP500", "EXPECTED_RETURN_V9",
                date, LocalDateTime.of(date, java.time.LocalTime.NOON), date.plusDays(28), symbol,
                predicted, actual, probability, actual - 1.0d, actual + 1.0d,
                actual + 2.0d, 2.0d, "Technology", "NEUTRAL");
    }
}

