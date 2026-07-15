package com.kingyurina.demo.stock.evaluation;

import java.util.List;

public record ExpectedReturnEvaluationDataset(
        List<ExpectedReturnEvaluationRow> rows,
        List<ExpectedReturnEvaluationExclusion> exclusions,
        int targetRowCount,
        double coveragePct,
        int pitViolationCount) {

    public ExpectedReturnEvaluationDataset {
        rows = List.copyOf(rows);
        exclusions = List.copyOf(exclusions);
    }
}

