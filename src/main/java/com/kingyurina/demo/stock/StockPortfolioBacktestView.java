package com.kingyurina.demo.stock;

import java.util.List;

public record StockPortfolioBacktestView(
        String indexCode,
        String benchmarkLabel,
        String costAssumption,
        List<MetricCard> cards,
        List<StrategyRow> strategies,
        List<RiskModelRow> riskModels,
        List<RiskImpactRow> riskImpacts,
        List<PeriodRow> recentPeriods,
        List<SectorExposureRow> sectorExposures,
        List<PositionRow> latestPositions,
        List<String> notes) {

    public record MetricCard(String label, String value, String note, String tone) {
    }

    public record RiskModelRow(String metric, String value, String rule, String tone) {
    }

    public record RiskImpactRow(
            String strategy,
            String horizon,
            String topCount,
            String weighting,
            String sharpeDelta,
            String drawdownDelta,
            String betaDelta,
            String sectorDelta,
            String costDelta,
            String excessDelta,
            String verdict,
            String tone) {
    }

    public record StrategyRow(
            String rank,
            String strategy,
            String riskModel,
            String horizon,
            String topCount,
            String weighting,
            String rebalanceCount,
            String cumulativeReturn,
            String benchmarkReturn,
            String excessReturn,
            String annualReturn,
            String volatility,
            String sharpe,
            String maxDrawdown,
            String benchmarkBeatRate,
            String averageTurnover,
            String averageTransactionCost,
            String averageMaxSectorWeight,
            String averageMaxPositionWeight,
            String averageBeta,
            String averageTrailingVolatility,
            String averageLiquidity,
            String tone) {
    }

    public record PeriodRow(
            String tradeDate,
            String strategy,
            String horizon,
            String positionCount,
            String grossReturn,
            String netReturn,
            String benchmarkReturn,
            String excessReturn,
            String turnover,
            String transactionCost,
            String beta,
            String trailingVolatility,
            String liquidity,
            String equity,
            String benchmarkEquity,
            String tone) {
    }

    public record SectorExposureRow(
            String strategy,
            String horizon,
            String sector,
            String averageWeight,
            String maxWeight,
            String tone) {
    }

    public record PositionRow(
            String rank,
            String symbol,
            String name,
            String sector,
            String score,
            String weight,
            String forwardReturn,
            String beta,
            String trailingVolatility,
            String liquidity,
            String tone) {
    }
}
