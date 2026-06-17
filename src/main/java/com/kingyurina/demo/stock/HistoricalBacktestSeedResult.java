package com.kingyurina.demo.stock;

public record HistoricalBacktestSeedResult(
        int seedDateCount,
        int requestedSymbols,
        int snapshotCount,
        int skippedCount,
        int backtestResultCount) {
}
