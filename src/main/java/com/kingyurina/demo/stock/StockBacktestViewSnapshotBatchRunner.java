package com.kingyurina.demo.stock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class StockBacktestViewSnapshotBatchRunner implements ApplicationRunner {

    private final boolean enabled;
    private final boolean exitOnComplete;
    private final String indexCode;
    private final String indexCodes;
    private final StockBacktestService stockBacktestService;
    private final ConfigurableApplicationContext applicationContext;

    public StockBacktestViewSnapshotBatchRunner(
            @Value("${app.batch.backtest-view.enabled:false}") boolean enabled,
            @Value("${app.batch.backtest-view.exit-on-complete:false}") boolean exitOnComplete,
            @Value("${app.batch.backtest-view.index-code:SP500}") String indexCode,
            @Value("${app.batch.backtest-view.index-codes:}") String indexCodes,
            StockBacktestService stockBacktestService,
            ConfigurableApplicationContext applicationContext) {
        this.enabled = enabled;
        this.exitOnComplete = exitOnComplete;
        this.indexCode = indexCode;
        this.indexCodes = indexCodes;
        this.stockBacktestService = stockBacktestService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        int exitCode = 0;
        try {
            for (String code : resolvedIndexCodes()) {
                StockBacktestService.BacktestViewSnapshotRefreshResult result =
                        stockBacktestService.refreshBacktestViewSnapshot(code);
                System.out.println("Stock backtest view snapshot indexCode=" + result.indexCode()
                        + ", saved=" + result.saved()
                        + ", message=" + result.message()
                        + ", elapsedMillis=" + result.elapsedMillis());
                if (!result.saved()) {
                    exitCode = 1;
                }
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

    private java.util.List<String> resolvedIndexCodes() {
        String source = indexCodes == null || indexCodes.isBlank() ? indexCode : indexCodes;
        return java.util.Arrays.stream(source.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }
}
