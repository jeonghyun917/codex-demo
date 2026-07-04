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
public class StockExpectedReturnBatchRunner implements ApplicationRunner {

    private final boolean enabled;
    private final boolean exitOnComplete;
    private final String indexCode;
    private final String indexCodes;
    private final int resultLimit;
    private final int dateLimit;
    private final int minTrainingRows;
    private final boolean historicalEnabled;
    private final boolean liveEnabled;
    private final boolean v3Enabled;
    private final boolean v3HistoricalEnabled;
    private final boolean v3LiveEnabled;
    private final boolean v4Enabled;
    private final boolean v4HistoricalEnabled;
    private final boolean v4LiveEnabled;
    private final boolean v5Enabled;
    private final boolean v5HistoricalEnabled;
    private final boolean v5LiveEnabled;
    private final boolean v6Enabled;
    private final boolean v6HistoricalEnabled;
    private final boolean v6LiveEnabled;
    private final boolean v7Enabled;
    private final boolean v7HistoricalEnabled;
    private final boolean v7LiveEnabled;
    private final boolean v8Enabled;
    private final boolean v8HistoricalEnabled;
    private final boolean v8LiveEnabled;
    private final boolean v9Enabled;
    private final boolean v9HistoricalEnabled;
    private final boolean v9LiveEnabled;
    private final StockPortfolioBacktestService stockPortfolioBacktestService;
    private final StockMacroFeatureService stockMacroFeatureService;
    private final StockExpectedReturnV8Service stockExpectedReturnV8Service;
    private final StockExpectedReturnV9Service stockExpectedReturnV9Service;
    private final ConfigurableApplicationContext applicationContext;

