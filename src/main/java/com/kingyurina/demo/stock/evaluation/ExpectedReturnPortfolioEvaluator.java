package com.kingyurina.demo.stock.evaluation;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ExpectedReturnPortfolioEvaluator {

    private final int positionCount;

    public ExpectedReturnPortfolioEvaluator(int positionCount) {
        if (positionCount <= 0) {
            throw new IllegalArgumentException("positionCount must be positive");
        }
        this.positionCount = positionCount;
    }

    public ExpectedReturnPortfolioMetrics evaluate(List<ExpectedReturnEvaluationRow> rows, double costBps) {
        if (!Double.isFinite(costBps) || costBps < 0 || costBps >= 5000) {
            throw new IllegalArgumentException("costBps must be finite and between 0 and 5000 per side");
        }
        Map<LocalDate, List<ExpectedReturnEvaluationRow>> byDate = rows == null ? Map.of() : rows.stream()
                .filter(row -> row != null && row.signalDate() != null && row.labelEndDate() != null
                        && row.labelEndDate().isAfter(row.signalDate()))
                .collect(Collectors.groupingBy(ExpectedReturnEvaluationRow::signalDate,
                        LinkedHashMap::new, Collectors.toList()));
        List<LocalDate> dates = byDate.keySet().stream().sorted().toList();
        if (dates.isEmpty()) {
            return unavailable(0);
        }

        LocalDate capitalAvailableOn = null;
        LocalDate firstDecision = null;
        LocalDate lastExit = null;
        double grossEquity = 1.0d;
        double netEquity = 1.0d;
        double benchmarkEquity = 1.0d;
        double peak = 1.0d;
        double maxDrawdown = 0.0d;
        double turnoverSum = 0.0d;
        double costSum = 0.0d;
        int beatCount = 0;
        List<Double> netPeriodReturns = new ArrayList<>();
        List<Double> excessPeriodReturns = new ArrayList<>();

        for (LocalDate date : dates) {
            // Reserve the entire capital from decision through liquidation. No overlapping cohorts.
            if (capitalAvailableOn != null && date.isBefore(capitalAvailableOn)) continue;
            List<ExpectedReturnEvaluationRow> selected = byDate.get(date).stream()
                    .filter(row -> Double.isFinite(row.predictedExcessReturnPct())
                            && Double.isFinite(row.actualReturnPct())
                            && Double.isFinite(row.benchmarkReturnPct())
                            && row.actualReturnPct() >= -100 && row.benchmarkReturnPct() >= -100)
                    .sorted(Comparator.comparingDouble(
                            ExpectedReturnEvaluationRow::predictedExcessReturnPct).reversed())
                    .limit(positionCount)
                    .toList();
            if (selected.isEmpty()) {
                continue;
            }
            // Different exit calendars/benchmark windows cannot be pooled into one holding period.
            if (selected.stream().map(ExpectedReturnEvaluationRow::labelEndDate).distinct().count() != 1
                    || selected.stream().map(ExpectedReturnEvaluationRow::benchmarkReturnPct).distinct().count() != 1
                    || selected.stream().map(ExpectedReturnEvaluationRow::symbol).distinct().count() != selected.size()) continue;
            double turnover = 2.0d; // full cash -> portfolio -> cash, including terminal liquidation
            double costPct = turnover * costBps / 100.0d;
            double grossPct = selected.stream()
                    .mapToDouble(ExpectedReturnEvaluationRow::actualReturnPct).average().orElse(0.0d);
            double benchmarkPct = selected.stream()
                    .mapToDouble(ExpectedReturnEvaluationRow::benchmarkReturnPct).average().orElse(0.0d);
            double netPct = Math.max(-100.0d, grossPct - costPct);

            grossEquity *= 1.0d + grossPct / 100.0d;
            netEquity *= 1.0d + netPct / 100.0d;
            benchmarkEquity *= 1.0d + benchmarkPct / 100.0d;
            if (!Double.isFinite(grossEquity) || !Double.isFinite(netEquity)
                    || !Double.isFinite(benchmarkEquity)) return unavailable(netPeriodReturns.size() + 1);
            peak = Math.max(peak, netEquity);
            maxDrawdown = Math.min(maxDrawdown, (netEquity / peak - 1.0d) * 100.0d);
            turnoverSum += turnover * 100.0d;
            costSum += costPct;
            netPeriodReturns.add(netPct);
            excessPeriodReturns.add(netPct - benchmarkPct);
            if (netPct > benchmarkPct) {
                beatCount++;
            }
            if (firstDecision == null) firstDecision = date;
            lastExit = selected.getFirst().labelEndDate();
            capitalAvailableOn = lastExit;
            if (netEquity == 0) break;
        }

        int periods = netPeriodReturns.size();
        long elapsedDays = firstDecision == null ? 0 : ChronoUnit.DAYS.between(firstDecision, lastExit);
        double periodsPerYear = elapsedDays > 0 ? periods * 365.0d / elapsedDays : Double.NaN;
        double annualizedNet = annualized(netEquity, elapsedDays);
        double annualizedBenchmark = annualized(benchmarkEquity, elapsedDays);
        return new ExpectedReturnPortfolioMetrics(
                periods,
                (grossEquity - 1.0d) * 100.0d,
                (netEquity - 1.0d) * 100.0d,
                (benchmarkEquity - 1.0d) * 100.0d,
                annualizedNet - annualizedBenchmark,
                sharpe(netPeriodReturns, periodsPerYear),
                sortino(netPeriodReturns, periodsPerYear),
                maxDrawdown,
                periods == 0 ? 0.0d : beatCount * 100.0d / periods,
                turnoverSum,
                costSum,
                tailLoss(excessPeriodReturns));
    }

    private static ExpectedReturnPortfolioMetrics unavailable(int periods) {
        return new ExpectedReturnPortfolioMetrics(periods, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, 0, Double.NaN);
    }

    private static double annualized(double equity, long elapsedDays) {
        if (elapsedDays <= 0 || !Double.isFinite(equity) || equity < 0) return Double.NaN;
        double value = (Math.pow(equity, 365.0d / elapsedDays) - 1.0d) * 100.0d;
        return Double.isFinite(value) ? value : Double.NaN;
    }

    private static double sharpe(List<Double> returns, double periodsPerYear) {
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        double deviation = standardDeviation(returns, mean);
        return deviation == 0.0d ? Double.NaN : mean / deviation * Math.sqrt(periodsPerYear);
    }

    private static double sortino(List<Double> returns, double periodsPerYear) {
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        double downside = Math.sqrt(returns.stream()
                .mapToDouble(value -> Math.min(0, value) * Math.min(0, value)).average().orElse(0.0d));
        return downside == 0.0d ? Double.NaN : mean / downside * Math.sqrt(periodsPerYear);
    }

    private static double standardDeviation(List<Double> values, double mean) {
        if (values.size() < 2) {
            return 0.0d;
        }
        return Math.sqrt(values.stream().mapToDouble(value -> (value - mean) * (value - mean))
                .sum() / (values.size() - 1));
    }

    private static double tailLoss(List<Double> returns) {
        if (returns.isEmpty()) {
            return Double.NaN;
        }
        List<Double> sorted = returns.stream().sorted().toList();
        int count = Math.max(1, (int) Math.ceil(sorted.size() * 0.05d));
        return sorted.subList(0, count).stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }
}

