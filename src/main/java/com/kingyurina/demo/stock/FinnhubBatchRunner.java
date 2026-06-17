package com.kingyurina.demo.stock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.ibatis.exceptions.PersistenceException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class FinnhubBatchRunner implements ApplicationRunner {

    private static final String JOB_NAME = "finnhub-symbol-refresh";

    private final boolean enabled;
    private final boolean exitOnComplete;
    private final int symbolLimit;
    private final long delayMillis;
    private final String indexCodes;
    private final FinnhubProperties properties;
    private final StockAnalysisService stockAnalysisService;
    private final StockSignalRefreshService stockSignalRefreshService;
    private final IndexConstituentSyncService indexConstituentSyncService;
    private final ObjectProvider<IndexConstituentMapper> indexConstituentMapper;
    private final ObjectProvider<StockSymbolMapper> stockSymbolMapper;
    private final ObjectProvider<FinnhubBatchRunMapper> batchRunMapper;
    private final ConfigurableApplicationContext applicationContext;

    public FinnhubBatchRunner(
            @Value("${app.batch.finnhub.enabled:false}") boolean enabled,
            @Value("${app.batch.finnhub.exit-on-complete:false}") boolean exitOnComplete,
            @Value("${app.batch.finnhub.symbol-limit:10}") int symbolLimit,
            @Value("${app.batch.finnhub.delay-millis:4000}") long delayMillis,
            @Value("${app.batch.finnhub.index-codes:}") String indexCodes,
            FinnhubProperties properties,
            StockAnalysisService stockAnalysisService,
            StockSignalRefreshService stockSignalRefreshService,
            IndexConstituentSyncService indexConstituentSyncService,
            ObjectProvider<IndexConstituentMapper> indexConstituentMapper,
            ObjectProvider<StockSymbolMapper> stockSymbolMapper,
            ObjectProvider<FinnhubBatchRunMapper> batchRunMapper,
            ConfigurableApplicationContext applicationContext) {
        this.enabled = enabled;
        this.exitOnComplete = exitOnComplete;
        this.symbolLimit = symbolLimit;
        this.delayMillis = delayMillis;
        this.indexCodes = indexCodes;
        this.properties = properties;
        this.stockAnalysisService = stockAnalysisService;
        this.stockSignalRefreshService = stockSignalRefreshService;
        this.indexConstituentSyncService = indexConstituentSyncService;
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
        StockSymbolMapper symbolMapper = requireMapper(stockSymbolMapper, "StockSymbolMapper");
        FinnhubBatchRunMapper runMapper = requireMapper(batchRunMapper, "FinnhubBatchRunMapper");

        FinnhubBatchRun run = new FinnhubBatchRun(JOB_NAME, "RUNNING");
        if (!properties.hasApiKey()) {
            run.setStatus("FAILED");
            run.setMessage("FINNHUB_API_KEY is not configured.");
            runMapper.insert(run);
            run.setFinishedAt(LocalDateTime.now());
            runMapper.finish(run);
            throw new IllegalStateException(run.getMessage());
        }

        IndexConstituentSyncService.SyncSummary syncSummary;
        try {
            syncSummary = indexConstituentSyncService.syncEnabledIfStale();
        } catch (RuntimeException ex) {
            syncSummary = new IndexConstituentSyncService.SyncSummary(List.of(
                    IndexConstituentSyncService.SyncResult.failed("ALL", ex.getMessage())));
        }

        runMapper.insert(run);
        int requested = 0;
        int success = 0;
        int fail = 0;
        int skipped = 0;
        String syncMessage = syncSummary.message();
        String message = syncMessage == null ? null : "Index sync " + syncMessage + " ";
        try {
            List<String> targetIndexCodes = targetIndexCodes();
            List<String> symbols = targetSymbols(symbolMapper, targetIndexCodes, Math.max(1, symbolLimit));
            List<String> refreshedSymbols = new ArrayList<>();
            message = appendMessage(message, targetIndexCodes.isEmpty()
                    ? "Target symbols=active stock_symbol."
                    : "Target indexes=" + String.join(",", targetIndexCodes) + ".");
            for (String symbol : symbols) {
                requested++;
                try {
                    StockDashboard dashboard = stockAnalysisService.dashboard(symbol);
                    if (dashboard.quote() != null || dashboard.profile() != null || dashboard.metric() != null
                            || !dashboard.recommendations().isEmpty() || !dashboard.epsSurprises().isEmpty()) {
                        success++;
                        symbolMapper.markCollected(symbol);
                        refreshedSymbols.add(symbol);
                    } else {
                        fail++;
                    }
                } catch (PersistenceException ex) {
                    fail++;
                    message = "DB error at " + symbol + ": " + ex.getMessage();
                } catch (RuntimeException ex) {
                    fail++;
                    message = "Runtime error at " + symbol + ": " + ex.getMessage();
                }

                if (fail > 0 && message != null && message.contains("429")) {
                    skipped += symbols.size() - requested;
                    break;
                }
                sleepBetweenSymbols();
            }
            StockSignalRefreshService.RefreshResult signalRefresh = refreshedSymbols.isEmpty()
                    ? new StockSignalRefreshService.RefreshResult(0, 0, 0)
                    : stockSignalRefreshService.recalculateSymbols(refreshedSymbols);

            run.setFinishedAt(LocalDateTime.now());
            run.setRequestedCount(requested);
            run.setSuccessCount(success);
            run.setFailCount(fail);
            run.setSkippedCount(skipped);
            run.setStatus(fail == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS");
            String processedMessage = "Processed " + requested + " symbols. Signal refreshed "
                    + signalRefresh.success() + "/" + signalRefresh.requested() + ".";
            run.setMessage(message == null ? processedMessage : message + processedMessage);
            runMapper.finish(run);
        } catch (RuntimeException ex) {
            run.setFinishedAt(LocalDateTime.now());
            run.setRequestedCount(requested);
            run.setSuccessCount(success);
            run.setFailCount(Math.max(1, fail));
            run.setSkippedCount(skipped);
            run.setStatus("FAILED");
            run.setMessage(appendMessage(message, "Batch failed: " + ex.getMessage()));
            runMapper.finish(run);
            throw ex;
        }
    }

    private List<String> targetSymbols(StockSymbolMapper symbolMapper, List<String> targetIndexCodes, int limit) {
        if (targetIndexCodes.isEmpty()) {
            return symbolMapper.findActiveSymbols(limit);
        }
        IndexConstituentMapper constituentMapper = requireMapper(indexConstituentMapper, "IndexConstituentMapper");
        return constituentMapper.findCurrentSymbolsByIndexCodes(targetIndexCodes, limit);
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

    private static String appendMessage(String current, String addition) {
        if (addition == null || addition.isBlank()) {
            return current;
        }
        if (current == null || current.isBlank()) {
            return addition + " ";
        }
        return current + addition + " ";
    }

    private void sleepBetweenSymbols() {
        if (delayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Batch interrupted.", ex);
        }
    }

    private static <T> T requireMapper(ObjectProvider<T> provider, String name) {
        T mapper = provider.getIfAvailable();
        if (mapper == null) {
            throw new IllegalStateException(name + " is not available. Run with the mariadb profile.");
        }
        return mapper;
    }
}
