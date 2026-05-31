package com.kingyurina.demo.stock;

import java.util.List;

public record StockDashboard(
        String symbol,
        boolean apiConfigured,
        boolean databaseEnabled,
        boolean quoteCacheHit,
        boolean profileCacheHit,
        boolean metricCacheHit,
        boolean newsCacheHit,
        boolean recommendationCacheHit,
        boolean epsSurpriseCacheHit,
        boolean candleCacheHit,
        String message,
        CompanyProfile profile,
        StockQuoteCache quote,
        StockMetricSnapshot metric,
        List<CompanyNews> news,
        List<StockRecommendationTrend> recommendations,
        List<StockEpsSurprise> epsSurprises,
        List<StockCandleDaily> candles,
        String summary) {
}
