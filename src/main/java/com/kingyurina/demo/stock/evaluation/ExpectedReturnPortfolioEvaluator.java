package com.kingyurina.demo.stock.evaluation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        Map<LocalDate, List<ExpectedReturnEvaluationRow>> byDate = rows == null ? Map.of() : rows.stream()
                .filter(row -> row != null && row.signalDate() != null)
                .collect(Collectors.groupingBy(ExpectedReturnEvaluationRow::signalDate,
                        LinkedHashMap::new, Collectors.toList()));
        List<LocalDate> dates = byDate.keySet().stream().sorted().toList();
        if (dates.isEmpty()) {
            return new ExpectedReturnPortfolioMetrics(0, 0.0d, 0.0d, 0.0d, 0.0d,
                    Double.NaN, Double.NaN, 0.0d, 0.0d, 0.0d, 0.0d, Double.NaN);
        }

        Set<String> previous = Set.of();
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
            List<ExpectedReturnEvaluationRow> selected = byDate.get(date).stream()
                    .filter(row -> Double.isFinite(row.predictedExcessReturnPct())
                            && Double.isFinite(row.actualReturnPct())
                            && Double.isFinite(row.benchmarkReturnPct()))
                    .sorted(Comparator.comparingDouble(
                            ExpectedReturnEvaluationRow::predictedExcessReturnPct).reversed())
                    .limit(positionCount)
                    .toList();
            if (selected.isEmpty()) {
                continue;
            }
            Set<String> current = selected.stream().map(ExpectedReturnEvaluationRow::symbol)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            double turnover = previous.isEmpty() ? 1.0d : turnover(previous, current);
            double costPct = turnover * Math.max(0.0d, costBps) / 100.0d;
            double grossPct = selected.stream()
                    .mapToDouble(ExpectedReturnEvaluationRow::actualReturnPct).average().orElse(0.0d);
            double benchmarkPct = selected.stream()
                    .mapToDouble(ExpectedReturnEvaluationRow::benchmarkReturnPct).average().orElse(0.0d);
            double netPct = grossPct - costPct;

            grossEquity *= 1.0d + grossPct / 100.0d;
            netEquity *= 1.0d + netPct / 100.0d;
            benchmarkEquity *= 1.0d + benchmarkPct / 100.0d;
            peak = Math.max(peak, netEquity);
            maxDrawdown = Math.min(maxDrawdown, (netEquity / peak - 1.0d) * 100.0d);
            turnoverSum += turnover * 100.0d;
            costSum += costPct;
            netPeriodReturns.add(netPct);
            excessPeriodReturns.add(netPct - benchmarkPct);
            if (netPct > benchmarkPct) {
                beatCount++;
            }
            previous = current;
        }

        int periods = netPeriodReturns.size();
        double annualizedNet = annualized(netEquity, periods);
        double annualizedBenchmark = annualized(benchmarkEquity, periods);
        return new ExpectedReturnPortfolioMetrics(
                periods,
                (grossEquity - 1.0d) * 100.0d,
                (netEquity - 1.0d) * 100.0d,
                (benchmarkEquity - 1.0d) * 100.0d,
                annualizedNet - annualizedBenchmark,
                sharpe(netPeriodReturns),
                sortino(netPeriodReturns),
                maxDrawdown,
                periods == 0 ? 0.0d : beatCount * 100.0d / periods,
                turnoverSum,
                costSum,
                tailLoss(excessPeriodReturns));
    }

    private static double turnover(Set<String> previous, Set<String> current) {
        Set<String> union = new LinkedHashSet<>(previous);
        union.addAll(current);
        double previousWeight = previous.isEmpty() ? 0.0d : 1.0d / previous.size();
        double currentWeight = current.isEmpty() ? 0.0d : 1.0d / current.size();
        double absoluteChange = 0.0d;
        for (String symbol : union) {
            absoluteChange += Math.abs((current.contains(symbol) ? currentWeight : 0.0d)
                    - (previous.contains(symbol) ? previousWeight : 0.0d));
        }
        return absoluteChange / 2.0d;
    }

    private static double annualized(double equity, int periods) {
        return periods == 0 ? 0.0d : (Math.pow(equity, 12.0d / periods) - 1.0d) * 100.0d;
    }

    private static double sharpe(List<Double> returns) {
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        double deviation = standardDeviation(returns, mean);
        return deviation == 0.0d ? Double.NaN : mean / deviation * Math.sqrt(12.0d);
    }

    private static double sortino(List<Double> returns) {
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        double downside = Math.sqrt(returns.stream().filter(value -> value < 0.0d)
                .mapToDouble(value -> value * value).average().orElse(0.0d));
        return downside == 0.0d ? Double.NaN : mean / downside * Math.sqrt(12.0d);
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

