package com.kingyurina.demo.stock.evaluation;

public record ExpectedReturnMetrics(
        int sampleCount,
        double rankIc,
        double positiveIcDatePct,
        double maePct,
        double directionalAccuracyPct,
        double brierScore,
        double calibrationErrorPct,
        double intervalCoveragePct,
        double quintileSpreadPct,
        double coveragePct) {
}

