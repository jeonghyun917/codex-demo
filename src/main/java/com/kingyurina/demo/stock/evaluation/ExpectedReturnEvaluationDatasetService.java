package com.kingyurina.demo.stock.evaluation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.kingyurina.demo.stock.StockBacktestResult;
import com.kingyurina.demo.stock.StockBenchmarkReturn;
import com.kingyurina.demo.stock.StockExpectedReturnSnapshot;
import com.kingyurina.demo.stock.StockIndexMembershipSnapshot;
import com.kingyurina.demo.stock.evaluation.ExpectedReturnEvaluationExclusion.Code;

public final class ExpectedReturnEvaluationDatasetService {

    public ExpectedReturnEvaluationDataset assemble(
            ExpectedReturnPredictionContract contract,
            String modelVersion,
            List<StockExpectedReturnSnapshot> predictionRows,
            List<StockBacktestResult> realizedRows,
            List<StockBenchmarkReturn> benchmarkRows,
            List<StockIndexMembershipSnapshot> membershipRows,
            LocalDate asOfDate) {
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(modelVersion, "modelVersion");
        Objects.requireNonNull(asOfDate, "asOfDate");

        List<ExpectedReturnEvaluationExclusion> exclusions = new ArrayList<>();
        Map<Key, List<StockExpectedReturnSnapshot>> predictions = safe(predictionRows).stream()
                .filter(row -> modelVersion.equals(row.getModelVersion()))
                .filter(row -> Integer.valueOf(contract.horizonTradingDays()).equals(row.getHorizonDays()))
                .filter(row -> row.getSignalDate() != null && row.getSymbol() != null)
                .collect(Collectors.groupingBy(row -> new Key(row.getSignalDate(), row.getSymbol()),
                        LinkedHashMap::new, Collectors.toList()));
        Map<Key, StockIndexMembershipSnapshot> memberships = safe(membershipRows).stream()
                .filter(row -> contract.indexCode().equals(row.getIndexCode()))
                .filter(row -> row.getSnapshotDate() != null && row.getSymbol() != null)
                .collect(Collectors.toMap(row -> new Key(row.getSnapshotDate(), row.getSymbol()),
                        Function.identity(), (left, right) -> left, HashMap::new));
        List<StockBenchmarkReturn> benchmarks = safe(benchmarkRows).stream()
                .filter(row -> contract.indexCode().equals(row.getIndexCode()))
                .filter(row -> row.getTradeDate() != null && row.getReturnPct() != null)
                .toList();

        List<StockBacktestResult> targets = safe(realizedRows).stream()
                .filter(row -> Integer.valueOf(contract.horizonTradingDays()).equals(row.getHorizonDays()))
                .filter(row -> row.getSignalDate() != null && row.getSymbol() != null)
                .toList();
        List<ExpectedReturnEvaluationRow> validRows = new ArrayList<>();
        for (StockBacktestResult actual : targets) {
            Key key = new Key(actual.getSignalDate(), actual.getSymbol());
            List<StockExpectedReturnSnapshot> matches = predictions.getOrDefault(key, List.of());
            boolean excluded = false;
            if (matches.isEmpty()) {
                exclusions.add(exclusion(Code.MISSING_PREDICTION, actual, "No model snapshot"));
                excluded = true;
            }
            if (matches.size() > 1) {
                exclusions.add(exclusion(Code.DUPLICATE_PREDICTION, actual,
                        "Expected one snapshot but found " + matches.size()));
                excluded = true;
            }
            StockExpectedReturnSnapshot prediction = matches.isEmpty() ? null : matches.get(0);
            if (actual.getForwardReturnPct() == null || actual.getEntryTradeDate() == null
                    || actual.getExitTradeDate() == null) {
                exclusions.add(exclusion(Code.MISSING_REALIZED_RETURN, actual, "Incomplete realized label"));
                excluded = true;
            }
            StockIndexMembershipSnapshot membership = memberships.get(key);
            if (membership == null || !Boolean.TRUE.equals(membership.getMember())) {
                exclusions.add(exclusion(Code.MISSING_MEMBERSHIP, actual, "No active PIT membership"));
                excluded = true;
            }
            if (prediction != null && prediction.getCalculatedAt() != null
                    && prediction.getCalculatedAt().toLocalDate().isAfter(asOfDate)) {
                exclusions.add(exclusion(Code.PIT_VIOLATION, actual,
                        "Prediction calculated after evaluation as-of date"));
                excluded = true;
            }
            Double benchmarkReturn = compoundBenchmark(benchmarks,
                    actual.getEntryTradeDate(), actual.getExitTradeDate());
            if (benchmarkReturn == null) {
                exclusions.add(exclusion(Code.MISSING_BENCHMARK, actual, "Incomplete benchmark holding period"));
                excluded = true;
            }
            if (prediction != null && !finitePrediction(prediction)) {
                exclusions.add(exclusion(Code.INVALID_VALUE, actual, "Non-finite prediction value"));
                excluded = true;
            }
            if (excluded) {
                continue;
            }

            double realizedReturn = number(actual.getForwardReturnPct());
            double benchmark = benchmarkReturn;
            LocalDateTime availableAt = prediction.getCalculatedAt() == null
                    ? LocalDateTime.of(actual.getSignalDate(), LocalTime.MAX)
                    : prediction.getCalculatedAt();
            validRows.add(new ExpectedReturnEvaluationRow(
                    contract.version(),
                    contract.indexCode(),
                    modelVersion,
                    actual.getSignalDate(),
                    availableAt,
                    actual.getExitTradeDate(),
                    actual.getSymbol(),
                    number(prediction.getExpectedExcessReturnPct()),
                    realizedReturn - benchmark,
                    number(prediction.getCalibratedUpsideProbabilityPct()),
                    number(prediction.getExcessP10Pct()),
                    number(prediction.getExcessP90Pct()),
                    realizedReturn,
                    benchmark,
                    membership.getSector() == null ? actual.getSector() : membership.getSector(),
                    "UNKNOWN"));
        }
        double coverage = targets.isEmpty() ? 0.0d : validRows.size() * 100.0d / targets.size();
        int pitViolations = (int) exclusions.stream().filter(row -> row.code() == Code.PIT_VIOLATION).count();
        return new ExpectedReturnEvaluationDataset(validRows, exclusions, targets.size(), coverage, pitViolations);
    }

