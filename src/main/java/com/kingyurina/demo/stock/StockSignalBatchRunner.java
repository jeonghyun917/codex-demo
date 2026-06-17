package com.kingyurina.demo.stock;

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
public class StockSignalBatchRunner implements ApplicationRunner {

    private final boolean enabled;
    private final boolean exitOnComplete;
    private final String indexCodes;
    private final int symbolLimit;
    private final StockSignalRefreshService refreshService;
    private final ObjectProvider<IndexConstituentMapper> indexConstituentMapper;
    private final ConfigurableApplicationContext applicationContext;

    public StockSignalBatchRunner(
            @Value("${app.batch.signal-refresh.enabled:false}") boolean enabled,
            @Value("${app.batch.signal-refresh.exit-on-complete:false}") boolean exitOnComplete,
            @Value("${app.batch.signal-refresh.index-codes:SP500}") String indexCodes,
            @Value("${app.batch.signal-refresh.symbol-limit:1000}") int symbolLimit,
            StockSignalRefreshService refreshService,
            ObjectProvider<IndexConstituentMapper> indexConstituentMapper,
            ConfigurableApplicationContext applicationContext) {
        this.enabled = enabled;
        this.exitOnComplete = exitOnComplete;
        this.indexCodes = indexCodes;
        this.symbolLimit = symbolLimit;
        this.refreshService = refreshService;
        this.indexConstituentMapper = indexConstituentMapper;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        int exitCode = 0;
        try {
            IndexConstituentMapper mapper = indexConstituentMapper.getIfAvailable();
            if (mapper == null) {
                throw new IllegalStateException("IndexConstituentMapper is not available. Run with the mariadb profile.");
            }
            List<String> symbols = mapper.findCurrentSymbolsByIndexCodes(targetIndexCodes(), Math.max(1, symbolLimit));
            StockSignalRefreshService.RefreshResult result = refreshService.recalculateSymbols(symbols);
            System.out.println("Stock signal refresh requested=" + result.requested()
                    + ", success=" + result.success()
                    + ", fail=" + result.fail());
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

    private List<String> targetIndexCodes() {
        Set<String> normalized = new LinkedHashSet<>();
        if (indexCodes != null) {
            for (String token : indexCodes.split(",")) {
                String value = token == null ? "" : token.trim().toUpperCase(Locale.ROOT).replace("-", "").replace("_", "");
                if ("SP500".equals(value) || "S&P500".equals(value)) {
                    normalized.add("SP500");
                } else if ("NASDAQ100".equals(value) || "NDX".equals(value)) {
                    normalized.add("NASDAQ100");
                } else if ("DOW30".equals(value) || "DJI".equals(value)) {
                    normalized.add("DOW30");
                } else if (!value.isBlank()) {
                    normalized.add(value);
                }
            }
        }
        return normalized.isEmpty() ? List.of("SP500") : List.copyOf(normalized);
    }
}
