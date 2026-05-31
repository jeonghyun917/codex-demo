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
    private final String indexCode;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public StockSignalScheduler(StockSignalRefreshService refreshService,
            @Value("${app.signal.refresh.enabled:true}") boolean enabled,
            @Value("${app.signal.refresh.run-on-startup:true}") boolean runOnStartup,
            @Value("${app.signal.refresh.index-code:SP500}") String indexCode) {
        this.refreshService = refreshService;
        this.enabled = enabled;
        this.runOnStartup = runOnStartup;
        this.indexCode = indexCode;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnStartup() {
        if (runOnStartup) {
            refresh("startup");
        }
    }

    @Scheduled(
            initialDelayString = "${app.signal.refresh.initial-delay-millis:3600000}",
            fixedDelayString = "${app.signal.refresh.fixed-delay-millis:3600000}")
    public void refreshScheduled() {
        refresh("scheduled");
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
