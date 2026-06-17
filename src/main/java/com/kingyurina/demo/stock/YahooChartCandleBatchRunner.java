package com.kingyurina.demo.stock;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class YahooChartCandleBatchRunner implements ApplicationRunner {

    private final boolean enabled;
    private final boolean exitOnComplete;
    private final int symbolLimit;
    private final int years;
    private final long delayMillis;
    private final String indexCodes;
    private final String symbols;
    private final YahooChartCandleService yahooChartCandleService;
    private final StockSignalRefreshService stockSignalRefreshService;
    private final ObjectProvider<IndexConstituentMapper> indexConstituentMapper;
    private final ObjectProvider<StockSymbolMapper> stockSymbolMapper;
    private final ObjectProvider<YahooCandleBatchRunMapper> batchRunMapper;
    private final ConfigurableApplicationContext applicationContext;

    private static final String JOB_NAME = "yahoo-chart-candle-refresh";

    public YahooChartCandleBatchRunner(
            @Value("${app.batch.yahoo-candle.enabled:false}") boolean enabled,
            @Value("${app.batch.yahoo-candle.exit-on-complete:false}") boolean exitOnComplete,
            @Value("${app.batch.yahoo-candle.symbol-limit:1000}") int symbolLimit,
            @Value("${app.batch.yahoo-candle.years:${yahoo-chart.default-years:5}}") int years,
            @Value("${app.batch.yahoo-candle.delay-millis:${yahoo-chart.request-delay-millis:250}}") long delayMillis,
            @Value("${app.batch.yahoo-candle.index-codes:SP500,NASDAQ100,DOW30}") String indexCodes,
            @Value("${app.batch.yahoo-candle.symbols:}") String symbols,
            YahooChartCandleService yahooChartCandleService,
            StockSignalRefreshService stockSignalRefreshService,
            ObjectProvider<IndexConstituentMapper> indexConstituentMapper,
            ObjectProvider<StockSymbolMapper> stockSymbolMapper,
            ObjectProvider<YahooCandleBatchRunMapper> batchRunMapper,
            ConfigurableApplicationContext applicationContext) {
        this.enabled = enabled;
        this.exitOnComplete = exitOnComplete;
        this.symbolLimit = symbolLimit;
        this.years = years;
        this.delayMillis = delayMillis;
        this.indexCodes = indexCodes;
        this.symbols = symbols;
        this.yahooChartCandleService = yahooChartCandleService;
        this.stockSignalRefreshService = stockSignalRefreshService;
        this.indexConstituentMapper = indexConstituentMapper;
        this.stockSymbolMapper = stockSymbolMapper;
        this.batchRunMapper = batchRunMapper;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        int exitCode = 0;
        try {
            runBatch();
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

    private void runBatch() {
        YahooCandleBatchRunMapper runMapper = requireMapper(batchRunMapper, "YahooCandleBatchRunMapper");
        YahooCandleBatchRun run = new YahooCandleBatchRun(JOB_NAME, "RUNNING");
        runMapper.insert(run);

        try {
            List<String> explicitSymbols = explicitSymbols();
            List<String> targetSymbols = explicitSymbols.isEmpty()
                    ? targetSymbols(Math.max(1, symbolLimit))
                    : explicitSymbols;
            YahooChartCandleService.SyncSummary candleSummary = yahooChartCandleService.syncSymbols(
                    targetSymbols,
                    years,
                    delayMillis);
            StockSignalRefreshService.RefreshResult signalRefresh = stockSignalRefreshService
                    .recalculateSymbols(targetSymbols);
            String targetMessage = !explicitSymbols.isEmpty()
                    ? "Target symbols=" + String.join(",", explicitSymbols) + ". "
                    : "Target indexes=" + String.join(",", targetIndexCodes()) + ". ";
            String message = targetMessage
                    + "Yahoo chart candle sync savedRows=" + candleSummary.savedRows()
                    + ". Signal refreshed " + signalRefresh.success() + "/" + signalRefresh.requested() + ".";
            if (!candleSummary.failedSymbols().isEmpty()) {
                message += " Failed symbols="
                        + String.join(",", candleSummary.failedSymbols().stream().limit(30).toList()) + ".";
            }
            run.setFinishedAt(LocalDateTime.now());
            run.setRequestedCount(candleSummary.requested());
            run.setSuccessCount(candleSummary.success());
            run.setFailCount(candleSummary.fail());
            run.setSkippedCount(0);
            run.setSavedRows(candleSummary.savedRows());
            run.setSignalRefreshedCount(signalRefresh.success());
            run.setStatus(candleSummary.fail() == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS");
            run.setMessage(message);
            runMapper.finish(run);
            System.out.println("Yahoo chart candle sync requested=" + candleSummary.requested()
                    + ", success=" + candleSummary.success()
                    + ", fail=" + candleSummary.fail()
                    + ", savedRows=" + candleSummary.savedRows()
                    + ", signalRefreshed=" + signalRefresh.success() + "/" + signalRefresh.requested());
        } catch (RuntimeException ex) {
            run.setFinishedAt(LocalDateTime.now());
            run.setStatus("FAILED");
            run.setMessage(ex.getMessage());
            runMapper.finish(run);
            throw ex;
        }
    }

    private List<String> targetSymbols(int limit) {
        List<String> targetIndexCodes = targetIndexCodes();
        if (!targetIndexCodes.isEmpty()) {
            IndexConstituentMapper constituentMapper = requireMapper(indexConstituentMapper, "IndexConstituentMapper");
            List<String> symbolsByIndex = constituentMapper.findCurrentSymbolsByIndexCodes(targetIndexCodes, limit);
            if (!symbolsByIndex.isEmpty()) {
                return symbolsByIndex;
            }
        }
        StockSymbolMapper symbolMapper = requireMapper(stockSymbolMapper, "StockSymbolMapper");
        return symbolMapper.findActiveSymbols(limit);
    }

    private List<String> explicitSymbols() {
        if (symbols == null || symbols.isBlank()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String token : symbols.split(",")) {
            String symbol = token == null ? "" : token.trim().toUpperCase(Locale.ROOT);
            if (!symbol.isBlank()) {
                normalized.add(symbol);
            }
        }
        return List.copyOf(normalized);
    }

    private List<String> targetIndexCodes() {
        if (indexCodes == null || indexCodes.isBlank()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String token : indexCodes.split(",")) {
            String indexCode = normalizeIndexCode(token);
            if (!indexCode.isBlank()) {
                normalized.add(indexCode);
            }
        }
        return List.copyOf(normalized);
    }

    private static String normalizeIndexCode(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace("-", "").replace("_", "");
        if ("SP500".equals(normalized) || "S&P500".equals(normalized)) {
            return "SP500";
        }
        if ("NASDAQ100".equals(normalized) || "NDX".equals(normalized)) {
            return "NASDAQ100";
        }
        if ("DOW30".equals(normalized) || "DOWJONES30".equals(normalized) || "DJI".equals(normalized)) {
            return "DOW30";
        }
        return normalized;
    }

    private static <T> T requireMapper(ObjectProvider<T> provider, String name) {
        T mapper = provider.getIfAvailable();
        if (mapper == null) {
            throw new IllegalStateException(name + " is not available. Run with the mariadb profile.");
        }
        return mapper;
    }
}
