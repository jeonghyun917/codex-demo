package com.kingyurina.demo.stock.evaluation;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.kingyurina.demo.stock.StockBenchmarkReturn;

/** One price-return definition shared by labels and evaluation; null means unusable evidence. */
public final class CompleteBenchmarkReturn {
    private CompleteBenchmarkReturn() { }

    public static Double compound(List<StockBenchmarkReturn> rows, String indexCode,
            LocalDate entryDate, LocalDate exitDate, int horizonDays) {
        if (rows == null || entryDate == null || exitDate == null || horizonDays <= 0
                || !exitDate.isAfter(entryDate)) return null;
        List<StockBenchmarkReturn> period = rows.stream()
                .filter(row -> row != null && indexCode.equals(row.getIndexCode()) && row.getTradeDate() != null)
                .filter(row -> row.getTradeDate().isAfter(entryDate) && !row.getTradeDate().isAfter(exitDate))
                .sorted(Comparator.comparing(StockBenchmarkReturn::getTradeDate)).toList();
        if (period.size() != horizonDays || !period.getLast().getTradeDate().equals(exitDate)) return null;
        Set<LocalDate> dates = new HashSet<>();
        String source = period.getFirst().getSource();
        if (source == null || source.isBlank()) return null;
        double equity = 1;
        for (StockBenchmarkReturn row : period) {
            if (!dates.add(row.getTradeDate()) || !source.equals(row.getSource()) || row.getReturnPct() == null) return null;
            if (row.getConstituentCount() != null && (row.getConstituentCount() <= 0
                    || row.getCoverageCount() == null || !row.getConstituentCount().equals(row.getCoverageCount()))) return null;
            double value = row.getReturnPct().doubleValue();
            if (!Double.isFinite(value) || value < -100) return null;
            equity *= 1 + value / 100;
            if (!Double.isFinite(equity)) return null;
        }
        double result = (equity - 1) * 100;
        return Double.isFinite(result) ? result : null;
    }
}
