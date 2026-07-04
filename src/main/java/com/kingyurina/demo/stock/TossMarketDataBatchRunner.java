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
public class TossMarketDataBatchRunner implements ApplicationRunner {

    private static final String JOB_NAME = "toss-market-data-refresh";

    private final boolean enabled;
    private final boolean exitOnComplete;
    private final int symbolLimit;
    private final int candleCount;
    private final int tradeCount;
    private final long delayMillis;
    private final String indexCodes;
    private final String symbols;
    private final boolean prices;
    private final boolean stockInfo;
    private final boolean candles;
    private final boolean warnings;
    private final boolean priceLimits;
    private final boolean exchangeRate;
    private final boolean calendar;
    private final boolean orderbook;
    private final boolean trades;
    private final TossMarketDataService service;
    private final ObjectProvider<IndexConstituentMapper> indexConstituentMapper;
    private final ObjectProvider<StockSymbolMapper> stockSymbolMapper;
    private final ObjectProvider<TossMarketDataMapper> tossMarketDataMapper;
    private final ConfigurableApplicationContext applicationContext;

    public TossMarketDataBatchRunner(
            @Value("${app.batch.toss.enabled:false}") boolean enabled,
            @Value("${app.batch.toss.exit-on-complete:false}") boolean exitOnComplete,
            @Value("${app.batch.toss.symbol-limit:100}") int symbolLimit,
            @Value("${app.batch.toss.candle-count:120}") int candleCount,
            @Value("${app.batch.toss.trade-count:20}") int tradeCount,
            @Value("${app.batch.toss.delay-millis:250}") long delayMillis,
            @Value("${app.batch.toss.index-codes:SP500}") String indexCodes,
            @Value("${app.batch.toss.symbols:}") String symbols,
            @Value("${app.batch.toss.prices:true}") boolean prices,
            @Value("${app.batch.toss.stock-info:true}") boolean stockInfo,
            @Value("${app.batch.toss.candles:true}") boolean candles,
            @Value("${app.batch.toss.warnings:true}") boolean warnings,
            @Value("${app.batch.toss.price-limits:false}") boolean priceLimits,
            @Value("${app.batch.toss.exchange-rate:true}") boolean exchangeRate,
            @Value("${app.batch.toss.calendar:true}") boolean calendar,
            @Value("${app.batch.toss.orderbook:false}") boolean orderbook,
            @Value("${app.batch.toss.trades:false}") boolean trades,
            TossMarketDataService service,
            ObjectProvider<IndexConstituentMapper> indexConstituentMapper,
            ObjectProvider<StockSymbolMapper> stockSymbolMapper,
            ObjectProvider<TossMarketDataMapper> tossMarketDataMapper,
            ConfigurableApplicationContext applicationContext) {
        this.enabled = enabled;
        this.exitOnComplete = exitOnComplete;
        this.symbolLimit = symbolLimit;
        this.candleCount = candleCount;
        this.tradeCount = tradeCount;
        this.delayMillis = delayMillis;
        this.indexCodes = indexCodes;
        this.symbols = symbols;
        this.prices = prices;
        this.stockInfo = stockInfo;
        this.candles = candles;
        this.warnings = warnings;
        this.priceLimits = priceLimits;
        this.exchangeRate = exchangeRate;
        this.calendar = calendar;
        this.orderbook = orderbook;
        this.trades = trades;
        this.service = service;
        this.indexConstituentMapper = indexConstituentMapper;
        this.stockSymbolMapper = stockSymbolMapper;
        this.tossMarketDataMapper = tossMarketDataMapper;
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
        TossMarketDataMapper mapper = requireMapper(tossMarketDataMapper, "TossMarketDataMapper");
        TossBatchRun run = new TossBatchRun(JOB_NAME, "RUNNING");
        mapper.insertBatchRun(run);

        try {
            List<String> explicitSymbols = explicitSymbols();
            List<String> targetSymbols = explicitSymbols.isEmpty()
                    ? targetSymbols(Math.max(1, symbolLimit))
                    : explicitSymbols;
            TossMarketDataService.SyncOptions options = new TossMarketDataService.SyncOptions(
                    Math.max(1, symbolLimit),
                    Math.max(1, Math.min(200, candleCount)),
                    Math.max(1, Math.min(50, tradeCount)),
                    Math.max(0L, delayMillis),
                    prices,
                    stockInfo,
                    candles,
                    warnings,
                    priceLimits,
                    exchangeRate,
                    calendar,
                    orderbook,
                    trades);
            TossMarketDataService.SyncSummary summary = service.sync(targetSymbols, options);
            String targetMessage = !explicitSymbols.isEmpty()
                    ? "Target symbols=" + String.join(",", explicitSymbols) + ". "
                    : "Target indexes=" + String.join(",", targetIndexCodes()) + ". ";
            String message = targetMessage
                    + "savedRows=" + summary.savedRows()
                    + ", requestedCalls=" + summary.requested()
                    + ", successCalls=" + summary.success()
                    + ", failCalls=" + summary.fail() + ".";
            if (!summary.failedItems().isEmpty()) {
                message += " Failed=" + String.join(" | ", summary.failedItems().stream().limit(20).toList());
            }
            run.setFinishedAt(LocalDateTime.now());
            run.setRequestedCount(summary.requested());
            run.setSuccessCount(summary.success());
            run.setFailCount(summary.fail());
            run.setSkippedCount(summary.skipped());
            run.setSavedRows(summary.savedRows());
            run.setStatus(summary.fail() == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS");
            run.setMessage(message);
            mapper.finishBatchRun(run);
            System.out.println("Toss market data sync requested=" + summary.requested()
                    + ", success=" + summary.success()
                    + ", fail=" + summary.fail()
                    + ", savedRows=" + summary.savedRows());
        } catch (RuntimeException ex) {
            run.setFinishedAt(LocalDateTime.now());
            run.setStatus("FAILED");
            run.setMessage(ex.getMessage());
            mapper.finishBatchRun(run);
            throw ex;
        }
    }

    private List<String> targetSymbols(int limit) {
        List<String> targetIndexCodes = targetIndexCodes();
        if (!targetIndexCodes.isEmpty()) {
            IndexConstituentMapper mapper = requireMapper(indexConstituentMapper, "IndexConstituentMapper");
            List<String> symbolsByIndex = mapper.findCurrentSymbolsByIndexCodes(targetIndexCodes, limit);
            if (!symbolsByIndex.isEmpty()) {
                return symbolsByIndex;
            }
        }
        StockSymbolMapper mapper = requireMapper(stockSymbolMapper, "StockSymbolMapper");
        return mapper.findActiveSymbols(limit);
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
