package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.util.List;

public record StockMarketView(
        String title,
        String subtitle,
        String totalMembers,
        String cachedQuotes,
        String advancers,
        String decliners,
        String aggregateMarketCap,
        String averageChange,
        List<IndexCard> indexCards,
        List<Row> rows,
        List<Row> topMovers,
        List<SectorRow> sectors) {

    public record IndexCard(String label, String value, String change, boolean positive) {
    }

    public record Row(String rank, String symbol, String name, String logoUrl, String logoInitial, String sector,
            String price, String change, String marketCap, String institutionFlow, String per, String pbr, String roe,
            String signalScore, String signalConfidence, String signalTone, boolean positive, Integer signalValue,
            String dataQualityScore, String dataQualityTone, Integer dataQualityValue,
            String expectedExcessReturn, String calibratedUpsideProbability, String expectedConfidence,
            Integer expectedConfidenceValue, String expectedModelVersion, String expectedSignalDate,
            Integer valuationScore, Integer qualityScore, Integer growthScore, Integer stabilityScore,
            Integer earningsScore, Integer analystScore, Integer newsScore, Integer momentumScore, Integer riskScore,
            String threeMonthHighRatio, BigDecimal threeMonthHighRatioValue, String threeMonthHighRatioTone) {
    }

    public record SectorRow(String sector, String count, String marketCap, String averageChange) {
    }
}
