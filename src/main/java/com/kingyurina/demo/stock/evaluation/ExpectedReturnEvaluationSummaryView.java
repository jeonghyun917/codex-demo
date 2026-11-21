package com.kingyurina.demo.stock.evaluation;

public record ExpectedReturnEvaluationSummaryView(
        String status,
        String tone,
        String decision,
        String contractVersion,
        String modelVersion,
        String asOfDate,
        String samples,
        String coverage,
        String rankIc,
        String brierScore,
        String annualizedExcessReturn,
        String gateSummary) {
}

