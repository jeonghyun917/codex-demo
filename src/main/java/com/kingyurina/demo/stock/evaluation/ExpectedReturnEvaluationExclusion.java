package com.kingyurina.demo.stock.evaluation;

import java.time.LocalDate;

public record ExpectedReturnEvaluationExclusion(
        Code code,
        LocalDate signalDate,
        String symbol,
        String detail) {

    public enum Code {
        MISSING_PREDICTION,
        MISSING_REALIZED_RETURN,
        MISSING_BENCHMARK,
        MISSING_MEMBERSHIP,
        PIT_VIOLATION,
        UNPROVEN_PREDICTION_PROVENANCE,
        UNPROVEN_MEMBERSHIP_PROVENANCE,
        DUPLICATE_REALIZED_RETURN,
        DUPLICATE_PREDICTION,
        INVALID_VALUE
    }
}

