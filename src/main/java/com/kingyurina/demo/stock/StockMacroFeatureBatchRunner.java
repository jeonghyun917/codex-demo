package com.kingyurina.demo.stock;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class StockMacroFeatureBatchRunner implements ApplicationRunner {

    private final boolean enabled;
    private final boolean exitOnComplete;
    private final String indexCode;
    private final String indexCodes;
    private final int dateLimit;
    private final StockMacroFeatureService stockMacroFeatureService;
    private final ConfigurableApplicationContext applicationContext;

    public StockMacroFeatureBatchRunner(
            @Value("${app.batch.macro-feature.enabled:false}") boolean enabled,
            @Value("${app.batch.macro-feature.exit-on-complete:false}") boolean exitOnComplete,
            @Value("${app.batch.macro-feature.index-code:SP500}") String indexCode,
            @Value("${app.batch.macro-feature.index-codes:}") String indexCodes,
            @Value("${app.batch.macro-feature.date-limit:1500}") int dateLimit,
            StockMacroFeatureService stockMacroFeatureService,
            ConfigurableApplicationContext applicationContext) {
        this.enabled = enabled;
        this.exitOnComplete = exitOnComplete;
        this.indexCode = indexCode;
        this.indexCodes = indexCodes;
        this.dateLimit = dateLimit;
        this.stockMacroFeatureService = stockMacroFeatureService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        int exitCode = 0;
        try {
            for (String targetIndexCode : targetIndexCodes()) {
                int saved = stockMacroFeatureService.refreshMacroFeatures(targetIndexCode, Math.max(1, dateLimit));
                System.out.println("Stock macro feature refresh indexCode=" + targetIndexCode
                        + ", savedRows=" + saved
                        + ", dateLimit=" + Math.max(1, dateLimit));
            }
        } catch (RuntimeException ex) {
            exitCode = 1;
            throw ex;
        } finally {
            if (exitOnComplete) {
                int code = exitCode;
                Thread shutdown = new Thread(() -> System.exit(SpringApplication.exit(applicationContext, () -> code)));
                shutdown.setDaemon(false);
                shutdown.start();
            }
        }
    }

    private Set<String> targetIndexCodes() {
        Set<String> normalized = new LinkedHashSet<>();
        if (indexCodes != null && !indexCodes.isBlank()) {
            for (String token : indexCodes.split(",")) {
                addNormalizedIndexCode(normalized, token);
            }
        }
        if (normalized.isEmpty()) {
            addNormalizedIndexCode(normalized, indexCode);
        }
        if (normalized.isEmpty()) {
            normalized.add("SP500");
        }
        return normalized;
    }

    private static void addNormalizedIndexCode(Set<String> normalized, String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace("-", "").replace("_", "");
        if (value.isBlank()) {
            return;
        }
        if ("NASDAQ100".equals(value) || "NDX".equals(value)) {
            normalized.add("NASDAQ100");
        } else if ("DOW30".equals(value) || "DJI".equals(value)) {
            normalized.add("DOW30");
        } else {
            normalized.add("SP500");
        }
    }
}
