package com.kingyurina.demo.stock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class StockMacroRegimeBatchRunner implements ApplicationRunner {

    private final boolean enabled;
    private final boolean exitOnComplete;
    private final String indexCode;
    private final int dateLimit;
    private final int lookbackDays;
    private final StockMacroRegimeService stockMacroRegimeService;
    private final ConfigurableApplicationContext applicationContext;

    public StockMacroRegimeBatchRunner(
            @Value("${app.batch.macro-regime.enabled:false}") boolean enabled,
            @Value("${app.batch.macro-regime.exit-on-complete:false}") boolean exitOnComplete,
            @Value("${app.batch.macro-regime.index-code:SP500}") String indexCode,
            @Value("${app.batch.macro-regime.date-limit:1500}") int dateLimit,
            @Value("${app.batch.macro-regime.lookback-days:126}") int lookbackDays,
            StockMacroRegimeService stockMacroRegimeService,
            ConfigurableApplicationContext applicationContext) {
        this.enabled = enabled;
        this.exitOnComplete = exitOnComplete;
        this.indexCode = indexCode;
        this.dateLimit = dateLimit;
        this.lookbackDays = lookbackDays;
        this.stockMacroRegimeService = stockMacroRegimeService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        int exitCode = 0;
        try {
            int saved = stockMacroRegimeService.refreshMacroRegimeSnapshots(indexCode, Math.max(1, dateLimit),
                    Math.max(80, lookbackDays));
            System.out.println("Stock macro regime refresh indexCode=" + indexCode
                    + ", dateLimit=" + dateLimit
                    + ", lookbackDays=" + lookbackDays
                    + ", saved=" + saved);
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