    public StockExpectedReturnBatchRunner(
            @Value("${app.batch.expected-return.enabled:false}") boolean enabled,
            @Value("${app.batch.expected-return.exit-on-complete:false}") boolean exitOnComplete,
            @Value("${app.batch.expected-return.index-code:SP500}") String indexCode,
            @Value("${app.batch.expected-return.index-codes:}") String indexCodes,
            @Value("${app.batch.expected-return.result-limit:100000}") int resultLimit,
            @Value("${app.batch.expected-return.date-limit:80}") int dateLimit,
            @Value("${app.batch.expected-return.min-training-rows:300}") int minTrainingRows,
            @Value("${app.batch.expected-return.historical-enabled:true}") boolean historicalEnabled,
            @Value("${app.batch.expected-return.live-enabled:true}") boolean liveEnabled,
            @Value("${app.batch.expected-return.v3-enabled:true}") boolean v3Enabled,
            @Value("${app.batch.expected-return.v3-historical-enabled:true}") boolean v3HistoricalEnabled,
            @Value("${app.batch.expected-return.v3-live-enabled:true}") boolean v3LiveEnabled,
            @Value("${app.batch.expected-return.v4-enabled:true}") boolean v4Enabled,
            @Value("${app.batch.expected-return.v4-historical-enabled:true}") boolean v4HistoricalEnabled,
            @Value("${app.batch.expected-return.v4-live-enabled:true}") boolean v4LiveEnabled,
            @Value("${app.batch.expected-return.v5-enabled:true}") boolean v5Enabled,
            @Value("${app.batch.expected-return.v5-historical-enabled:true}") boolean v5HistoricalEnabled,
            @Value("${app.batch.expected-return.v5-live-enabled:true}") boolean v5LiveEnabled,
            @Value("${app.batch.expected-return.v6-enabled:true}") boolean v6Enabled,
            @Value("${app.batch.expected-return.v6-historical-enabled:true}") boolean v6HistoricalEnabled,
            @Value("${app.batch.expected-return.v6-live-enabled:true}") boolean v6LiveEnabled,
            @Value("${app.batch.expected-return.v7-enabled:true}") boolean v7Enabled,
            @Value("${app.batch.expected-return.v7-historical-enabled:true}") boolean v7HistoricalEnabled,
            @Value("${app.batch.expected-return.v7-live-enabled:true}") boolean v7LiveEnabled,
            @Value("${app.batch.expected-return.v8-enabled:true}") boolean v8Enabled,
            @Value("${app.batch.expected-return.v8-historical-enabled:true}") boolean v8HistoricalEnabled,
            @Value("${app.batch.expected-return.v8-live-enabled:true}") boolean v8LiveEnabled,
            @Value("${app.batch.expected-return.v9-enabled:true}") boolean v9Enabled,
            @Value("${app.batch.expected-return.v9-historical-enabled:true}") boolean v9HistoricalEnabled,
            @Value("${app.batch.expected-return.v9-live-enabled:true}") boolean v9LiveEnabled,
            StockPortfolioBacktestService stockPortfolioBacktestService,
            StockMacroFeatureService stockMacroFeatureService,
            StockExpectedReturnV8Service stockExpectedReturnV8Service,
            StockExpectedReturnV9Service stockExpectedReturnV9Service,
            ConfigurableApplicationContext applicationContext) {
        this.enabled = enabled;
        this.exitOnComplete = exitOnComplete;
        this.indexCode = indexCode;
        this.indexCodes = indexCodes;
        this.resultLimit = resultLimit;
        this.dateLimit = dateLimit;
        this.minTrainingRows = minTrainingRows;
        this.historicalEnabled = historicalEnabled;
        this.liveEnabled = liveEnabled;
        this.v3Enabled = v3Enabled;
        this.v3HistoricalEnabled = v3HistoricalEnabled;
        this.v3LiveEnabled = v3LiveEnabled;
        this.v4Enabled = v4Enabled;
        this.v4HistoricalEnabled = v4HistoricalEnabled;
        this.v4LiveEnabled = v4LiveEnabled;
        this.v5Enabled = v5Enabled;
        this.v5HistoricalEnabled = v5HistoricalEnabled;
        this.v5LiveEnabled = v5LiveEnabled;
        this.v6Enabled = v6Enabled;
        this.v6HistoricalEnabled = v6HistoricalEnabled;
        this.v6LiveEnabled = v6LiveEnabled;
        this.v7Enabled = v7Enabled;
        this.v7HistoricalEnabled = v7HistoricalEnabled;
        this.v7LiveEnabled = v7LiveEnabled;
        this.stockPortfolioBacktestService = stockPortfolioBacktestService;
        this.v8Enabled = v8Enabled;
        this.v8HistoricalEnabled = v8HistoricalEnabled;
        this.v8LiveEnabled = v8LiveEnabled;
        this.v9Enabled = v9Enabled;
        this.v9HistoricalEnabled = v9HistoricalEnabled;
        this.v9LiveEnabled = v9LiveEnabled;
        this.stockMacroFeatureService = stockMacroFeatureService;
        this.stockExpectedReturnV8Service = stockExpectedReturnV8Service;
        this.stockExpectedReturnV9Service = stockExpectedReturnV9Service;
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
        StockPortfolioBacktestService.ExpectedReturnRefreshResult result =
                historicalEnabled
                        ? stockPortfolioBacktestService.refreshExpectedReturnSnapshots(targetIndexCode,
                                Math.max(1, resultLimit),
                                Math.max(1, dateLimit),
                                Math.max(50, minTrainingRows))
                        : new StockPortfolioBacktestService.ExpectedReturnRefreshResult(0, 0, 0,
                                Math.max(50, minTrainingRows), 0);
        StockPortfolioBacktestService.ExpectedReturnRefreshResult liveResult = liveEnabled
                ? stockPortfolioBacktestService.refreshLatestExpectedReturnSnapshots(targetIndexCode,
                        Math.max(1, resultLimit),
                        Math.max(50, minTrainingRows))
                : new StockPortfolioBacktestService.ExpectedReturnRefreshResult(0, 0, 0,
                        Math.max(50, minTrainingRows), 0);
        StockPortfolioBacktestService.ExpectedReturnRefreshResult v3Result =
                v3Enabled && v3HistoricalEnabled
                        ? stockPortfolioBacktestService.refreshExpectedReturnV3Snapshots(targetIndexCode,
                                Math.max(1, resultLimit),
                                Math.max(1, dateLimit),
                                Math.max(50, minTrainingRows))
                        : new StockPortfolioBacktestService.ExpectedReturnRefreshResult(0, 0, 0,
                                Math.max(50, minTrainingRows), 0);
        StockPortfolioBacktestService.ExpectedReturnRefreshResult v3LiveResult = v3Enabled && v3LiveEnabled
                ? stockPortfolioBacktestService.refreshLatestExpectedReturnV3Snapshots(targetIndexCode,
                        Math.max(1, resultLimit),
                        Math.max(50, minTrainingRows))
                : new StockPortfolioBacktestService.ExpectedReturnRefreshResult(0, 0, 0,
                        Math.max(50, minTrainingRows), 0);
        StockPortfolioBacktestService.ExpectedReturnRefreshResult v4Result =
                v4Enabled && v4HistoricalEnabled
                        ? stockPortfolioBacktestService.refreshExpectedReturnV4Snapshots(targetIndexCode,
                                Math.max(1, resultLimit),
                                Math.max(1, dateLimit),
                                Math.max(50, minTrainingRows))
                        : new StockPortfolioBacktestService.ExpectedReturnRefreshResult(0, 0, 0,
                                Math.max(50, minTrainingRows), 0);
        StockPortfolioBacktestService.ExpectedReturnRefreshResult v4LiveResult = v4Enabled && v4LiveEnabled
                ? stockPortfolioBacktestService.refreshLatestExpectedReturnV4Snapshots(targetIndexCode,
                        Math.max(1, resultLimit),
                        Math.max(50, minTrainingRows))
                : new StockPortfolioBacktestService.ExpectedReturnRefreshResult(0, 0, 0,
                        Math.max(50, minTrainingRows), 0);
        StockPortfolioBacktestService.ExpectedReturnRefreshResult v5Result =
                v5Enabled && v5HistoricalEnabled
                        ? stockPortfolioBacktestService.refreshExpectedReturnV5Snapshots(targetIndexCode,
                                Math.max(1, resultLimit),
                                Math.max(1, dateLimit),
                                Math.max(50, minTrainingRows))
                        : new StockPortfolioBacktestService.ExpectedReturnRefreshResult(0, 0, 0,
                                Math.max(50, minTrainingRows), 0);
        StockPortfolioBacktestService.ExpectedReturnRefreshResult v5LiveResult = v5Enabled && v5LiveEnabled
                ? stockPortfolioBacktestService.refreshLatestExpectedReturnV5Snapshots(targetIndexCode,
                        Math.max(1, resultLimit),
                        Math.max(50, minTrainingRows))
                : new StockPortfolioBacktestService.ExpectedReturnRefreshResult(0, 0, 0,
                        Math.max(50, minTrainingRows), 0);
        StockPortfolioBacktestService.ExpectedReturnRefreshResult v6Result =
                v6Enabled && v6HistoricalEnabled
                        ? stockPortfolioBacktestService.refreshExpectedReturnV6Snapshots(targetIndexCode,
                                Math.max(1, resultLimit),
                                Math.max(1, dateLimit),
                                Math.max(50, minTrainingRows))
                        : new StockPortfolioBacktestService.ExpectedReturnRefreshResult(0, 0, 0,
                                Math.max(50, minTrainingRows), 0);
        StockPortfolioBacktestService.ExpectedReturnRefreshResult v6LiveResult = v6Enabled && v6LiveEnabled
                ? stockPortfolioBacktestService.refreshLatestExpectedReturnV6Snapshots(targetIndexCode,
                        Math.max(1, resultLimit),
                        Math.max(50, minTrainingRows))
                : new StockPortfolioBacktestService.ExpectedReturnRefreshResult(0, 0, 0,
                        Math.max(50, minTrainingRows), 0);
        StockPortfolioBacktestService.ExpectedReturnRefreshResult v7Result =
                v7Enabled && v7HistoricalEnabled
                        ? stockPortfolioBacktestService.refreshExpectedReturnV7Snapshots(targetIndexCode,
                                Math.max(1, resultLimit),
                                Math.max(1, dateLimit),
                                Math.max(50, minTrainingRows))
                        : new StockPortfolioBacktestService.ExpectedReturnRefreshResult(0, 0, 0,
                                Math.max(50, minTrainingRows), 0);
        StockPortfolioBacktestService.ExpectedReturnRefreshResult v7LiveResult = v7Enabled && v7LiveEnabled
                ? stockPortfolioBacktestService.refreshLatestExpectedReturnV7Snapshots(targetIndexCode,
                        Math.max(1, resultLimit),
                        Math.max(50, minTrainingRows))
                : new StockPortfolioBacktestService.ExpectedReturnRefreshResult(0, 0, 0,
                        Math.max(50, minTrainingRows), 0);
        int macroFeatureRows = v8Enabled
                ? stockMacroFeatureService.refreshMacroFeatures(targetIndexCode, Math.max(1, dateLimit + 30))
                : 0;
        StockPortfolioBacktestService.ExpectedReturnRefreshResult v8Result =
                v8Enabled && v8HistoricalEnabled
                        ? stockExpectedReturnV8Service.refreshHistorical(targetIndexCode,
                                Math.max(1, resultLimit),
                                Math.max(1, dateLimit))
                        : new StockPortfolioBacktestService.ExpectedReturnRefreshResult(0, 0, 0,
                                Math.max(50, minTrainingRows), 0);
        StockPortfolioBacktestService.ExpectedReturnRefreshResult v8LiveResult = v8Enabled && v8LiveEnabled
                ? stockExpectedReturnV8Service.refreshLatest(targetIndexCode)
                : new StockPortfolioBacktestService.ExpectedReturnRefreshResult(0, 0, 0,
                        Math.max(50, minTrainingRows), 0);
        StockPortfolioBacktestService.ExpectedReturnRefreshResult v9Result =
                v9Enabled && v9HistoricalEnabled
                        ? stockExpectedReturnV9Service.refreshHistorical(targetIndexCode,
                                Math.max(1, resultLimit),
                                Math.max(1, dateLimit))
                        : new StockPortfolioBacktestService.ExpectedReturnRefreshResult(0, 0, 0,
                                Math.max(50, minTrainingRows), 0);
        StockPortfolioBacktestService.ExpectedReturnRefreshResult v9LiveResult = v9Enabled && v9LiveEnabled
                ? stockExpectedReturnV9Service.refreshLatest(targetIndexCode)
                : new StockPortfolioBacktestService.ExpectedReturnRefreshResult(0, 0, 0,
                        Math.max(50, minTrainingRows), 0);
        System.out.println("Stock expected return refresh indexCode=" + targetIndexCode
                + ", processedDates=" + result.processedDates()
                + ", savedRows=" + result.savedRows()
                + ", trainedRows=" + result.trainedRows()
                + ", minTrainingRows=" + result.minTrainingRows()
                + ", calibrationRows=" + result.calibrationRows()
                + ", liveSavedRows=" + liveResult.savedRows()
                + ", liveTrainedRows=" + liveResult.trainedRows()
                + ", v3ProcessedDates=" + v3Result.processedDates()
                + ", v3SavedRows=" + v3Result.savedRows()
                + ", v3CalibrationRows=" + v3Result.calibrationRows()
                + ", v3LiveSavedRows=" + v3LiveResult.savedRows()
                + ", v4ProcessedDates=" + v4Result.processedDates()
                + ", v4SavedRows=" + v4Result.savedRows()
                + ", v4CalibrationRows=" + v4Result.calibrationRows()
                + ", v4LiveSavedRows=" + v4LiveResult.savedRows()
                + ", v5ProcessedDates=" + v5Result.processedDates()
                + ", v5SavedRows=" + v5Result.savedRows()
                + ", v5CalibrationRows=" + v5Result.calibrationRows()
                + ", v5LiveSavedRows=" + v5LiveResult.savedRows()
                + ", v6ProcessedDates=" + v6Result.processedDates()
                + ", v6SavedRows=" + v6Result.savedRows()
                + ", v6CalibrationRows=" + v6Result.calibrationRows()
                + ", v6LiveSavedRows=" + v6LiveResult.savedRows()
                + ", v7ProcessedDates=" + v7Result.processedDates()
                + ", v7SavedRows=" + v7Result.savedRows()
                + ", v7CalibrationRows=" + v7Result.calibrationRows()
                + ", v7LiveSavedRows=" + v7LiveResult.savedRows()
                + ", macroFeatureRows=" + macroFeatureRows
                + ", v8ProcessedDates=" + v8Result.processedDates()
                + ", v8SavedRows=" + v8Result.savedRows()
                + ", v8CalibrationRows=" + v8Result.calibrationRows()
                + ", v8LiveSavedRows=" + v8LiveResult.savedRows()
                + ", v9ProcessedDates=" + v9Result.processedDates()
                + ", v9SavedRows=" + v9Result.savedRows()
                + ", v9CalibrationRows=" + v9Result.calibrationRows()
                + ", v9LiveSavedRows=" + v9LiveResult.savedRows());
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
