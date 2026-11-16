package com.kingyurina.demo.stock.evaluation;

import java.util.List;
import java.util.Map;
import java.time.LocalDate;

public record ExpectedReturnEvaluationDataset(
        List<ExpectedReturnEvaluationRow> rows,
        List<ExpectedReturnEvaluationExclusion> exclusions,
        int targetRowCount,
        double coveragePct,
        int pitViolationCount,
        Map<LocalDate, Integer> targetCountsByDate) {

    public ExpectedReturnEvaluationDataset {
        rows = List.copyOf(rows);
        exclusions = List.copyOf(exclusions);
        targetCountsByDate = Map.copyOf(targetCountsByDate);
    }
}

