package com.kingyurina.demo.stock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StooqProperties {

    private final String baseUrl;
    private final int defaultYears;
    private final long requestDelayMillis;

    public StooqProperties(
            @Value("${stooq.base-url:https://stooq.com/q/d/l/}") String baseUrl,
            @Value("${stooq.default-years:5}") int defaultYears,
            @Value("${stooq.request-delay-millis:300}") long requestDelayMillis) {
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
