package com.kingyurina.demo.stock.evaluation;

import java.util.Locale;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.kingyurina.demo.stock.StockBacktestMapper;

@Service
public class ExpectedReturnEvaluationSummaryService {

    private static final String CONTRACT_VERSION = "SP500_EXCESS_20D_V1";

    private final ObjectProvider<StockBacktestMapper> mapperProvider;

    public ExpectedReturnEvaluationSummaryService(ObjectProvider<StockBacktestMapper> mapperProvider) {
        this.mapperProvider = mapperProvider;
    }

    public ExpectedReturnEvaluationSummaryView build(String indexCode) {
        StockBacktestMapper mapper = mapperProvider.getIfAvailable();
        ExpectedReturnEvaluationRun run = mapper == null ? null
                : mapper.findLatestExpectedReturnEvaluationRun(indexCode, CONTRACT_VERSION);
        if (run == null) {
            return new ExpectedReturnEvaluationSummaryView(
                    "Not evaluated", "neutral", "NOT_EVALUATED", CONTRACT_VERSION,
                    "EXPECTED_RETURN_V9", "-", "0 / 0", "-", "-", "-", "-", "Run the evaluation batch");
        }
        String decision = value(run.getDecision(), run.getStatus());
        return new ExpectedReturnEvaluationSummaryView(
                run.getStatus(),
                tone(decision),
                decision,
                run.getContractVersion(),
                run.getBaselineModelVersion(),
                run.getAsOfDate() == null ? "-" : run.getAsOfDate().toString(),
                integer(run.getValidRowCount()) + " / " + integer(run.getTargetRowCount()),
                percent(run.getCoveragePct(), 1, false),
                decimal(run.getRankIc(), 4),
                decimal(run.getBrierScore(), 4),
                percent(run.getAnnualizedExcessReturnPct(), 2, true),
                failedGateSummary(run.getChecksJson()));
    }

    private static String failedGateSummary(String checksJson) {
        if (checksJson == null || checksJson.isBlank()) {
            return "No stored gate checks";
        }
        int failures = 0;
        int index = 0;
        String marker = "\"passed\":false";
        while ((index = checksJson.indexOf(marker, index)) >= 0) {
            failures++;
            index += marker.length();
        }
        return failures == 0 ? "All stored gates passed" : failures + " gate checks failed";
    }

    private static String tone(String decision) {
        return switch (decision) {
            case "BASELINE_QUALIFIED", "PROMOTE" -> "positive";
            case "REJECT", "FAILED", "BASELINE_UNSTABLE" -> "negative";
            default -> "neutral";
        };
    }

    private static String integer(Integer value) {
        return value == null ? "0" : String.format(Locale.US, "%,d", value);
    }

    private static String decimal(Double value, int digits) {
        return value == null || !Double.isFinite(value) ? "-"
                : String.format(Locale.US, "%." + digits + "f", value);
    }

    private static String percent(Double value, int digits, boolean signed) {
        if (value == null || !Double.isFinite(value)) {
            return "-";
        }
        String pattern = signed ? "%+." + digits + "f%%" : "%." + digits + "f%%";
        return String.format(Locale.US, pattern, value);
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

