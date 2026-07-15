package com.kingyurina.demo.stock.evaluation;

public record ExpectedReturnPortfolioMetrics(
        int periodCount,
        double grossReturnPct,
        double netReturnPct,
        double benchmarkReturnPct,
        double annualizedExcessReturnPct,
        double sharpe,
        double sortino,
        double maximumDrawdownPct,
        double benchmarkBeatRatePct,
        double turnoverPct,
        double transactionCostPct,
        double tailLossPct) {
}

