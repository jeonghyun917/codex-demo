package com.kingyurina.demo.stock.evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.kingyurina.demo.stock.evaluation.ExpectedReturnPromotionResult.Check;
import com.kingyurina.demo.stock.evaluation.ExpectedReturnPromotionResult.Decision;

public final class ExpectedReturnPromotionGate {

    private final ExpectedReturnPromotionPolicy policy;

    public ExpectedReturnPromotionGate(ExpectedReturnPromotionPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public ExpectedReturnPromotionResult qualifyBaseline(ExpectedReturnEvaluationEvidence evidence) {
        List<Check> checks = sufficiencyChecks(evidence);
        if (checks.stream().anyMatch(check -> !check.passed())) {
            return new ExpectedReturnPromotionResult(Decision.INSUFFICIENT_DATA, checks);
        }
        add(checks, "NO_PIT_VIOLATIONS", "= 0", evidence.pitViolationCount(),
                evidence.pitViolationCount() == 0);
        ExpectedReturnMetrics metrics = evidence.metrics();
        ExpectedReturnPortfolioMetrics portfolio = evidence.portfolio();
        add(checks, "RANK_IC_POSITIVE", "> 0", metrics.rankIc(),
                metrics.rankIc() > policy.minimumBaselineRankIc());
        add(checks, "POSITIVE_IC_DATES", ">= " + policy.minimumPositiveIcDatePct(),
                metrics.positiveIcDatePct(), metrics.positiveIcDatePct() >= policy.minimumPositiveIcDatePct());
        add(checks, "QUINTILE_SPREAD_POSITIVE", "> 0", metrics.quintileSpreadPct(),
                metrics.quintileSpreadPct() > 0.0d);
        add(checks, "BRIER_SCORE", "<= " + policy.maximumBrierScore(),
                metrics.brierScore(), metrics.brierScore() <= policy.maximumBrierScore());
        add(checks, "CALIBRATION_ERROR", "<= " + policy.maximumCalibrationErrorPct(),
                metrics.calibrationErrorPct(),
                metrics.calibrationErrorPct() <= policy.maximumCalibrationErrorPct());
        add(checks, "INTERVAL_COVERAGE_MIN", ">= " + policy.minimumIntervalCoveragePct(),
                metrics.intervalCoveragePct(),
                metrics.intervalCoveragePct() >= policy.minimumIntervalCoveragePct());
        add(checks, "INTERVAL_COVERAGE_MAX", "<= " + policy.maximumIntervalCoveragePct(),
                metrics.intervalCoveragePct(),
                metrics.intervalCoveragePct() <= policy.maximumIntervalCoveragePct());
        add(checks, "NET_EXCESS_POSITIVE", "> 0", portfolio.annualizedExcessReturnPct(),
                portfolio.annualizedExcessReturnPct() > 0.0d);
        add(checks, "WORST_REGIME_IC", ">= " + policy.minimumWorstRegimeRankIc(),
                evidence.worstRegimeRankIc(),
                evidence.worstRegimeRankIc() >= policy.minimumWorstRegimeRankIc());
        boolean passed = checks.stream().allMatch(Check::passed);
        return new ExpectedReturnPromotionResult(
                passed ? Decision.BASELINE_QUALIFIED : Decision.BASELINE_UNSTABLE, checks);
    }

    public ExpectedReturnPromotionResult evaluateCandidate(ExpectedReturnEvaluationEvidence baseline,
            ExpectedReturnEvaluationEvidence candidate) {
        List<Check> checks = sufficiencyChecks(candidate);
        if (checks.stream().anyMatch(check -> !check.passed())) {
            return new ExpectedReturnPromotionResult(Decision.INSUFFICIENT_DATA, checks);
        }
        add(checks, "NO_PIT_VIOLATIONS", "= 0", candidate.pitViolationCount(),
                candidate.pitViolationCount() == 0);
        ExpectedReturnMetrics baseMetrics = baseline.metrics();
        ExpectedReturnMetrics metrics = candidate.metrics();
        ExpectedReturnPortfolioMetrics basePortfolio = baseline.portfolio();
        ExpectedReturnPortfolioMetrics portfolio = candidate.portfolio();

        add(checks, "RANK_IC_POSITIVE", "> 0", metrics.rankIc(), metrics.rankIc() > 0.0d);
        add(checks, "QUINTILE_SPREAD_POSITIVE", "> 0", metrics.quintileSpreadPct(),
                metrics.quintileSpreadPct() > 0.0d);
        add(checks, "NET_EXCESS_POSITIVE", "> 0", portfolio.annualizedExcessReturnPct(),
                portfolio.annualizedExcessReturnPct() > 0.0d);
        add(checks, "BRIER_HARD_LIMIT", "<= " + policy.maximumBrierScore(), metrics.brierScore(),
                metrics.brierScore() <= policy.maximumBrierScore());
        if (checks.stream().anyMatch(check -> !check.passed())) {
            return new ExpectedReturnPromotionResult(Decision.REJECT, checks);
        }

        add(checks, "RANK_IC_IMPROVEMENT", ">= " + policy.minimumCandidateRankIcImprovement(),
                metrics.rankIc() - baseMetrics.rankIc(),
                metrics.rankIc() - baseMetrics.rankIc() >= policy.minimumCandidateRankIcImprovement());
        add(checks, "POSITIVE_IC_DATES_NOT_WORSE", ">= baseline", metrics.positiveIcDatePct(),
                metrics.positiveIcDatePct() >= baseMetrics.positiveIcDatePct());
        add(checks, "SPREAD_IMPROVEMENT", "> 0",
                metrics.quintileSpreadPct() - baseMetrics.quintileSpreadPct(),
                metrics.quintileSpreadPct() > baseMetrics.quintileSpreadPct());
        add(checks, "BRIER_NOT_WORSE", "<= baseline", metrics.brierScore(),
                metrics.brierScore() <= baseMetrics.brierScore());
        add(checks, "CALIBRATION_NOT_MATERIALLY_WORSE",
                "<= baseline + " + policy.maximumCalibrationDeteriorationPct(),
                metrics.calibrationErrorPct(),
                metrics.calibrationErrorPct() <= baseMetrics.calibrationErrorPct()
                        + policy.maximumCalibrationDeteriorationPct());
        double minimumCoverage = Math.max(policy.minimumCoveragePct(),
                baseline.coveragePct() * policy.minimumCandidateCoverageRatio());
        add(checks, "COVERAGE_PRESERVED", ">= " + minimumCoverage, candidate.coveragePct(),
                candidate.coveragePct() >= minimumCoverage);
        add(checks, "ANNUALIZED_EXCESS_IMPROVEMENT",
                ">= " + policy.minimumAnnualizedExcessImprovementPct(),
                portfolio.annualizedExcessReturnPct() - basePortfolio.annualizedExcessReturnPct(),
                portfolio.annualizedExcessReturnPct() - basePortfolio.annualizedExcessReturnPct()
                        >= policy.minimumAnnualizedExcessImprovementPct());
        add(checks, "SHARPE_IMPROVEMENT", ">= " + policy.minimumSharpeImprovement(),
                portfolio.sharpe() - basePortfolio.sharpe(),
                portfolio.sharpe() - basePortfolio.sharpe() >= policy.minimumSharpeImprovement());
        add(checks, "DRAWDOWN_NOT_MATERIALLY_WORSE",
                ">= baseline - " + policy.maximumDrawdownDeteriorationPct(),
                portfolio.maximumDrawdownPct(),
                portfolio.maximumDrawdownPct() >= basePortfolio.maximumDrawdownPct()
                        - policy.maximumDrawdownDeteriorationPct());
        add(checks, "TURNOVER_LIMIT", "<= baseline x " + policy.maximumTurnoverIncreaseRatio(),
                portfolio.turnoverPct(),
                portfolio.turnoverPct() <= basePortfolio.turnoverPct()
                        * policy.maximumTurnoverIncreaseRatio());
        add(checks, "WORST_REGIME_IC", ">= " + policy.minimumWorstRegimeRankIc(),
                candidate.worstRegimeRankIc(),
                candidate.worstRegimeRankIc() >= policy.minimumWorstRegimeRankIc());

        return new ExpectedReturnPromotionResult(
                checks.stream().allMatch(Check::passed) ? Decision.PROMOTE : Decision.HOLD,
                checks);
    }

    private List<Check> sufficiencyChecks(ExpectedReturnEvaluationEvidence evidence) {
        List<Check> checks = new ArrayList<>();
        add(checks, "MINIMUM_VALID_MONTHS", ">= " + policy.minimumValidMonths(),
                evidence.validMonths(), evidence.validMonths() >= policy.minimumValidMonths());
        add(checks, "MINIMUM_ROWS", ">= " + policy.minimumRows(),
                evidence.totalRows(), evidence.totalRows() >= policy.minimumRows());
        add(checks, "MINIMUM_COVERAGE", ">= " + policy.minimumCoveragePct(),
                evidence.coveragePct(), evidence.coveragePct() >= policy.minimumCoveragePct());
        return checks;
    }

    private static void add(List<Check> checks, String name, String expected,
            double actual, boolean passed) {
        checks.add(new Check(name, expected, actual, passed));
    }
}

