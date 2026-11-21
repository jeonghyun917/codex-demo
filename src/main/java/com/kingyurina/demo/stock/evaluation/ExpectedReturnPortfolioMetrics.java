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
    public String accountingScope() {
        return "NON_OVERLAPPING_CASH_TO_CASH_HOLDINGS;ELAPSED_CALENDAR_DAYS;"
                + "ROUND_TRIP_COSTS_ON_INITIAL_NOTIONAL;EXIT_ONLY_DRAWDOWN;HOLDING_PERIOD_SHARPE_SORTINO_NOT_DAILY;"
                + "PRICE_RETURNS_NO_DIVIDENDS_OR_DELISTING_RECOVERY";
    }
}

