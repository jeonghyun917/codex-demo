package com.kingyurina.demo.stock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SecEdgarProperties {

    private final String baseUrl;
    private final String filesBaseUrl;
    private final String userAgent;
    private final int companyFactsCacheHours;
    private final int maxFactsPerConcept;
    private final long requestDelayMillis;
    private final boolean fullConceptEnabled;

    public SecEdgarProperties(
            @Value("${sec.edgar.base-url:https://data.sec.gov}") String baseUrl,
            @Value("${sec.edgar.files-base-url:https://www.sec.gov}") String filesBaseUrl,
            @Value("${sec.edgar.user-agent:king-yurina-stock-research/0.1 contact@example.com}") String userAgent,
            @Value("${sec.edgar.company-facts-cache-hours:168}") int companyFactsCacheHours,
            @Value("${sec.edgar.max-facts-per-concept:80}") int maxFactsPerConcept,
            @Value("${sec.edgar.request-delay-millis:300}") long requestDelayMillis,
            @Value("${sec.edgar.full-concept-enabled:false}") boolean fullConceptEnabled) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.filesBaseUrl = trimTrailingSlash(filesBaseUrl);
        this.userAgent = userAgent;
        this.companyFactsCacheHours = companyFactsCacheHours;
        this.maxFactsPerConcept = maxFactsPerConcept;
        this.requestDelayMillis = requestDelayMillis;
        this.fullConceptEnabled = fullConceptEnabled;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String filesBaseUrl() {
        return filesBaseUrl;
    }

    public String userAgent() {
        return userAgent;
    }

    public int companyFactsCacheHours() {
        return companyFactsCacheHours;
    }

    public int maxFactsPerConcept() {
        return maxFactsPerConcept;
    }

    public long requestDelayMillis() {
        return requestDelayMillis;
    }

    public boolean fullConceptEnabled() {
        return fullConceptEnabled;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank() || !value.endsWith("/")) {
            return value;
        }
        return value.substring(0, value.length() - 1);
    }
}
