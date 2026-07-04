package com.kingyurina.demo.stock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class StockDashboardViewSnapshotBatchRunner implements ApplicationRunner {

    private final boolean enabled;
    private final boolean exitOnComplete;
    private final String indexCode;
    private final String indexCodes;
    private final StockDashboardViewSnapshotService stockDashboardViewSnapshotService;
    private final ConfigurableApplicationContext applicationContext;

    public StockDashboardViewSnapshotBatchRunner(
            @Value("${app.batch.dashboard-view.enabled:false}") boolean enabled,
            @Value("${app.batch.dashboard-view.exit-on-complete:false}") boolean exitOnComplete,
            @Value("${app.batch.dashboard-view.index-code:SP500}") String indexCode,
            @Value("${app.batch.dashboard-view.index-codes:}") String indexCodes,
            StockDashboardViewSnapshotService stockDashboardViewSnapshotService,
            ConfigurableApplicationContext applicationContext) {
        this.enabled = enabled;
        this.exitOnComplete = exitOnComplete;
        this.indexCode = indexCode;
        this.indexCodes = indexCodes;
        this.stockDashboardViewSnapshotService = stockDashboardViewSnapshotService;
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
                StockDashboardViewSnapshotService.DashboardViewSnapshotRefreshResult result =
                        stockDashboardViewSnapshotService.refreshDashboardViewSnapshot(code);
                System.out.println("Stock dashboard view snapshot indexCode=" + result.indexCode()
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
