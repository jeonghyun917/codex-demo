package com.kingyurina.demo.stock.evaluation;

public record ExpectedReturnEvaluationEvidence(
        ExpectedReturnMetrics metrics,
        ExpectedReturnPortfolioMetrics portfolio,
        int validMonths,
        int totalRows,
        double coveragePct,
        double worstRegimeRankIc,
        int pitViolationCount) {

    public ExpectedReturnEvaluationEvidence withPitViolations(int count) {
        return new ExpectedReturnEvaluationEvidence(metrics, portfolio, validMonths, totalRows,
                coveragePct, worstRegimeRankIc, count);
    }

    public ExpectedReturnEvaluationEvidence withCounts(int months, int rows) {
        return new ExpectedReturnEvaluationEvidence(metrics, portfolio, months, rows,
                coveragePct, worstRegimeRankIc, pitViolationCount);
    }

    public ExpectedReturnEvaluationEvidence withRelativeQuality(double positiveIcDatePct,
            double brierScore, double quintileSpreadPct, double worstRegimeIc, double turnoverPct) {
        ExpectedReturnMetrics updatedMetrics = new ExpectedReturnMetrics(
                metrics.sampleCount(), metrics.rankIc(), positiveIcDatePct, metrics.maePct(),
                metrics.directionalAccuracyPct(), brierScore, metrics.calibrationErrorPct(),
                metrics.intervalCoveragePct(), quintileSpreadPct, metrics.coveragePct());
        ExpectedReturnPortfolioMetrics updatedPortfolio = new ExpectedReturnPortfolioMetrics(
                portfolio.periodCount(), portfolio.grossReturnPct(), portfolio.netReturnPct(),
                portfolio.benchmarkReturnPct(), portfolio.annualizedExcessReturnPct(),
                portfolio.sharpe(), portfolio.sortino(), portfolio.maximumDrawdownPct(),
                portfolio.benchmarkBeatRatePct(), turnoverPct, portfolio.transactionCostPct(),
                portfolio.tailLossPct());
        return new ExpectedReturnEvaluationEvidence(updatedMetrics, updatedPortfolio, validMonths,
                totalRows, coveragePct, worstRegimeIc, pitViolationCount);
    }
}

