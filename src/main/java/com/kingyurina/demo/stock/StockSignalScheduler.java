package com.kingyurina.demo.stock;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StockSignalScheduler {

    private static final Logger log = LoggerFactory.getLogger(StockSignalScheduler.class);

    private final StockSignalRefreshService refreshService;
    private final boolean enabled;
    private final boolean runOnStartup;
    private final boolean riskSnapshotBatchEnabled;
    private final boolean marketSnapshotBatchEnabled;
    private final boolean covarianceSnapshotBatchEnabled;
    private final boolean macroRegimeBatchEnabled;
    private final boolean macroVintageBatchEnabled;
    private final boolean riskFreeRateBatchEnabled;
    private final boolean pitImportBatchEnabled;
    private final boolean expectedReturnBatchEnabled;
    private final boolean portfolioViewBatchEnabled;
    private final boolean optimizerShadowBatchEnabled;
    private final boolean dashboardViewBatchEnabled;
    private final boolean backtestViewBatchEnabled;
    private final boolean tossBatchEnabled;
    private final String indexCode;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public StockSignalScheduler(StockSignalRefreshService refreshService,
            @Value("${app.signal.refresh.enabled:true}") boolean enabled,
            @Value("${app.signal.refresh.run-on-startup:true}") boolean runOnStartup,
            @Value("${app.batch.risk-snapshot.enabled:false}") boolean riskSnapshotBatchEnabled,
            @Value("${app.batch.market-snapshot.enabled:false}") boolean marketSnapshotBatchEnabled,
            @Value("${app.batch.covariance-snapshot.enabled:false}") boolean covarianceSnapshotBatchEnabled,
            @Value("${app.batch.macro-regime.enabled:false}") boolean macroRegimeBatchEnabled,
            @Value("${app.batch.macro-vintage.enabled:false}") boolean macroVintageBatchEnabled,
            @Value("${app.batch.risk-free-rate.enabled:false}") boolean riskFreeRateBatchEnabled,
            @Value("${app.batch.pit-import.enabled:false}") boolean pitImportBatchEnabled,
            @Value("${app.batch.expected-return.enabled:false}") boolean expectedReturnBatchEnabled,
            @Value("${app.batch.portfolio-view.enabled:false}") boolean portfolioViewBatchEnabled,
            @Value("${app.batch.optimizer-shadow.enabled:false}") boolean optimizerShadowBatchEnabled,
            @Value("${app.batch.dashboard-view.enabled:false}") boolean dashboardViewBatchEnabled,
            @Value("${app.batch.backtest-view.enabled:false}") boolean backtestViewBatchEnabled,
            @Value("${app.batch.toss.enabled:false}") boolean tossBatchEnabled,
            @Value("${app.signal.refresh.index-code:SP500}") String indexCode) {
        this.refreshService = refreshService;
        this.enabled = enabled;
        this.runOnStartup = runOnStartup;
        this.riskSnapshotBatchEnabled = riskSnapshotBatchEnabled;
        this.marketSnapshotBatchEnabled = marketSnapshotBatchEnabled;
        this.covarianceSnapshotBatchEnabled = covarianceSnapshotBatchEnabled;
        this.macroRegimeBatchEnabled = macroRegimeBatchEnabled;
        this.macroVintageBatchEnabled = macroVintageBatchEnabled;
        this.riskFreeRateBatchEnabled = riskFreeRateBatchEnabled;
        this.pitImportBatchEnabled = pitImportBatchEnabled;
        this.expectedReturnBatchEnabled = expectedReturnBatchEnabled;
        this.portfolioViewBatchEnabled = portfolioViewBatchEnabled;
        this.optimizerShadowBatchEnabled = optimizerShadowBatchEnabled;
        this.dashboardViewBatchEnabled = dashboardViewBatchEnabled;
        this.backtestViewBatchEnabled = backtestViewBatchEnabled;
        this.tossBatchEnabled = tossBatchEnabled;
        this.indexCode = indexCode;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnStartup() {
        if (runOnStartup && !batchMode()) {
            refresh("startup");
        }
    }

    @Scheduled(
            initialDelayString = "${app.signal.refresh.initial-delay-millis:3600000}",
            fixedDelayString = "${app.signal.refresh.fixed-delay-millis:3600000}")
    public void refreshScheduled() {
        if (batchMode()) {
            return;
        }
        refresh("scheduled");
    }

    private boolean batchMode() {
        return riskSnapshotBatchEnabled || marketSnapshotBatchEnabled || covarianceSnapshotBatchEnabled
                || macroRegimeBatchEnabled || macroVintageBatchEnabled || riskFreeRateBatchEnabled
                || pitImportBatchEnabled || expectedReturnBatchEnabled || portfolioViewBatchEnabled
                || optimizerShadowBatchEnabled || dashboardViewBatchEnabled || backtestViewBatchEnabled
                || tossBatchEnabled;
    }

    private void refresh(String trigger) {
        if (!enabled) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.info("Stock signal refresh skipped because a refresh is already running.");
            return;
        }
        try {
            StockSignalRefreshService.RefreshResult result = refreshService.recalculateIndexLatest(indexCode);
            log.info("Stock signal {} refresh completed. requested={}, success={}, fail={}",
                    trigger, result.requested(), result.success(), result.fail());
        } finally {
            running.set(false);
        }
    }
}
