package com.kingyurina.demo.stock.evaluation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
        ExpectedReturnPredictionContract contract = ExpectedReturnPredictionContract.sp500Excess20dV1();
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
                    mapper.findIndexMembershipSnapshots(contract.indexCode(), fromDate, asOfDate),
                    asOfDate);
            persistExclusions(mapper, run.getId(), dataset);

            List<WalkForwardWindow> windows =
                    new WalkForwardWindowGenerator(contract).generate(dataset.rows());
            ExpectedReturnMetricsCalculator metricsCalculator = new ExpectedReturnMetricsCalculator();
            ExpectedReturnPortfolioEvaluator portfolioEvaluator = new ExpectedReturnPortfolioEvaluator(20);
            int validMonths = 0;
            for (WalkForwardWindow window : windows) {
                if (window.status() != WalkForwardWindow.Status.ELIGIBLE) {
                    continue;
                }
                ExpectedReturnMetrics metrics =
                        metricsCalculator.calculate(window.testRows(), window.testRows().size());
                ExpectedReturnPortfolioMetrics portfolio =
                        portfolioEvaluator.evaluate(window.testRows(), TRANSACTION_COST_BPS);
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

            ExpectedReturnMetrics overallMetrics =
                    metricsCalculator.calculate(dataset.rows(), dataset.targetRowCount());
            ExpectedReturnPortfolioMetrics overallPortfolio =
                    portfolioEvaluator.evaluate(dataset.rows(), TRANSACTION_COST_BPS);
            ExpectedReturnEvaluationEvidence evidence = new ExpectedReturnEvaluationEvidence(
                    overallMetrics,
                    overallPortfolio,
                    validMonths,
                    dataset.rows().size(),
                    dataset.coveragePct(),
                    worstRegimeIc(dataset.rows(), metricsCalculator),
                    dataset.pitViolationCount());
            ExpectedReturnPromotionResult promotion =
                    new ExpectedReturnPromotionGate(ExpectedReturnPromotionPolicy.sp500Excess20dV1())
                            .qualifyBaseline(evidence);
            finishRun(run, dataset, validMonths, overallMetrics, overallPortfolio, promotion);
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
            int validMonths,
            ExpectedReturnMetrics metrics,
            ExpectedReturnPortfolioMetrics portfolio,
            ExpectedReturnPromotionResult promotion) {
        run.setStatus(promotion.decision() == ExpectedReturnPromotionResult.Decision.INSUFFICIENT_DATA
                ? "INSUFFICIENT_DATA" : "COMPLETED");
        run.setDecision(promotion.decision().name());
        run.setValidMonths(validMonths);
        run.setTargetRowCount(dataset.targetRowCount());
        run.setValidRowCount(dataset.rows().size());
        run.setCoveragePct(dataset.coveragePct());
        run.setRankIc(finiteOrNull(metrics.rankIc()));
        run.setBrierScore(finiteOrNull(metrics.brierScore()));
        run.setAnnualizedExcessReturnPct(finiteOrNull(portfolio.annualizedExcessReturnPct()));
        try {
            run.setChecksJson(objectMapper.writeValueAsString(promotion.checks()));
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

