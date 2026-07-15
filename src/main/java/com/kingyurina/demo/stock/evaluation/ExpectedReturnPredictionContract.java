package com.kingyurina.demo.stock.evaluation;

public record ExpectedReturnPredictionContract(
        String version,
        String indexCode,
        String baselineModel,
        int horizonTradingDays,
        int minimumTrainingYears,
        int minimumTrainingRows,
        int minimumTestRows,
        int minimumValidMonths,
        double minimumCoveragePct) {

    public static ExpectedReturnPredictionContract sp500Excess20dV1() {
        return new ExpectedReturnPredictionContract(
                "SP500_EXCESS_20D_V1",
                "SP500",
                "EXPECTED_RETURN_V9",
                20,
                3,
                1000,
                100,
                12,
                70.0d);
    }
}

