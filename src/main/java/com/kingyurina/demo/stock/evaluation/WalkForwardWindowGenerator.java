package com.kingyurina.demo.stock.evaluation;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class WalkForwardWindowGenerator {

    private final ExpectedReturnPredictionContract contract;

    public WalkForwardWindowGenerator(ExpectedReturnPredictionContract contract) {
        this.contract = Objects.requireNonNull(contract, "contract");
    }

    public List<WalkForwardWindow> generate(List<ExpectedReturnEvaluationRow> sourceRows) {
        if (sourceRows == null || sourceRows.isEmpty()) {
            return List.of();
        }
        List<ExpectedReturnEvaluationRow> rows = sourceRows.stream()
                .filter(Objects::nonNull)
                .filter(row -> row.signalDate() != null && row.labelEndDate() != null)
                .sorted(Comparator.comparing(ExpectedReturnEvaluationRow::signalDate)
                        .thenComparing(ExpectedReturnEvaluationRow::symbol,
                                Comparator.nullsLast(String::compareTo)))
                .toList();
        if (rows.isEmpty()) {
            return List.of();
        }

        LocalDate firstSignalDate = rows.get(0).signalDate();
        Map<YearMonth, List<ExpectedReturnEvaluationRow>> byMonth = new LinkedHashMap<>();
        for (ExpectedReturnEvaluationRow row : rows) {
            byMonth.computeIfAbsent(YearMonth.from(row.signalDate()), ignored -> new ArrayList<>()).add(row);
        }

        List<WalkForwardWindow> windows = new ArrayList<>();
        for (Map.Entry<YearMonth, List<ExpectedReturnEvaluationRow>> entry : byMonth.entrySet()) {
            YearMonth month = entry.getKey();
            LocalDate testFrom = month.atDay(1);
            if (testFrom.isBefore(firstSignalDate.plusYears(contract.minimumTrainingYears()))) {
                continue;
            }
            LocalDate testTo = month.atEndOfMonth();
            List<ExpectedReturnEvaluationRow> trainRows = rows.stream()
                    .filter(row -> row.labelEndDate().isBefore(testFrom))
                    .filter(row -> row.signalDate().isBefore(testFrom))
                    .toList();
            List<ExpectedReturnEvaluationRow> testRows = List.copyOf(entry.getValue());
            WalkForwardWindow.Status status =
                    trainRows.size() >= contract.minimumTrainingRows()
                            && testRows.size() >= contract.minimumTestRows()
                                    ? WalkForwardWindow.Status.ELIGIBLE
                                    : WalkForwardWindow.Status.INSUFFICIENT_DATA;
            windows.add(new WalkForwardWindow(
                    month.toString(),
                    testFrom,
                    testTo,
                    trainRows,
                    testRows,
                    status));
        }
        return List.copyOf(windows);
    }
}

