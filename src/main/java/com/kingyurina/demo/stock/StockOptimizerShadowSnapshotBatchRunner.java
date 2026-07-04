package com.kingyurina.demo.stock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class StockOptimizerShadowSnapshotBatchRunner implements ApplicationRunner {

    private final boolean enabled;
    private final boolean exitOnComplete;
    private final String indexCode;
    private final int resultLimit;
    private final int dateLimit;
    private final StockPortfolioBacktestService stockPortfolioBacktestService;
    private final ConfigurableApplicationContext applicationContext;

    public StockOptimizerShadowSnapshotBatchRunner(
            @Value("${app.batch.optimizer-shadow.enabled:false}") boolean enabled,
            @Value("${app.batch.optimizer-shadow.exit-on-complete:false}") boolean exitOnComplete,
            @Value("${app.batch.optimizer-shadow.index-code:SP500}") String indexCode,
            @Value("${app.batch.optimizer-shadow.result-limit:100000}") int resultLimit,
            @Value("${app.batch.optimizer-shadow.date-limit:0}") int dateLimit,
            StockPortfolioBacktestService stockPortfolioBacktestService,
            ConfigurableApplicationContext applicationContext) {
        this.enabled = enabled;
        this.exitOnComplete = exitOnComplete;
        this.indexCode = indexCode;
        this.resultLimit = resultLimit;
        this.dateLimit = dateLimit;
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
            StockPortfolioBacktestService.OptimizerShadowRefreshResult result =
                    stockPortfolioBacktestService.refreshOptimizerShadowSnapshots(indexCode,
                            Math.max(1, resultLimit), dateLimit);
            System.out.println("Stock optimizer shadow snapshot indexCode=" + result.indexCode()
                    + ", requestedRows=" + result.requestedRows()
                    + ", savedRows=" + result.savedRows()
                    + ", usableRows=" + result.usableRows()
                    + ", message=" + result.message());
            if (result.savedRows() == 0) {
                exitCode = 1;
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
}
