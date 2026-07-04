package com.kingyurina.demo.stock;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class StockCovarianceSnapshotBatchRunner implements ApplicationRunner {

    private final boolean enabled;
    private final boolean exitOnComplete;
    private final String indexCode;
    private final String indexCodes;
    private final int dateLimit;
    private final int candidateLimit;
    private final int lookbackDays;
    private final StockPortfolioBacktestService stockPortfolioBacktestService;
    private final ConfigurableApplicationContext applicationContext;

    public StockCovarianceSnapshotBatchRunner(
            @Value("${app.batch.covariance-snapshot.enabled:false}") boolean enabled,
            @Value("${app.batch.covariance-snapshot.exit-on-complete:false}") boolean exitOnComplete,
            @Value("${app.batch.covariance-snapshot.index-code:SP500}") String indexCode,
            @Value("${app.batch.covariance-snapshot.index-codes:}") String indexCodes,
            @Value("${app.batch.covariance-snapshot.date-limit:80}") int dateLimit,
            @Value("${app.batch.covariance-snapshot.candidate-limit:120}") int candidateLimit,
            @Value("${app.batch.covariance-snapshot.lookback-days:126}") int lookbackDays,
            StockPortfolioBacktestService stockPortfolioBacktestService,
            ConfigurableApplicationContext applicationContext) {
        this.enabled = enabled;
        this.exitOnComplete = exitOnComplete;
        this.indexCode = indexCode;
        this.indexCodes = indexCodes;
        this.dateLimit = dateLimit;
        this.candidateLimit = candidateLimit;
        this.lookbackDays = lookbackDays;
        this.stockPortfolioBacktestService = stockPortfolioBacktestService;
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
                refreshIndex(targetIndexCode);
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

    private void refreshIndex(String targetIndexCode) {
            StockPortfolioBacktestService.CovarianceRefreshResult result =
                    stockPortfolioBacktestService.refreshCovarianceSnapshots(targetIndexCode,
                            Math.max(1, dateLimit),
                            Math.max(2, candidateLimit),
                            Math.max(40, lookbackDays));
            StockPortfolioBacktestService.CovarianceRefreshResult liveResult =
                    stockPortfolioBacktestService.refreshLatestCovarianceSnapshot(targetIndexCode,
                            Math.max(2, candidateLimit),
                            Math.max(40, lookbackDays));
            System.out.println("Stock covariance snapshot refresh indexCode=" + targetIndexCode
                    + ", processedDates=" + result.processedDates()
                    + ", candidateRows=" + result.candidateRows()
                    + ", savedPairs=" + result.savedPairs()
                    + ", lookbackDays=" + result.lookbackDays());
            System.out.println("Stock live covariance snapshot refresh indexCode=" + targetIndexCode
                    + ", processedDates=" + liveResult.processedDates()
                    + ", candidateRows=" + liveResult.candidateRows()
                    + ", savedPairs=" + liveResult.savedPairs()
                    + ", lookbackDays=" + liveResult.lookbackDays());
    }

    private List<String> targetIndexCodes() {
        Set<String> normalized = new LinkedHashSet<>();
        if (indexCodes != null && !indexCodes.isBlank()) {
            for (String token : indexCodes.split(",")) {
                addNormalizedIndexCode(normalized, token);
            }
        }
        if (normalized.isEmpty()) {
            addNormalizedIndexCode(normalized, indexCode);
        }
        return normalized.isEmpty() ? List.of("SP500") : List.copyOf(normalized);
    }

    private static void addNormalizedIndexCode(Set<String> normalized, String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace("-", "").replace("_", "");
        if (value.isBlank()) {
            return;
        }
        if ("SP500".equals(value) || "S&P500".equals(value)) {
            normalized.add("SP500");
        } else if ("NASDAQ100".equals(value) || "NDX".equals(value)) {
            normalized.add("NASDAQ100");
        } else if ("DOW30".equals(value) || "DJI".equals(value)) {
            normalized.add("DOW30");
        } else {
            normalized.add(value);
        }
    }
}
