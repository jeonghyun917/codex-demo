package com.kingyurina.demo.stock.evaluation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

public final class ExpectedReturnMetricsCalculator {

    public ExpectedReturnMetrics calculate(List<ExpectedReturnEvaluationRow> sourceRows, int targetRowCount) {
        List<ExpectedReturnEvaluationRow> rows = sourceRows == null ? List.of() : sourceRows.stream()
                .filter(ExpectedReturnMetricsCalculator::valid)
                .toList();
        if (rows.isEmpty()) {
            return new ExpectedReturnMetrics(0, Double.NaN, 0.0d, Double.NaN, 0.0d,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.0d);
        }
        Map<LocalDate, List<ExpectedReturnEvaluationRow>> byDate = rows.stream()
                .collect(Collectors.groupingBy(ExpectedReturnEvaluationRow::signalDate,
                        LinkedHashMap::new, Collectors.toList()));
        List<Double> dateIcs = byDate.values().stream()
                .map(this::spearman)
                .filter(Double::isFinite)
                .toList();
        List<Double> spreads = byDate.values().stream()
                .map(this::quintileSpread)
                .filter(Double::isFinite)
                .toList();

        double mae = rows.stream().mapToDouble(row ->
                Math.abs(row.predictedExcessReturnPct() - row.actualExcessReturnPct())).average().orElse(Double.NaN);
        double direction = rows.stream().filter(row ->
                sameDirection(row.predictedExcessReturnPct(), row.actualExcessReturnPct())).count()
                * 100.0d / rows.size();
        double brier = rows.stream().mapToDouble(row -> {
            double probability = clamp(row.predictedUpsideProbabilityPct(), 0.0d, 100.0d) / 100.0d;
            double actual = row.actualExcessReturnPct() > 0.0d ? 1.0d : 0.0d;
            return square(probability - actual);
        }).average().orElse(Double.NaN);
        double intervalCoverage = rows.stream().filter(row ->
                row.actualExcessReturnPct() >= row.excessP10Pct()
                        && row.actualExcessReturnPct() <= row.excessP90Pct()).count()
                * 100.0d / rows.size();
        double positiveIcPct = dateIcs.isEmpty() ? 0.0d
                : dateIcs.stream().filter(value -> value > 0.0d).count() * 100.0d / dateIcs.size();
        double coverage = targetRowCount <= 0 ? 100.0d : rows.size() * 100.0d / targetRowCount;
        return new ExpectedReturnMetrics(
                rows.size(),
                average(dateIcs),
                positiveIcPct,
                mae,
                direction,
                brier,
                calibrationError(rows),
                intervalCoverage,
                average(spreads),
                coverage);
    }

    private double spearman(List<ExpectedReturnEvaluationRow> rows) {
        if (rows.size() < 2) {
            return Double.NaN;
        }
        double[] predicted = ranks(rows, ExpectedReturnEvaluationRow::predictedExcessReturnPct);
        double[] actual = ranks(rows, ExpectedReturnEvaluationRow::actualExcessReturnPct);
        return pearson(predicted, actual);
    }

    private double quintileSpread(List<ExpectedReturnEvaluationRow> rows) {
        if (rows.size() < 5) {
            return Double.NaN;
        }
        List<ExpectedReturnEvaluationRow> ordered = rows.stream()
                .sorted(Comparator.comparingDouble(ExpectedReturnEvaluationRow::predictedExcessReturnPct).reversed())
                .toList();
        int count = Math.max(1, ordered.size() / 5);
        double top = ordered.subList(0, count).stream()
                .mapToDouble(ExpectedReturnEvaluationRow::actualExcessReturnPct).average().orElse(Double.NaN);
        double bottom = ordered.subList(ordered.size() - count, ordered.size()).stream()
                .mapToDouble(ExpectedReturnEvaluationRow::actualExcessReturnPct).average().orElse(Double.NaN);
        return top - bottom;
    }

    private static double calibrationError(List<ExpectedReturnEvaluationRow> rows) {
        Map<Integer, List<ExpectedReturnEvaluationRow>> buckets = rows.stream()
                .collect(Collectors.groupingBy(row ->
                        (int) Math.floor(clamp(row.predictedUpsideProbabilityPct(), 0.0d, 99.999d) / 10.0d)));
        double weightedError = 0.0d;
        for (List<ExpectedReturnEvaluationRow> bucket : buckets.values()) {
            double predicted = bucket.stream()
                    .mapToDouble(ExpectedReturnEvaluationRow::predictedUpsideProbabilityPct).average().orElse(50.0d);
            double actual = bucket.stream().filter(row -> row.actualExcessReturnPct() > 0.0d).count()
                    * 100.0d / bucket.size();
            weightedError += Math.abs(actual - predicted) * bucket.size();
        }
        return weightedError / rows.size();
    }

    private static double[] ranks(List<ExpectedReturnEvaluationRow> rows,
            ToDoubleFunction<ExpectedReturnEvaluationRow> reader) {
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            order.add(index);
        }
        order.sort(Comparator.comparingDouble(index -> reader.applyAsDouble(rows.get(index))));
        double[] ranks = new double[rows.size()];
        int start = 0;
        while (start < order.size()) {
            int end = start + 1;
            double value = reader.applyAsDouble(rows.get(order.get(start)));
            while (end < order.size()
                    && Double.compare(value, reader.applyAsDouble(rows.get(order.get(end)))) == 0) {
                end++;
            }
            double averageRank = (start + 1 + end) / 2.0d;
            for (int index = start; index < end; index++) {
                ranks[order.get(index)] = averageRank;
            }
            start = end;
        }
        return ranks;
    }

    private static double pearson(double[] left, double[] right) {
        double leftMean = java.util.Arrays.stream(left).average().orElse(0.0d);
        double rightMean = java.util.Arrays.stream(right).average().orElse(0.0d);
        double covariance = 0.0d;
        double leftVariance = 0.0d;
        double rightVariance = 0.0d;
        for (int index = 0; index < left.length; index++) {
            double leftDelta = left[index] - leftMean;
            double rightDelta = right[index] - rightMean;
            covariance += leftDelta * rightDelta;
            leftVariance += leftDelta * leftDelta;
            rightVariance += rightDelta * rightDelta;
        }
        double denominator = Math.sqrt(leftVariance * rightVariance);
        return denominator == 0.0d ? Double.NaN : covariance / denominator;
    }

    private static boolean valid(ExpectedReturnEvaluationRow row) {
        return row != null && row.signalDate() != null
                && Double.isFinite(row.predictedExcessReturnPct())
                && Double.isFinite(row.actualExcessReturnPct())
                && Double.isFinite(row.predictedUpsideProbabilityPct())
                && Double.isFinite(row.excessP10Pct())
                && Double.isFinite(row.excessP90Pct());
    }

    private static boolean sameDirection(double predicted, double actual) {
        return (predicted > 0.0d && actual > 0.0d)
                || (predicted < 0.0d && actual < 0.0d)
                || (predicted == 0.0d && actual == 0.0d);
    }

    private static double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }

    private static double square(double value) {
        return value * value;
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }
}

