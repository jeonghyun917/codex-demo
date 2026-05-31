package com.kingyurina.demo.stock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FinnhubProperties {

    private final String apiKey;
    private final String baseUrl;
    private final int quoteCacheSeconds;
    private final int profileCacheHours;
    private final int metricsCacheHours;
    private final int newsCacheMinutes;
    private final int recommendationCacheHours;
    private final int epsSurprisesCacheHours;
    private final int candleCacheHours;

    public FinnhubProperties(
            @Value("${finnhub.api-key:}") String apiKey,
            @Value("${finnhub.base-url:https://finnhub.io/api/v1}") String baseUrl,
            @Value("${finnhub.quote-cache-seconds:60}") int quoteCacheSeconds,
            @Value("${finnhub.profile-cache-hours:24}") int profileCacheHours,
            @Value("${finnhub.metrics-cache-hours:24}") int metricsCacheHours,
            @Value("${finnhub.news-cache-minutes:30}") int newsCacheMinutes,
            @Value("${finnhub.recommendation-cache-hours:24}") int recommendationCacheHours,
            @Value("${finnhub.eps-surprises-cache-hours:24}") int epsSurprisesCacheHours,
            @Value("${finnhub.candle-cache-hours:24}") int candleCacheHours) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.quoteCacheSeconds = quoteCacheSeconds;
        this.profileCacheHours = profileCacheHours;
        this.metricsCacheHours = metricsCacheHours;
        this.newsCacheMinutes = newsCacheMinutes;
        this.recommendationCacheHours = recommendationCacheHours;
        this.epsSurprisesCacheHours = epsSurprisesCacheHours;
        this.candleCacheHours = candleCacheHours;
    }

    public boolean hasApiKey() {
        return StringUtils.hasText(apiKey);
    }

    public String apiKey() {
        return apiKey;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public int quoteCacheSeconds() {
        return quoteCacheSeconds;
    }

    public int profileCacheHours() {
        return profileCacheHours;
    }

    public int metricsCacheHours() {
        return metricsCacheHours;
    }

    public int newsCacheMinutes() {
        return newsCacheMinutes;
    }

    public int recommendationCacheHours() {
        return recommendationCacheHours;
    }

    public int epsSurprisesCacheHours() {
        return epsSurprisesCacheHours;
    }

    public int candleCacheHours() {
        return candleCacheHours;
    }
}
