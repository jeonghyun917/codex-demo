package com.kingyurina.demo.stock.evaluation;

public record ExpectedReturnPromotionPolicy(
        String version,
        int minimumValidMonths,
        int minimumRows,
        double minimumCoveragePct,
        double minimumBaselineRankIc,
        double minimumPositiveIcDatePct,
        double maximumBrierScore,
        double maximumCalibrationErrorPct,
        double minimumIntervalCoveragePct,
        double maximumIntervalCoveragePct,
        double minimumWorstRegimeRankIc,
        double minimumCandidateRankIcImprovement,
        double minimumAnnualizedExcessImprovementPct,
        double minimumSharpeImprovement,
        double maximumDrawdownDeteriorationPct,
        double maximumTurnoverIncreaseRatio,
        double maximumCalibrationDeteriorationPct,
        double minimumCandidateCoverageRatio) {

    public static ExpectedReturnPromotionPolicy sp500Excess20dV1() {
        return new ExpectedReturnPromotionPolicy(
                "SP500_EXCESS_20D_V1",
                12,
                3000,
                70.0d,
                0.0d,
                50.0d,
                0.25d,
                10.0d,
                70.0d,
                90.0d,
                -0.05d,
                0.01d,
                1.0d,
                0.10d,
                2.0d,
                1.25d,
                2.0d,
                0.95d);
    }
}

