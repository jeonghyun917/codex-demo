package com.kingyurina.demo.stock.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ExpectedReturnPortfolioEvaluatorTest {

    @Test
    void topTwentyPortfolioDeductsTurnoverCost() {
        ExpectedReturnPortfolioEvaluator evaluator = new ExpectedReturnPortfolioEvaluator(20);
        ExpectedReturnPortfolioMetrics metrics = evaluator.evaluate(twoMonthlyRebalances(), 10.0d);

        assertEquals(2, metrics.periodCount());
        assertTrue(metrics.transactionCostPct() > 0.0d);
        assertTrue(metrics.netReturnPct() < metrics.grossReturnPct());
        assertTrue(metrics.annualizedExcessReturnPct() > 0.0d);
        assertEquals(100.0d, metrics.benchmarkBeatRatePct(), 1e-9);
    }

    @Test
    void drawdownReflectsLosingMonth() {
        ExpectedReturnPortfolioEvaluator evaluator = new ExpectedReturnPortfolioEvaluator(20);
        List<ExpectedReturnEvaluationRow> rows = new ArrayList<>(month(LocalDate.of(2025, 1, 6), 3.0d, 1.0d));
        rows.addAll(month(LocalDate.of(2025, 2, 6), -10.0d, -2.0d));

        ExpectedReturnPortfolioMetrics metrics = evaluator.evaluate(rows, 0.0d);

        assertTrue(metrics.maximumDrawdownPct() < 0.0d);
        assertTrue(metrics.tailLossPct() < 0.0d);
    }

    private static List<ExpectedReturnEvaluationRow> twoMonthlyRebalances() {
        List<ExpectedReturnEvaluationRow> rows = new ArrayList<>(month(LocalDate.of(2025, 1, 6), 3.0d, 1.0d));
        rows.addAll(month(LocalDate.of(2025, 2, 6), 2.0d, 0.5d));
        return rows;
    }

    private static List<ExpectedReturnEvaluationRow> month(LocalDate date, double selectedReturn,
            double benchmarkReturn) {
        List<ExpectedReturnEvaluationRow> rows = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            double predicted = 25 - index;
            double actualReturn = index < 20 ? selectedReturn : -2.0d;
            rows.add(new ExpectedReturnEvaluationRow(
                    "SP500_EXCESS_20D_V1", "SP500", "EXPECTED_RETURN_V9",
                    date, LocalDateTime.of(date, java.time.LocalTime.NOON), date.plusDays(28), "S" + index,
                    predicted, actualReturn - benchmarkReturn, 70.0d, -5.0d, 8.0d,
                    actualReturn, benchmarkReturn, "Technology", "NEUTRAL"));
        }
        return rows;
    }
}