    private static Double compoundBenchmark(List<StockBenchmarkReturn> rows,
            LocalDate entryDate, LocalDate exitDate) {
        if (entryDate == null || exitDate == null || !exitDate.isAfter(entryDate)) {
            return null;
        }
        List<StockBenchmarkReturn> period = rows.stream()
                .filter(row -> row.getTradeDate().isAfter(entryDate)
                        && !row.getTradeDate().isAfter(exitDate))
                .sorted(java.util.Comparator.comparing(StockBenchmarkReturn::getTradeDate))
                .toList();
        if (period.isEmpty()) {
            return null;
        }
        double equity = 1.0d;
        for (StockBenchmarkReturn row : period) {
            equity *= 1.0d + number(row.getReturnPct()) / 100.0d;
        }
        return (equity - 1.0d) * 100.0d;
    }

    private static boolean finitePrediction(StockExpectedReturnSnapshot row) {
        return finite(row.getExpectedExcessReturnPct())
                && finite(row.getCalibratedUpsideProbabilityPct())
                && finite(row.getExcessP10Pct())
                && finite(row.getExcessP90Pct());
    }

    private static boolean finite(BigDecimal value) {
        return value != null && Double.isFinite(value.doubleValue());
    }

    private static double number(BigDecimal value) {
        return value == null ? Double.NaN : value.doubleValue();
    }

    private static ExpectedReturnEvaluationExclusion exclusion(Code code,
            StockBacktestResult actual, String detail) {
        return new ExpectedReturnEvaluationExclusion(code, actual.getSignalDate(), actual.getSymbol(), detail);
    }

    private static <T> List<T> safe(List<T> rows) {
        return rows == null ? List.of() : rows;
    }

    private record Key(LocalDate date, String symbol) {
    }
}

