package com.kingyurina.demo.stock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class YahooChartProperties {

    private final String baseUrl;
    private final int defaultYears;
    private final long requestDelayMillis;

    public YahooChartProperties(
            @Value("${yahoo-chart.base-url:https://query1.finance.yahoo.com/v8/finance/chart/}") String baseUrl,
            @Value("${yahoo-chart.default-years:5}") int defaultYears,
            @Value("${yahoo-chart.request-delay-millis:250}") long requestDelayMillis) {
        this.baseUrl = baseUrl;
        this.defaultYears = defaultYears;
        this.requestDelayMillis = requestDelayMillis;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public int defaultYears() {
        return defaultYears;
    }

    public long requestDelayMillis() {
        return requestDelayMillis;
    }
}
