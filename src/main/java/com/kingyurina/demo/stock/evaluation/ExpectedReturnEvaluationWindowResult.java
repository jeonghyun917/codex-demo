package com.kingyurina.demo.stock.evaluation;

import java.time.LocalDate;
import java.util.Map;
import java.util.LinkedHashMap;

public record ExpectedReturnEvaluationWindowResult(
        Long runId,
        String windowId,
        LocalDate testFrom,
        LocalDate testTo,
        String modelVersion,
        String status,
        int trainRowCount,
        int testRowCount,
        ExpectedReturnMetrics metrics,
        ExpectedReturnPortfolioMetrics portfolio) {
    /** JDBC numeric columns use null for unavailable evidence; NaN is never a stored measurement. */
    public Map<String, Double> finiteMetrics() {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("rankIc", metrics.rankIc());
        values.put("positiveIcDatePct", metrics.positiveIcDatePct());
        values.put("maePct", metrics.maePct());
        values.put("directionalAccuracyPct", metrics.directionalAccuracyPct());
        values.put("brierScore", metrics.brierScore());
        values.put("calibrationErrorPct", metrics.calibrationErrorPct());
        values.put("intervalCoveragePct", metrics.intervalCoveragePct());
        values.put("quintileSpreadPct", metrics.quintileSpreadPct());
        values.put("netReturnPct", portfolio.netReturnPct());
        values.put("annualizedExcessReturnPct", portfolio.annualizedExcessReturnPct());
        values.put("sharpe", portfolio.sharpe());
        values.put("maximumDrawdownPct", portfolio.maximumDrawdownPct());
        values.put("turnoverPct", portfolio.turnoverPct());
        values.put("transactionCostPct", portfolio.transactionCostPct());
        values.replaceAll((key, value) -> Double.isFinite(value) ? value : null);
        return values;
    }
}

