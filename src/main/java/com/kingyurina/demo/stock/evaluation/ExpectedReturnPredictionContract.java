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
        return new ExpectedReturnPredictionContract("SP500_EXCESS_20D_V1", "SP500", "EXPECTED_RETURN_V9",
                20, 3, 1000, 100, 12, 70.0d);
    }

    public static ExpectedReturnPredictionContract sp500Excess20dCorrected() {
        return new ExpectedReturnPredictionContract(
                "SP500_EXCESS_20D_V2_CORRECTED_20261017",
                "SP500",
                "EXPECTED_RETURN_V9_CORRECTED_20261017",
                20,
                3,
                1000,
                100,
                12,
                70.0d);
    }

    public String benchmarkDefinition() {
        return "MARKET_SNAPSHOT_WEIGHTED_PRICE_RETURN_PROXY_NOT_OFFICIAL_INDEX_OR_TOTAL_RETURN";
    }
}

