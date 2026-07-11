package com.kingyurina.demo.stock;

import java.util.List;

public record StockApiDataSourceView(
        String provider,
        String label,
        String description,
        List<String> datasets,
        String latestUpdate,
        String latestData,
        String coverage,
        String status,
        String tone) {
}
