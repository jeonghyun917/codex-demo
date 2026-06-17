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
public class SecEdgarBatchRunner implements ApplicationRunner {

    private static final String JOB_NAME = "sec-edgar-companyfacts-refresh";

    private final boolean enabled;
    private final boolean syncEnabled;
    private final boolean exitOnComplete;
    private final int symbolLimit;
    private final String indexCodes;
    private final String symbols;
    private final boolean rebuildStandard;
    private final SecEdgarService secEdgarService;
    private final SecFinancialStandardService secFinancialStandardService;
    private final StockSignalRefreshService stockSignalRefreshService;
    private final ObjectProvider<IndexConstituentMapper> indexConstituentMapper;
    private final ObjectProvider<StockSymbolMapper> stockSymbolMapper;
    private final ObjectProvider<SecEdgarBatchRunMapper> batchRunMapper;
    private final ConfigurableApplicationContext applicationContext;

    public SecEdgarBatchRunner(
            @Value("${app.batch.sec-edgar.enabled:false}") boolean enabled,
            @Value("${app.batch.sec-edgar.sync-enabled:true}") boolean syncEnabled,
            @Value("${app.batch.sec-edgar.exit-on-complete:false}") boolean exitOnComplete,
            @Value("${app.batch.sec-edgar.symbol-limit:1000}") int symbolLimit,
            @Value("${app.batch.sec-edgar.index-codes:SP500,NASDAQ100,DOW30}") String indexCodes,
            @Value("${app.batch.sec-edgar.symbols:}") String symbols,
            @Value("${app.batch.sec-edgar.rebuild-standard:false}") boolean rebuildStandard,
            SecEdgarService secEdgarService,
            SecFinancialStandardService secFinancialStandardService,
            StockSignalRefreshService stockSignalRefreshService,
            ObjectProvider<IndexConstituentMapper> indexConstituentMapper,
            ObjectProvider<StockSymbolMapper> stockSymbolMapper,
            ObjectProvider<SecEdgarBatchRunMapper> batchRunMapper,
            ConfigurableApplicationContext applicationContext) {
        this.enabled = enabled;
        this.syncEnabled = syncEnabled;
        this.exitOnComplete = exitOnComplete;
        this.symbolLimit = symbolLimit;
        this.indexCodes = indexCodes;
        this.symbols = symbols;
        this.rebuildStandard = rebuildStandard;
        this.secEdgarService = secEdgarService;
        this.secFinancialStandardService = secFinancialStandardService;
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
        StockSymbolMapper symbolMapper = requireMapper(stockSymbolMapper, "StockSymbolMapper");
        SecEdgarBatchRunMapper runMapper = requireMapper(batchRunMapper, "SecEdgarBatchRunMapper");

        SecEdgarBatchRun run = new SecEdgarBatchRun(JOB_NAME, "RUNNING");
        runMapper.insert(run);

        try {
            List<String> targetIndexCodes = targetIndexCodes();
            List<String> explicitSymbols = explicitSymbols();
            List<String> targetSymbols = explicitSymbols.isEmpty()
                    ? targetSymbols(symbolMapper, targetIndexCodes, Math.max(1, symbolLimit))
                    : explicitSymbols;
            SecEdgarService.SecEdgarSyncSummary summary = syncEnabled
                    ? secEdgarService.syncSymbols(targetSymbols, Math.max(1, symbolLimit))
                    : skippedSyncSummary(targetSymbols.size());
            int rebuiltStandardRows = rebuildStandard ? rebuildStandard(targetSymbols) : 0;
            StockSignalRefreshService.RefreshResult signalRefresh = stockSignalRefreshService
                    .recalculateSymbols(targetSymbols);

            run.setFinishedAt(LocalDateTime.now());
            run.setRequestedCount(summary.requestedCount());
            run.setSuccessCount(summary.successCount());
            run.setFailCount(summary.failCount());
            run.setSkippedCount(summary.skippedCount());
            run.setStatus(summary.failCount() == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS");
            String targetMessage = !explicitSymbols.isEmpty()
                    ? "Target symbols=" + String.join(",", explicitSymbols) + ". "
                    : targetIndexCodes.isEmpty()
                    ? "Target symbols=active stock_symbol. "
                    : "Target indexes=" + String.join(",", targetIndexCodes) + ". ";
            run.setMessage(targetMessage + summary.message()
                    + (rebuildStandard ? " Rebuilt standard rows " + rebuiltStandardRows + "." : "")
                    + " Signal refreshed "
                    + signalRefresh.success() + "/" + signalRefresh.requested() + ".");
            runMapper.finish(run);
        } catch (RuntimeException ex) {
            run.setFinishedAt(LocalDateTime.now());
            run.setStatus("FAILED");
            run.setMessage(ex.getMessage());
            runMapper.finish(run);
            throw ex;
        }
    }

    private List<String> targetSymbols(StockSymbolMapper symbolMapper, List<String> targetIndexCodes, int limit) {
        if (!targetIndexCodes.isEmpty()) {
            IndexConstituentMapper constituentMapper = requireMapper(indexConstituentMapper, "IndexConstituentMapper");
            List<String> indexSymbols = constituentMapper.findCurrentSymbolsByIndexCodes(targetIndexCodes, limit);
            if (!indexSymbols.isEmpty()) {
                return indexSymbols;
            }
        }
        return symbolMapper.findActiveSymbols(limit);
    }

    private int rebuildStandard(List<String> symbols) {
        int rows = 0;
        for (String symbol : symbols) {
            rows += secFinancialStandardService.rebuildFromDatabase(symbol);
        }
        return rows;
    }

    private SecEdgarService.SecEdgarSyncSummary skippedSyncSummary(int requestedCount) {
        return new SecEdgarService.SecEdgarSyncSummary(
                requestedCount,
                0,
                0,
                requestedCount,
                0,
                0,
                0,
                "SEC sync skipped; using existing stock_sec_fact rows.");
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
