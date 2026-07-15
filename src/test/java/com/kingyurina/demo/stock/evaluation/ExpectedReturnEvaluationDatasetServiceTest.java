package com.kingyurina.demo.stock.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.kingyurina.demo.stock.StockBacktestResult;
import com.kingyurina.demo.stock.StockBenchmarkReturn;
import com.kingyurina.demo.stock.StockExpectedReturnSnapshot;
import com.kingyurina.demo.stock.StockIndexMembershipSnapshot;

class ExpectedReturnEvaluationDatasetServiceTest {

    private final ExpectedReturnEvaluationDatasetService service =
            new ExpectedReturnEvaluationDatasetService();

    @Test
    void compoundsBenchmarkReturnsAcrossTheRealizedHoldingPeriod() {
        LocalDate signalDate = LocalDate.of(2024, 1, 2);
        StockBacktestResult result = result("AAPL", signalDate,
                LocalDate.of(2024, 1, 3), LocalDate.of(2024, 1, 5), 5.0d);

        ExpectedReturnEvaluationDataset dataset = service.assemble(
                ExpectedReturnPredictionContract.sp500Excess20dV1(),
                "EXPECTED_RETURN_V9",
                List.of(prediction("AAPL", signalDate, 3.0d, LocalDateTime.of(2024, 2, 1, 12, 0))),
                List.of(result),
                List.of(benchmark(LocalDate.of(2024, 1, 4), 1.0d),
                        benchmark(LocalDate.of(2024, 1, 5), 2.0d)),
                List.of(membership("AAPL", signalDate, true)),
                LocalDate.of(2025, 1, 1));

        assertEquals(1, dataset.rows().size());
        assertEquals(3.02d, dataset.rows().get(0).benchmarkReturnPct(), 1e-9);
        assertEquals(1.98d, dataset.rows().get(0).actualExcessReturnPct(), 1e-9);
    }

    @Test
    void recordsMissingMembershipAndFutureCalculatedPredictionAsExclusions() {
        LocalDate signalDate = LocalDate.of(2024, 1, 2);
        StockBacktestResult result = result("AAPL", signalDate,
                LocalDate.of(2024, 1, 3), LocalDate.of(2024, 1, 5), 5.0d);
        LocalDate asOfDate = LocalDate.of(2024, 6, 1);

        ExpectedReturnEvaluationDataset dataset = service.assemble(
                ExpectedReturnPredictionContract.sp500Excess20dV1(),
                "EXPECTED_RETURN_V9",
                List.of(prediction("AAPL", signalDate, 3.0d, asOfDate.plusDays(1).atTime(12, 0))),
                List.of(result),
                List.of(benchmark(LocalDate.of(2024, 1, 4), 1.0d),
                        benchmark(LocalDate.of(2024, 1, 5), 2.0d)),
                List.of(),
                asOfDate);

        assertTrue(dataset.exclusions().stream()
                .anyMatch(row -> row.code() == ExpectedReturnEvaluationExclusion.Code.PIT_VIOLATION));
        assertTrue(dataset.exclusions().stream()
                .anyMatch(row -> row.code() == ExpectedReturnEvaluationExclusion.Code.MISSING_MEMBERSHIP));
        assertEquals(0, dataset.rows().size());
    }

    private static StockExpectedReturnSnapshot prediction(String symbol, LocalDate date,
            double expectedExcess, LocalDateTime calculatedAt) {
        StockExpectedReturnSnapshot row = new StockExpectedReturnSnapshot();
        row.setIndexCode("SP500");
        row.setSignalDate(date);
        row.setSymbol(symbol);
        row.setHorizonDays(20);
        row.setExpectedExcessReturnPct(BigDecimal.valueOf(expectedExcess));
        row.setCalibratedUpsideProbabilityPct(BigDecimal.valueOf(60.0d));
        row.setExcessP10Pct(BigDecimal.valueOf(-5.0d));
        row.setExcessP90Pct(BigDecimal.valueOf(8.0d));
        row.setModelVersion("EXPECTED_RETURN_V9");
        row.setCalculatedAt(calculatedAt);
        return row;
    }

    private static StockBacktestResult result(String symbol, LocalDate signalDate,
            LocalDate entryDate, LocalDate exitDate, double returnPct) {
        StockBacktestResult row = new StockBacktestResult();
        row.setSymbol(symbol);
        row.setSignalDate(signalDate);
        row.setHorizonDays(20);
        row.setEntryTradeDate(entryDate);
        row.setExitTradeDate(exitDate);
        row.setForwardReturnPct(BigDecimal.valueOf(returnPct));
        row.setSector("Technology");
        return row;
    }

    private static StockBenchmarkReturn benchmark(LocalDate date, double returnPct) {
        StockBenchmarkReturn row = new StockBenchmarkReturn();
        row.setIndexCode("SP500");
        row.setTradeDate(date);
        row.setReturnPct(BigDecimal.valueOf(returnPct));
        return row;
    }

    private static StockIndexMembershipSnapshot membership(String symbol, LocalDate date, boolean member) {
        StockIndexMembershipSnapshot row = new StockIndexMembershipSnapshot();
        row.setIndexCode("SP500");
        row.setSymbol(symbol);
        row.setSnapshotDate(date);
        row.setMember(member);
        row.setSector("Technology");
        row.setSource("PIT_TEST");
        return row;
    }
}
