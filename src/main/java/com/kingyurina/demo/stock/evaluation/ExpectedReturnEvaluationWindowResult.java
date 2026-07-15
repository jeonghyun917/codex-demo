package com.kingyurina.demo.stock.evaluation;

import java.time.LocalDate;

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
}

