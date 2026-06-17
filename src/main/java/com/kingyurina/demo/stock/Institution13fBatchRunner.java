package com.kingyurina.demo.stock;

import java.time.LocalDateTime;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Institution13fBatchRunner implements ApplicationRunner {

    private static final String JOB_NAME = "institution-13f-refresh";

    private final boolean enabled;
    private final boolean exitOnComplete;
    private final int managerLimit;
    private final int filingsPerManager;
    private final Institution13fService service;
    private final ObjectProvider<Institution13fBatchRunMapper> batchRunMapper;
    private final ConfigurableApplicationContext applicationContext;

    public Institution13fBatchRunner(
            @Value("${app.batch.institution-13f.enabled:false}") boolean enabled,
            @Value("${app.batch.institution-13f.exit-on-complete:false}") boolean exitOnComplete,
            @Value("${app.batch.institution-13f.manager-limit:20}") int managerLimit,
            @Value("${app.batch.institution-13f.filings-per-manager:2}") int filingsPerManager,
            Institution13fService service,
            ObjectProvider<Institution13fBatchRunMapper> batchRunMapper,
            ConfigurableApplicationContext applicationContext) {
        this.enabled = enabled;
        this.exitOnComplete = exitOnComplete;
        this.managerLimit = managerLimit;
        this.filingsPerManager = filingsPerManager;
        this.service = service;
        this.batchRunMapper = batchRunMapper;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        int exitCode = 0;
        try {
            runBatch();
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

    private void runBatch() {
        Institution13fBatchRunMapper mapper = batchRunMapper.getIfAvailable();
        if (mapper == null) {
            throw new IllegalStateException("Institution13fBatchRunMapper is not available. Run with the mariadb profile.");
        }

        Institution13fBatchRun run = new Institution13fBatchRun(JOB_NAME, "RUNNING");
        mapper.insert(run);
        try {
            Institution13fService.SyncSummary summary = service.syncLatest(managerLimit, filingsPerManager);
            run.setFinishedAt(LocalDateTime.now());
            run.setRequestedCount(summary.requestedCount());
            run.setSuccessCount(summary.successCount());
            run.setFailCount(summary.failCount());
            run.setHoldingCount(summary.holdingCount());
            run.setAggregateRows(summary.aggregateRows());
            run.setStatus(summary.failCount() == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS");
            run.setMessage(summary.message());
            mapper.finish(run);
            System.out.println("Institution 13F sync requested=" + summary.requestedCount()
                    + ", success=" + summary.successCount()
                    + ", fail=" + summary.failCount()
                    + ", holdings=" + summary.holdingCount()
                    + ", aggregateRows=" + summary.aggregateRows());
        } catch (RuntimeException ex) {
            run.setFinishedAt(LocalDateTime.now());
            run.setStatus("FAILED");
            run.setMessage(ex.getMessage());
            mapper.finish(run);
            throw ex;
        }
    }
}
