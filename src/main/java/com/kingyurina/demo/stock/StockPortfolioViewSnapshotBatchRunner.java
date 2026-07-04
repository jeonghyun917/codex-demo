package com.kingyurina.demo.stock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class StockPortfolioViewSnapshotBatchRunner implements ApplicationRunner {

    private final boolean enabled;
    private final boolean exitOnComplete;
    private final String indexCode;
    private final StockPortfolioBacktestService stockPortfolioBacktestService;
    private final ConfigurableApplicationContext applicationContext;

    public StockPortfolioViewSnapshotBatchRunner(
            @Value("${app.batch.portfolio-view.enabled:false}") boolean enabled,
            @Value("${app.batch.portfolio-view.exit-on-complete:false}") boolean exitOnComplete,
            @Value("${app.batch.portfolio-view.index-code:SP500}") String indexCode,
            StockPortfolioBacktestService stockPortfolioBacktestService,
            ConfigurableApplicationContext applicationContext) {
        this.enabled = enabled;
        this.exitOnComplete = exitOnComplete;
        this.indexCode = indexCode;
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
            StockPortfolioBacktestService.PortfolioViewSnapshotRefreshResult result =
                    stockPortfolioBacktestService.refreshPortfolioViewSnapshot(indexCode);
            System.out.println("Stock portfolio view snapshot indexCode=" + result.indexCode()
                    + ", saved=" + result.saved()
                    + ", message=" + result.message()
                    + ", elapsedMillis=" + result.elapsedMillis());
            if (!result.saved()) {
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
