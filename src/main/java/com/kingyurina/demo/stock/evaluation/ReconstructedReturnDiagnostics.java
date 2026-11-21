package com.kingyurina.demo.stock.evaluation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import com.kingyurina.demo.stock.StockBacktestResult;
import com.kingyurina.demo.stock.StockBenchmarkReturn;
import com.kingyurina.demo.stock.StockExpectedReturnSnapshot;
import com.kingyurina.demo.stock.StockIndexMembershipSnapshot;

/** Descriptive reconstruction only. This does not confer PIT eligibility or call promotion gates. */
public final class ReconstructedReturnDiagnostics {
    public record Report(String indexCode, String modelVersion, LocalDate asOf,
            String classification, boolean promotionAllowed, String benchmarkDefinition,
            int targetCount, int usableRows, double coveragePct, int immatureTargets,
            Map<String, Integer> exclusionCounts, ExpectedReturnMetrics metrics,
            Map<String, ExpectedReturnMetrics> metricsByMonth,
            LocalDate firstTargetDate, LocalDate lastTargetDate,
            LocalDate firstSignalDate, LocalDate lastSignalDate,
            LocalDate firstLabelEndDate, LocalDate lastLabelEndDate,
            int postSignalCalculatedRows, LocalDateTime firstCalculatedAt, LocalDateTime lastCalculatedAt,
            List<String> warnings) {}
    private record Key(LocalDate date, String symbol) {}
    private record Holding(LocalDate entry, LocalDate exit) {}

