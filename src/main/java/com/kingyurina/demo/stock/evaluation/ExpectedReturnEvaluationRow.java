package com.kingyurina.demo.stock.evaluation;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExpectedReturnEvaluationRow(
        String contractVersion,
        String indexCode,
        String modelVersion,
        LocalDate signalDate,
        LocalDateTime availableAt,
        LocalDate labelEndDate,
        String symbol,
        double predictedExcessReturnPct,
        double actualExcessReturnPct,
        double predictedUpsideProbabilityPct,
        double excessP10Pct,
        double excessP90Pct,
        double actualReturnPct,
        double benchmarkReturnPct,
        String sector,
        String marketRegime) {
}

