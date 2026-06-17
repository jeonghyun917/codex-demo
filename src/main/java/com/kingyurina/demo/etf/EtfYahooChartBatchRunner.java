package com.kingyurina.demo.etf;

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
public class EtfYahooChartBatchRunner implements ApplicationRunner {

    private final boolean enabled;
    private final boolean exitOnComplete;
    private final int symbolLimit;
    private final int years;
    private final long delayMillis;
    private final String symbols;
    private final EtfYahooChartService etfYahooChartService;
    private final ObjectProvider<EtfMapper> etfMapper;
    private final ConfigurableApplicationContext applicationContext;

    public EtfYahooChartBatchRunner(
            @Value("${app.batch.etf-yahoo.enabled:false}") boolean enabled,
            @Value("${app.batch.etf-yahoo.exit-on-complete:false}") boolean exitOnComplete,
            @Value("${app.batch.etf-yahoo.symbol-limit:100}") int symbolLimit,
            @Value("${app.batch.etf-yahoo.years:${yahoo-chart.default-years:3}}") int years,
            @Value("${app.batch.etf-yahoo.delay-millis:${yahoo-chart.request-delay-millis:250}}") long delayMillis,
            @Value("${app.batch.etf-yahoo.symbols:}") String symbols,
            EtfYahooChartService etfYahooChartService,
            ObjectProvider<EtfMapper> etfMapper,
            ConfigurableApplicationContext applicationContext) {
        this.enabled = enabled;
        this.exitOnComplete = exitOnComplete;
        this.symbolLimit = symbolLimit;
        this.years = years;
        this.delayMillis = delayMillis;
        this.symbols = symbols;
        this.etfYahooChartService = etfYahooChartService;
        this.etfMapper = etfMapper;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        int exitCode = 0;
        try {
            List<String> targets = explicitSymbols();
            if (targets.isEmpty()) {
                EtfMapper mapper = etfMapper.getIfAvailable();
                targets = mapper == null ? List.of() : mapper.findActiveSymbols(Math.max(1, symbolLimit));
            }
            EtfYahooChartService.SyncSummary summary = etfYahooChartService.syncSymbols(targets, years, delayMillis);
            System.out.println("ETF Yahoo chart sync requested=" + summary.requested()
                    + ", success=" + summary.success()
                    + ", fail=" + summary.fail()
                    + ", savedRows=" + summary.savedRows());
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
}
