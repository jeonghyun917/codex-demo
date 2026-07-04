package com.kingyurina.demo.stock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TossInvestProperties {

    private final String apiKey;
    private final String secretKey;
    private final String baseUrl;
    private final int timeoutSeconds;

    public TossInvestProperties(
            @Value("${toss.api-key:${TOSS_API_KEY:}}") String apiKey,
            @Value("${toss.secret-key:${TOSS_SECRET_KEY:}}") String secretKey,
            @Value("${toss.base-url:https://openapi.tossinvest.com}") String baseUrl,
            @Value("${toss.timeout-seconds:15}") int timeoutSeconds) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.baseUrl = baseUrl == null ? "https://openapi.tossinvest.com" : baseUrl.trim();
        this.timeoutSeconds = Math.max(3, timeoutSeconds);
    }

    public String apiKey() {
        return apiKey;
    }

    public String secretKey() {
        return secretKey;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public int timeoutSeconds() {
        return timeoutSeconds;
    }

    public boolean configured() {
        return !apiKey.isBlank() && !secretKey.isBlank();
    }
}