    public Report evaluate(String index, String model, List<StockExpectedReturnSnapshot> predictionRows,
            List<StockBacktestResult> outcomeRows, List<StockBenchmarkReturn> benchmarkRows,
            List<StockIndexMembershipSnapshot> membershipRows, LocalDate asOf) {
        if (index == null || index.isBlank() || model == null || model.isBlank() || asOf == null)
            throw new IllegalArgumentException("Index, model and as-of date are required");
        var predictions = safe(predictionRows).stream()
                .filter(p -> index.equals(p.getIndexCode()) && model.equals(p.getModelVersion())
                        && Integer.valueOf(20).equals(p.getHorizonDays()) && keyable(p.getSignalDate(), p.getSymbol()))
                .collect(Collectors.groupingBy(p -> new Key(p.getSignalDate(), p.getSymbol())));
        var outcomes = safe(outcomeRows).stream()
                .filter(r -> Integer.valueOf(20).equals(r.getHorizonDays()) && keyable(r.getSignalDate(), r.getSymbol()))
                .collect(Collectors.groupingBy(r -> new Key(r.getSignalDate(), r.getSymbol())));
        var memberships = safe(membershipRows).stream()
                .filter(m -> index.equals(m.getIndexCode()) && keyable(m.getSnapshotDate(), m.getSymbol())
                        && !m.getSnapshotDate().isAfter(asOf))
                .collect(Collectors.groupingBy(m -> new Key(m.getSnapshotDate(), m.getSymbol())));
        var benchmarks = safe(benchmarkRows).stream().filter(b -> index.equals(b.getIndexCode())
                && b.getTradeDate() != null && !b.getTradeDate().isAfter(asOf)).toList();
        var sessions = benchmarks.stream().map(StockBenchmarkReturn::getTradeDate).distinct().sorted().toList();
        var targets = memberships.keySet().stream()
                .filter(k -> memberships.get(k).stream().anyMatch(m -> Boolean.TRUE.equals(m.getMember())))
                .sorted(Comparator.comparing(Key::date).thenComparing(Key::symbol)).toList();
        var counts = new TreeMap<String, Integer>();
        var monthlyTargets = new TreeMap<String, Integer>();
        var rows = new ArrayList<ExpectedReturnEvaluationRow>();
        var benchmarkCache = new HashMap<Holding, Optional<Double>>();
        int targetCount = 0, immature = 0;
        LocalDate firstTarget = null, lastTarget = null;
        for (Key key : targets) {
            int position = Collections.binarySearch(sessions, key.date());
            int entryIndex = position >= 0 ? position + 1 : -position - 1;
            if (entryIndex + 20 >= sessions.size()) { immature++; continue; }
            LocalDate expectedEntry = sessions.get(entryIndex), expectedExit = sessions.get(entryIndex + 20);
            targetCount++;
            if (firstTarget == null) firstTarget = key.date();
            lastTarget = key.date();
            monthlyTargets.merge(YearMonth.from(key.date()).toString(), 1, Integer::sum);
            var reasons = new LinkedHashSet<String>();
            var ps = predictions.getOrDefault(key, List.of());
            var os = outcomes.getOrDefault(key, List.of());
            if (memberships.get(key).size() != 1) reasons.add("DUPLICATE_MEMBERSHIP");
            if (ps.isEmpty()) reasons.add("MISSING_PREDICTION");
            if (ps.size() > 1) reasons.add("DUPLICATE_PREDICTION");
            if (os.isEmpty()) reasons.add("MISSING_OUTCOME");
            if (os.size() > 1) reasons.add("DUPLICATE_OUTCOME");
            StockExpectedReturnSnapshot prediction = ps.size() == 1 ? ps.getFirst() : null;
            StockBacktestResult outcome = os.size() == 1 ? os.getFirst() : null;
            if (prediction != null && !validPrediction(prediction)) reasons.add("INVALID_PREDICTION");
            Double benchmark = null;
            if (outcome != null) {
                if (!finite(outcome.getForwardReturnPct()) || outcome.getForwardReturnPct().doubleValue() < -100
                        || !expectedEntry.equals(outcome.getEntryTradeDate()) || !expectedExit.equals(outcome.getExitTradeDate()))
                    reasons.add("INVALID_OUTCOME");
                Holding holding = new Holding(outcome.getEntryTradeDate(), outcome.getExitTradeDate());
                benchmark = benchmarkCache.computeIfAbsent(holding, h -> Optional.ofNullable(
                        CompleteBenchmarkReturn.compound(benchmarks, index, h.entry(), h.exit(), 20))).orElse(null);
            } else {
                Holding holding = new Holding(expectedEntry, expectedExit);
                benchmark = benchmarkCache.computeIfAbsent(holding, h -> Optional.ofNullable(
                        CompleteBenchmarkReturn.compound(benchmarks, index, h.entry(), h.exit(), 20))).orElse(null);
            }
            if (benchmark == null) reasons.add("INCOMPLETE_BENCHMARK");
            if (!reasons.isEmpty()) {
                reasons.forEach(r -> counts.merge(r, 1, Integer::sum));
                continue;
            }
            double actual = outcome.getForwardReturnPct().doubleValue();
            rows.add(new ExpectedReturnEvaluationRow("RECONSTRUCTED_RESEARCH_NOT_PIT", index, model,
                    key.date(), prediction.getCalculatedAt(), outcome.getExitTradeDate(), key.symbol(),
                    prediction.getExpectedExcessReturnPct().doubleValue(), actual - benchmark,
                    prediction.getCalibratedUpsideProbabilityPct().doubleValue(),
                    prediction.getExcessP10Pct().doubleValue(), prediction.getExcessP90Pct().doubleValue(),
                    actual, benchmark, memberships.get(key).getFirst().getSector(), "UNKNOWN"));
        }
        var calculator = new ExpectedReturnMetricsCalculator();
        var rowsByMonth = rows.stream().collect(Collectors.groupingBy(r -> YearMonth.from(r.signalDate()).toString()));
        var monthly = new TreeMap<String, ExpectedReturnMetrics>();
        monthlyTargets.forEach((month, target) -> monthly.put(month,
                calculator.calculate(rowsByMonth.getOrDefault(month, List.of()), target)));
        return new Report(index, model, asOf, "RECONSTRUCTED_RESEARCH_NOT_PIT", false, "PRICE_INDEX_NOT_TOTAL_RETURN",
                targetCount, rows.size(), targetCount == 0 ? 0 : rows.size() * 100.0 / targetCount,
                immature, Collections.unmodifiableMap(counts), calculator.calculate(rows, targetCount),
                Collections.unmodifiableMap(monthly), firstTarget, lastTarget,
                rows.stream().map(ExpectedReturnEvaluationRow::signalDate).min(LocalDate::compareTo).orElse(null),
                rows.stream().map(ExpectedReturnEvaluationRow::signalDate).max(LocalDate::compareTo).orElse(null),
                rows.stream().map(ExpectedReturnEvaluationRow::labelEndDate).min(LocalDate::compareTo).orElse(null),
                rows.stream().map(ExpectedReturnEvaluationRow::labelEndDate).max(LocalDate::compareTo).orElse(null),
                (int) rows.stream().filter(r -> r.availableAt().toLocalDate().isAfter(r.signalDate())).count(),
                rows.stream().map(ExpectedReturnEvaluationRow::availableAt).min(LocalDateTime::compareTo).orElse(null),
                rows.stream().map(ExpectedReturnEvaluationRow::availableAt).max(LocalDateTime::compareTo).orElse(null),
                List.of("Reconstructed research only; original calculatedAt retained, not point-in-time evidence or promotion eligibility.",
                        "Coverage is usable rows / all matured active membership keys in the supplied namespace; exclusion reasons overlap.",
                        "Maturity uses supplied benchmark sessions; missing sessions cannot establish a complete exchange calendar.",
                        "Monthly groups use signal month; overlapping 20-session labels are not independent observations.",
                        "Price-index benchmark is not total return; these descriptive metrics are not portfolio returns or a performance pass."));
    }
    private static boolean validPrediction(StockExpectedReturnSnapshot p) {
        return p.getCalculatedAt() != null && finite(p.getExpectedReturnPct()) && finite(p.getExpectedExcessReturnPct())
                && finite(p.getCalibratedUpsideProbabilityPct()) && p.getCalibratedUpsideProbabilityPct().signum() >= 0
                && p.getCalibratedUpsideProbabilityPct().compareTo(BigDecimal.valueOf(100)) <= 0
                && finite(p.getExcessP10Pct()) && finite(p.getExcessP90Pct())
                && p.getExcessP10Pct().compareTo(p.getExcessP90Pct()) <= 0;
    }
    private static boolean finite(BigDecimal n) { return n != null && Double.isFinite(n.doubleValue()); }
    private static boolean keyable(LocalDate date, String symbol) { return date != null && symbol != null && !symbol.isBlank(); }
    private static <T> List<T> safe(List<T> rows) {
        return rows == null ? List.of() : rows.stream().filter(Objects::nonNull).toList();
    }
}
