package com.kingyurina.demo.stock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class StockRiskSnapshotBatchRunner implements ApplicationRunner {

    private final boolean enabled;
    private final boolean exitOnComplete;
    private final String indexCode;
    private final int resultLimit;
    private final StockPortfolioBacktestService stockPortfolioBacktestService;
    private final ConfigurableApplicationContext applicationContext;

    public StockRiskSnapshotBatchRunner(
            @Value("${app.batch.risk-snapshot.enabled:false}") boolean enabled,
            @Value("${app.batch.risk-snapshot.exit-on-complete:false}") boolean exitOnComplete,
            @Value("${app.batch.risk-snapshot.index-code:SP500}") String indexCode,
            @Value("${app.batch.risk-snapshot.result-limit:100000}") int resultLimit,
            StockPortfolioBacktestService stockPortfolioBacktestService,
            ConfigurableApplicationContext applicationContext) {
        this.enabled = enabled;
        this.exitOnComplete = exitOnComplete;
        this.indexCode = indexCode;
        this.resultLimit = resultLimit;
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
            int saved = stockPortfolioBacktestService.refreshRiskSnapshots(indexCode, Math.max(1, resultLimit));
            int liveSaved = stockPortfolioBacktestService.refreshLatestRiskSnapshots(indexCode, Math.max(1, resultLimit));
            System.out.println("Stock risk snapshot refresh indexCode=" + indexCode + ", saved=" + saved);
            System.out.println("Stock live risk snapshot refresh indexCode=" + indexCode + ", saved=" + liveSaved);
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
}
