package com.kingyurina.demo.stock.evaluation;

import java.time.LocalDate;
import java.util.List;

public record WalkForwardWindow(
        String id,
        LocalDate testFrom,
        LocalDate testTo,
        List<ExpectedReturnEvaluationRow> trainRows,
        List<ExpectedReturnEvaluationRow> testRows,
        Status status) {

    public WalkForwardWindow {
        trainRows = List.copyOf(trainRows);
        testRows = List.copyOf(testRows);
    }

    public enum Status {
        ELIGIBLE,
        INSUFFICIENT_DATA
    }
}

