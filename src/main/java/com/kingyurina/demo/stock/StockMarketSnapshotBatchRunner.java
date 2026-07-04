package com.kingyurina.demo.stock;

import java.time.LocalDate;
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
public class StockMarketSnapshotBatchRunner implements ApplicationRunner {

    private final boolean enabled;
    private final boolean exitOnComplete;
    private final String indexCode;
    private final String indexCodes;
    private final int years;
    private final String fromDate;
    private final String toDate;
    private final StockPortfolioBacktestService stockPortfolioBacktestService;
    private final ConfigurableApplicationContext applicationContext;

    public StockMarketSnapshotBatchRunner(
            @Value("${app.batch.market-snapshot.enabled:false}") boolean enabled,
            @Value("${app.batch.market-snapshot.exit-on-complete:false}") boolean exitOnComplete,
            @Value("${app.batch.market-snapshot.index-code:SP500}") String indexCode,
            @Value("${app.batch.market-snapshot.index-codes:}") String indexCodes,
            @Value("${app.batch.market-snapshot.years:5}") int years,
            @Value("${app.batch.market-snapshot.from-date:}") String fromDate,
            @Value("${app.batch.market-snapshot.to-date:}") String toDate,
            StockPortfolioBacktestService stockPortfolioBacktestService,
            ConfigurableApplicationContext applicationContext) {
        this.enabled = enabled;
        this.exitOnComplete = exitOnComplete;
        this.indexCode = indexCode;
        this.indexCodes = indexCodes;
        this.years = years;
        this.fromDate = fromDate;
        this.toDate = toDate;
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
            LocalDate end = parseDate(toDate, LocalDate.now());
            LocalDate start = parseDate(fromDate, end.minusYears(Math.max(1, years)));
            for (String targetIndexCode : targetIndexCodes()) {
                refreshIndex(targetIndexCode, start, end);
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

    private void refreshIndex(String targetIndexCode, LocalDate start, LocalDate end) {
        StockPortfolioBacktestService.MarketSnapshotRefreshResult result =
                stockPortfolioBacktestService.refreshMarketSnapshotsAndBenchmarks(targetIndexCode, start, end);
        System.out.println("Stock market snapshot refresh indexCode=" + targetIndexCode
                + ", from=" + start
                + ", to=" + end
                + ", marketRows=" + result.loadedMarketRows()
                + ", benchmarkRows=" + result.savedBenchmarkRows()
                + ", tossShareRows=" + result.tossShareRows()
                + ", fallbackRows=" + result.fallbackMarketCapRows()
                + ", membershipRows=" + result.membershipRows()
                + ", sharesRows=" + result.sharesRows());
    }

    private static LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return LocalDate.parse(value.trim());
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
