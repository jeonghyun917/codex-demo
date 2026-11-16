package com.kingyurina.demo.stock.evaluation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.kingyurina.demo.stock.StockBacktestResult;
import com.kingyurina.demo.stock.StockBenchmarkReturn;
import com.kingyurina.demo.stock.StockExpectedReturnSnapshot;
import com.kingyurina.demo.stock.StockIndexMembershipSnapshot;
import com.kingyurina.demo.stock.StockMacroRegimeSnapshot;
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
        return assemble(contract, modelVersion, predictionRows, realizedRows, benchmarkRows, membershipRows,
                asOfDate, List.of());
    }

    public ExpectedReturnEvaluationDataset assemble(
            ExpectedReturnPredictionContract contract,
            String modelVersion,
            List<StockExpectedReturnSnapshot> predictionRows,
            List<StockBacktestResult> realizedRows,
            List<StockBenchmarkReturn> benchmarkRows,
            List<StockIndexMembershipSnapshot> membershipRows,
            LocalDate asOfDate,
            List<StockMacroRegimeSnapshot> regimeRows) {
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(modelVersion, "modelVersion");
        Objects.requireNonNull(asOfDate, "asOfDate");

        List<ExpectedReturnEvaluationExclusion> exclusions = new ArrayList<>();
        Map<LocalDate, List<StockMacroRegimeSnapshot>> regimes = safe(regimeRows).stream()
                .filter(row -> contract.indexCode().equals(row.getIndexCode()) && row.getSnapshotDate() != null)
                .collect(Collectors.groupingBy(StockMacroRegimeSnapshot::getSnapshotDate));
        Map<HoldingWindow, Optional<Double>> benchmarkCache = new HashMap<>();
        Map<Key, List<StockExpectedReturnSnapshot>> predictions = safe(predictionRows).stream()
                .filter(row -> contract.indexCode().equals(row.getIndexCode()))
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
        Map<Key, List<StockBacktestResult>> realized = safe(realizedRows).stream()
                .filter(row -> Integer.valueOf(contract.horizonTradingDays()).equals(row.getHorizonDays()))
                .filter(row -> row.getSignalDate() != null && row.getSymbol() != null)
                .filter(row -> !row.getSignalDate().isAfter(asOfDate))
                .collect(Collectors.groupingBy(row -> new Key(row.getSignalDate(), row.getSymbol()),
                        LinkedHashMap::new, Collectors.toList()));
        Map<Key, StockBacktestResult> targets = new LinkedHashMap<>();
        realized.forEach((key, values) -> targets.put(key, values.getFirst()));
        memberships.forEach((key, membership) -> {
            if (Boolean.TRUE.equals(membership.getMember()) && !key.date().isAfter(asOfDate)) {
                targets.computeIfAbsent(key, ignored -> missingTarget(key, contract.horizonTradingDays()));
            }
        });
        List<ExpectedReturnEvaluationRow> validRows = new ArrayList<>();
        for (StockBacktestResult actual : targets.values()) {
            Key key = new Key(actual.getSignalDate(), actual.getSymbol());
            List<StockExpectedReturnSnapshot> matches = predictions.getOrDefault(key, List.of());
            boolean excluded = false;
            if (realized.getOrDefault(key, List.of()).size() > 1) {
                exclusions.add(exclusion(Code.DUPLICATE_REALIZED_RETURN, actual, "Duplicate outcome key"));
                excluded = true;
            }
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
                    || actual.getExitTradeDate() == null || actual.getExitTradeDate().isAfter(asOfDate)) {
                exclusions.add(exclusion(Code.MISSING_REALIZED_RETURN, actual, "Incomplete realized label"));
                excluded = true;
            }
            StockIndexMembershipSnapshot membership = memberships.get(key);
            if (membership == null || !Boolean.TRUE.equals(membership.getMember())) {
                exclusions.add(exclusion(Code.MISSING_MEMBERSHIP, actual, "No active PIT membership"));
                excluded = true;
            } else if (!"OFFICIAL_PIT".equals(membership.getSource())
                    && !"LIVE_CAPTURE".equals(membership.getSource())) {
                exclusions.add(exclusion(Code.UNPROVEN_MEMBERSHIP_PROVENANCE, actual,
                        "Historical membership provenance is not verified: " + membership.getSource()));
                excluded = true;
            }
            if (prediction != null && (prediction.getCalculatedAt() == null
                    || prediction.getCalculatedAt().toLocalDate().isAfter(actual.getSignalDate())
                    || prediction.getCalculatedAt().toLocalDate().isBefore(actual.getSignalDate()))) {
                exclusions.add(exclusion(Code.PIT_VIOLATION, actual,
                        "Missing availability timestamp or prediction not recorded on its decision day"));
                excluded = true;
            }
            if (prediction != null && (prediction.getSource() == null
                    || !prediction.getSource().startsWith("LIVE_"))) {
                exclusions.add(exclusion(Code.UNPROVEN_PREDICTION_PROVENANCE, actual,
                        "Reconstruction requires input, training and calibration availability evidence; source name is not proof"));
                excluded = true;
            }
            HoldingWindow holding = new HoldingWindow(actual.getEntryTradeDate(), actual.getExitTradeDate());
            Double benchmarkReturn = benchmarkCache.computeIfAbsent(holding, window -> Optional.ofNullable(
                    CompleteBenchmarkReturn.compound(safe(benchmarkRows), contract.indexCode(), window.entry(),
                            window.exit(), contract.horizonTradingDays()))).orElse(null);
            if (benchmarkReturn == null) {
                exclusions.add(exclusion(Code.MISSING_BENCHMARK, actual, "Incomplete benchmark holding period"));
                excluded = true;
            }
            if (prediction != null && !finitePrediction(prediction)) {
                exclusions.add(exclusion(Code.INVALID_VALUE, actual, "Non-finite prediction value"));
                excluded = true;
            }
            if (actual.getForwardReturnPct() != null && (!finite(actual.getForwardReturnPct())
                    || number(actual.getForwardReturnPct()) < -100.0d)
                    || actual.getEntryTradeDate() != null && !actual.getEntryTradeDate().isAfter(actual.getSignalDate())) {
                exclusions.add(exclusion(Code.INVALID_VALUE, actual, "Invalid realized return or entry before decision"));
                excluded = true;
            }
            if (excluded) {
                continue;
            }

            double realizedReturn = number(actual.getForwardReturnPct());
            double benchmark = benchmarkReturn;
            LocalDateTime availableAt = prediction.getCalculatedAt();
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
                    knownRegime(regimes.getOrDefault(actual.getSignalDate(), List.of()), availableAt)));
        }
        double coverage = targets.isEmpty() ? 0.0d : validRows.size() * 100.0d / targets.size();
        int pitViolations = (int) exclusions.stream().filter(row -> row.code() == Code.PIT_VIOLATION).count();
        Map<LocalDate, Integer> targetCounts = new HashMap<>();
        targets.keySet().forEach(key -> targetCounts.merge(key.date(), 1, Integer::sum));
        return new ExpectedReturnEvaluationDataset(validRows, exclusions, targets.size(), coverage, pitViolations, targetCounts);
    }

    private static String knownRegime(List<StockMacroRegimeSnapshot> rows, LocalDateTime availableAt) {
        if (rows.size() != 1) return "UNKNOWN";
        StockMacroRegimeSnapshot regime = rows.getFirst();
        if (regime.getCalculatedAt() == null || regime.getCalculatedAt().isAfter(availableAt)
                || !regime.getCalculatedAt().toLocalDate().equals(regime.getSnapshotDate())
                || regime.getSource() == null || regime.getSource().isBlank()
                || regime.getRegimeLabel() == null || regime.getRegimeLabel().isBlank()) return "UNKNOWN";
        return regime.getRegimeLabel();
    }

    private static StockBacktestResult missingTarget(Key key, int horizon) {
        StockBacktestResult row = new StockBacktestResult();
        row.setSignalDate(key.date()); row.setSymbol(key.symbol()); row.setHorizonDays(horizon);
        return row;
    }

    private static boolean finitePrediction(StockExpectedReturnSnapshot row) {
        return finite(row.getExpectedExcessReturnPct())
                && finite(row.getCalibratedUpsideProbabilityPct())
                && number(row.getCalibratedUpsideProbabilityPct()) >= 0
                && number(row.getCalibratedUpsideProbabilityPct()) <= 100
                && finite(row.getExcessP10Pct())
                && finite(row.getExcessP90Pct())
                && row.getExcessP10Pct().compareTo(row.getExcessP90Pct()) <= 0;
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

    private record HoldingWindow(LocalDate entry, LocalDate exit) {
    }
}

