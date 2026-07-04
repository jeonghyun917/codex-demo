package com.kingyurina.demo.stock;

import java.util.List;

public record StockMacroRegimeView(
        String label,
        String tone,
        String score,
        String snapshotDate,
        String summary,
        List<Metric> metrics) {

    public record Metric(String label, String value, String note, String tone) {
    }
}
