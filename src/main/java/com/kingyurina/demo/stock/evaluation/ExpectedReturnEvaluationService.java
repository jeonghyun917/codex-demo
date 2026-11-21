package com.kingyurina.demo.stock.evaluation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.kingyurina.demo.stock.StockBacktestMapper;

import tools.jackson.databind.ObjectMapper;

@Service
public class ExpectedReturnEvaluationService {

    private static final double TRANSACTION_COST_BPS = 10.0d;

    private final ObjectProvider<StockBacktestMapper> mapperProvider;
    private final ObjectMapper objectMapper;

    public ExpectedReturnEvaluationService(ObjectProvider<StockBacktestMapper> mapperProvider,
            ObjectMapper objectMapper) {
        this.mapperProvider = mapperProvider;
        this.objectMapper = objectMapper;
    }

    public ExpectedReturnEvaluationRun evaluateBaseline(String indexCode, LocalDate asOfDate) {
        ExpectedReturnPredictionContract contract = ExpectedReturnPredictionContract.sp500Excess20dCorrected();
        if (!contract.indexCode().equalsIgnoreCase(indexCode)) {
            throw new IllegalArgumentException("Contract supports SP500 only");
        }
        StockBacktestMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new IllegalStateException("StockBacktestMapper unavailable");
        }
        ExpectedReturnEvaluationRun run = runningRun(contract, asOfDate);
        mapper.insertExpectedReturnEvaluationRun(run);
        try {
            LocalDate fromDate = asOfDate.minusYears(8);
            ExpectedReturnEvaluationDataset dataset = new ExpectedReturnEvaluationDatasetService().assemble(
                    contract,
                    contract.baselineModel(),
                    mapper.findExpectedReturnSnapshots(contract.indexCode(), fromDate, asOfDate),
                    mapper.findResultsForEvaluation(contract.indexCode(), contract.horizonTradingDays(),
                            fromDate, asOfDate),
                    mapper.findBenchmarkReturns(contract.indexCode(), fromDate.minusDays(40), asOfDate),
                    mapper.findEvaluationMembershipSnapshots(contract.indexCode(), fromDate, asOfDate),
                    asOfDate,
                    mapper.findMacroRegimeSnapshots(contract.indexCode(), fromDate, asOfDate));
            persistExclusions(mapper, run.getId(), dataset);

            List<WalkForwardWindow> windows =
                    new WalkForwardWindowGenerator(contract).generate(dataset.rows());
            ExpectedReturnMetricsCalculator metricsCalculator = new ExpectedReturnMetricsCalculator();
            ExpectedReturnPortfolioEvaluator portfolioEvaluator = new ExpectedReturnPortfolioEvaluator(20);
            int validMonths = 0;
            Map<String, ExpectedReturnEvaluationRow> eligible = new LinkedHashMap<>();
            int eligibleTargetCount = 0;
            for (WalkForwardWindow window : windows) {
                if (window.status() != WalkForwardWindow.Status.ELIGIBLE) {
                    continue;
                }
                int windowTargetCount = dataset.targetCountsByDate().entrySet().stream()
                        .filter(entry -> !entry.getKey().isBefore(window.testFrom())
                                && !entry.getKey().isAfter(window.testTo()))
                        .mapToInt(Map.Entry::getValue).sum();
                eligibleTargetCount += windowTargetCount;
                window.testRows().forEach(row -> eligible.putIfAbsent(row.signalDate() + "|" + row.symbol(), row));
                ExpectedReturnMetrics metrics =
                        metricsCalculator.calculate(window.testRows(), windowTargetCount);
                ExpectedReturnPortfolioMetrics portfolio =
                        portfolioEvaluator.evaluate(window.testRows().size() == windowTargetCount
                                ? window.testRows() : List.of(), TRANSACTION_COST_BPS);
                mapper.insertExpectedReturnEvaluationWindow(new ExpectedReturnEvaluationWindowResult(
                        run.getId(),
                        window.id(),
                        window.testFrom(),
                        window.testTo(),
                        contract.baselineModel(),
                        window.status().name(),
                        window.trainRows().size(),
                        window.testRows().size(),
                        metrics,
                        portfolio));
                validMonths++;
            }

            List<ExpectedReturnEvaluationRow> eligibleRows = List.copyOf(eligible.values());
            double eligibleCoverage = eligibleTargetCount == 0 ? 0 : eligibleRows.size() * 100.0d / eligibleTargetCount;
            ExpectedReturnMetrics overallMetrics =
                    metricsCalculator.calculate(eligibleRows, eligibleTargetCount);
            ExpectedReturnPortfolioMetrics overallPortfolio =
                    portfolioEvaluator.evaluate(eligibleRows.size() == eligibleTargetCount
                            ? eligibleRows : List.of(), TRANSACTION_COST_BPS);
            ExpectedReturnEvaluationEvidence evidence = new ExpectedReturnEvaluationEvidence(
                    overallMetrics,
                    overallPortfolio,
                    validMonths,
                    eligibleRows.size(),
                    eligibleCoverage,
                    worstRegimeIc(eligibleRows, metricsCalculator),
                    dataset.pitViolationCount());
            ExpectedReturnPromotionResult promotion =
                    new ExpectedReturnPromotionGate(ExpectedReturnPromotionPolicy.sp500Excess20dV1())
                            .qualifyBaseline(evidence);
            finishRun(run, dataset, eligibleTargetCount, eligibleRows.size(), eligibleCoverage,
                    validMonths, overallMetrics, overallPortfolio, promotion);
            int updated = mapper.completeExpectedReturnEvaluationRun(run);
            if (updated != 1) {
                throw new IllegalStateException("Evaluation run was not completed from RUNNING state");
            }
            return run;
        } catch (RuntimeException ex) {
            failRun(run, ex);
            mapper.completeExpectedReturnEvaluationRun(run);
            return run;
        }
    }

    private void finishRun(ExpectedReturnEvaluationRun run,
            ExpectedReturnEvaluationDataset dataset,
            int targetRowCount, int validRowCount, double coveragePct,
            int validMonths,
            ExpectedReturnMetrics metrics,
            ExpectedReturnPortfolioMetrics portfolio,
            ExpectedReturnPromotionResult promotion) {
        run.setStatus(promotion.decision() == ExpectedReturnPromotionResult.Decision.INSUFFICIENT_DATA
                ? "INSUFFICIENT_DATA" : "COMPLETED");
        run.setDecision(promotion.decision().name());
        run.setValidMonths(validMonths);
        run.setTargetRowCount(targetRowCount);
        run.setValidRowCount(validRowCount);
        run.setCoveragePct(coveragePct);
        run.setRankIc(finiteOrNull(metrics.rankIc()));
        run.setBrierScore(finiteOrNull(metrics.brierScore()));
        run.setAnnualizedExcessReturnPct(finiteOrNull(portfolio.annualizedExcessReturnPct()));
        try {
            run.setChecksJson(objectMapper.writeValueAsString(Map.of(
                    "checks", promotion.checks(),
                    "metricScope", portfolio.accountingScope(),
                    "benchmarkDefinition", ExpectedReturnPredictionContract.sp500Excess20dCorrected().benchmarkDefinition(),
                    "population", "ELIGIBLE_OUT_OF_SAMPLE_WINDOWS_ONLY",
                    "datasetTargets", dataset.targetRowCount(),
                    "datasetValidRows", dataset.rows().size(),
                    "exclusions", dataset.exclusions().stream().collect(Collectors.groupingBy(
                            exclusion -> exclusion.code().name(), Collectors.counting())))));
        } catch (Exception ex) {
            throw new IllegalStateException("PROMOTION_CHECK_SERIALIZATION_FAILED", ex);
        }
        run.setFinishedAt(LocalDateTime.now());
    }

    private static void persistExclusions(StockBacktestMapper mapper, Long runId,
            ExpectedReturnEvaluationDataset dataset) {
        Map<String, Long> counts = dataset.exclusions().stream()
                .collect(Collectors.groupingBy(row -> row.code().name(), Collectors.counting()));
        counts.forEach((code, count) -> mapper.insertExpectedReturnEvaluationExclusionCount(
                new ExpectedReturnEvaluationExclusionCount(runId, code, Math.toIntExact(count))));
    }

    private static double worstRegimeIc(List<ExpectedReturnEvaluationRow> rows,
            ExpectedReturnMetricsCalculator calculator) {
        if (rows.isEmpty() || rows.stream().anyMatch(row -> row.marketRegime() == null
                || row.marketRegime().isBlank() || "UNKNOWN".equalsIgnoreCase(row.marketRegime()))) {
            return Double.NaN;
        }
        return rows.stream()
                .collect(Collectors.groupingBy(row ->
                        row.marketRegime() == null ? "UNKNOWN" : row.marketRegime()))
                .values().stream()
                .map(group -> calculator.calculate(group, group.size()).rankIc())
                .filter(Double::isFinite)
                .min(Double::compare)
                .orElse(Double.NaN);
    }

    private static ExpectedReturnEvaluationRun runningRun(
            ExpectedReturnPredictionContract contract, LocalDate asOfDate) {
        ExpectedReturnEvaluationRun run = new ExpectedReturnEvaluationRun();
        run.setContractVersion(contract.version());
        run.setIndexCode(contract.indexCode());
        run.setBaselineModelVersion(contract.baselineModel());
        run.setAsOfDate(asOfDate);
        run.setStatus("RUNNING");
        run.setStartedAt(LocalDateTime.now());
        return run;
    }

    private static void failRun(ExpectedReturnEvaluationRun run, RuntimeException ex) {
        run.setStatus("FAILED");
        run.setDecision("REJECT");
        run.setErrorCode(ex.getMessage() != null && ex.getMessage().matches("[A-Z0-9_]+")
                ? ex.getMessage() : "EVALUATION_FAILED");
        run.setErrorMessage(safeMessage(ex));
        run.setFinishedAt(LocalDateTime.now());
    }

    private static String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.substring(0, Math.min(500, message.length()));
    }

    private static Double finiteOrNull(double value) {
        return Double.isFinite(value) ? value : null;
    }
}

