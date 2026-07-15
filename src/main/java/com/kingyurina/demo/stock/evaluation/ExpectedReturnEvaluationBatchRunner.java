package com.kingyurina.demo.stock.evaluation;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ExpectedReturnEvaluationBatchRunner implements ApplicationRunner {

    private final boolean enabled;
    private final boolean exitOnComplete;
    private final String indexCode;
    private final String asOfDate;
    private final ExpectedReturnEvaluationService service;
    private final ConfigurableApplicationContext applicationContext;

    public ExpectedReturnEvaluationBatchRunner(
            @Value("${app.batch.expected-return-evaluation.enabled:false}") boolean enabled,
            @Value("${app.batch.expected-return-evaluation.exit-on-complete:false}") boolean exitOnComplete,
            @Value("${app.batch.expected-return-evaluation.index-code:SP500}") String indexCode,
            @Value("${app.batch.expected-return-evaluation.as-of-date:}") String asOfDate,
            ExpectedReturnEvaluationService service,
            ConfigurableApplicationContext applicationContext) {
        this.enabled = enabled;
        this.exitOnComplete = exitOnComplete;
        this.indexCode = indexCode;
        this.asOfDate = asOfDate;
        this.service = service;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        ExpectedReturnEvaluationRun run =
                service.evaluateBaseline(indexCode, resolvedAsOfDate());
        int exitCode = "FAILED".equals(run.getStatus()) ? 1 : 0;
        System.out.println("Expected return evaluation runId=" + run.getId()
                + ", status=" + run.getStatus()
                + ", decision=" + run.getDecision()
                + ", validRows=" + run.getValidRowCount()
                + ", coveragePct=" + run.getCoveragePct());
        if (exitOnComplete) {
            Thread shutdown = new Thread(() ->
                    System.exit(SpringApplication.exit(applicationContext, () -> exitCode)));
            shutdown.setDaemon(false);
            shutdown.start();
        }
    }

    private LocalDate resolvedAsOfDate() {
        return asOfDate == null || asOfDate.isBlank()
                ? LocalDate.now()
                : LocalDate.parse(asOfDate.trim());
    }
}

