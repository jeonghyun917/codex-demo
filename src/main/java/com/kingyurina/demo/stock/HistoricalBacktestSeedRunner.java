package com.kingyurina.demo.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class HistoricalBacktestSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HistoricalBacktestSeedRunner.class);

    private final HistoricalBacktestSeedService seedService;
    private final ConfigurableApplicationContext applicationContext;
    private final boolean enabled;
    private final boolean exitOnComplete;
    private final String indexCode;
    private final int years;
    private final int symbolLimit;
    private final int dateLimit;

    public HistoricalBacktestSeedRunner(HistoricalBacktestSeedService seedService,
            ConfigurableApplicationContext applicationContext,
            @Value("${app.batch.historical-backtest.enabled:false}") boolean enabled,
            @Value("${app.batch.historical-backtest.exit-on-complete:false}") boolean exitOnComplete,
            @Value("${app.batch.historical-backtest.index-code:SP500}") String indexCode,
            @Value("${app.batch.historical-backtest.years:3}") int years,
            @Value("${app.batch.historical-backtest.symbol-limit:1000}") int symbolLimit,
            @Value("${app.batch.historical-backtest.date-limit:40}") int dateLimit) {
        this.seedService = seedService;
        this.applicationContext = applicationContext;
        this.enabled = enabled;
        this.exitOnComplete = exitOnComplete;
        this.indexCode = indexCode;
        this.years = years;
        this.symbolLimit = symbolLimit;
        this.dateLimit = dateLimit;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        HistoricalBacktestSeedResult result = seedService.seedMonthly(indexCode, years, symbolLimit, dateLimit);
        log.info("Historical backtest seed completed. seedDates={}, requestedSymbols={}, snapshots={}, skipped={}, results={}",
                result.seedDateCount(), result.requestedSymbols(), result.snapshotCount(), result.skippedCount(),
                result.backtestResultCount());
        if (exitOnComplete) {
            SpringApplication.exit(applicationContext, () -> 0);
        }
    }
}
