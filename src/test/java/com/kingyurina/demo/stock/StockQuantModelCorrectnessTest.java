package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

class StockQuantModelCorrectnessTest {
    private static final LocalDate TARGET = LocalDate.of(2025, 6, 1);

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    void futureOutcomeMutationCannotChangeAnEarlierPrediction(int version) throws Exception {
        List<StockBacktestResult> rows = trainingRows();
        List<StockExpectedReturnSnapshot> before = predict(version, rows);
        rows.stream().filter(r -> r.getExitTradeDate().isAfter(TARGET))
                .forEach(r -> r.setForwardReturnPct(BigDecimal.valueOf(-90)));
        List<StockExpectedReturnSnapshot> after = predict(version, rows);
        assertEquals(1, before.size());
        assertEquals(before.get(0).getExpectedReturnPct(), after.get(0).getExpectedReturnPct());
        assertEquals(before.get(0).getCalibratedUpsideProbabilityPct(),
                after.get(0).getCalibratedUpsideProbabilityPct());
        assertEquals(50, before.get(0).getSampleCount());
    }

    @Test
    void excessTrainingLabelUsesStoredBenchmarkNotSurvivorEqualWeightMean() throws Exception {
        var snapshots = predict(2, trainingRows());
        assertEquals(-2.0, snapshots.get(0).getExpectedExcessReturnPct().doubleValue(), 0.00001);
        assertTrue(snapshots.get(0).getModelVersion().contains("CORRECTED_20261017"));
        assertTrue(snapshots.get(0).getSource().contains("CUTOFF_LT_20250601"));
    }

    @Test
    void missingTargetBenchmarkDoesNotBecomeAFalseOutcomeInCalibration() {
        var mapper = mock(StockBacktestMapper.class);
        List<StockBacktestResult> rows = new ArrayList<>(trainingRows().subList(0, 100));
        for (int i = 0; i < 300; i++) rows.add(row("T" + i, TARGET, TARGET.plusDays(20), 4));
        when(mapper.findResults(eq("SP500"), anyInt())).thenReturn(rows);
        when(mapper.findBenchmarkReturns(eq("SP500"), any(), any()))
                .thenReturn(benchmark().stream().filter(b -> b.getTradeDate().isBefore(TARGET.minusDays(30))).toList());
        List<StockExpectedReturnCalibration> saved = new ArrayList<>();
        doAnswer(call -> { saved.add(call.getArgument(0)); return null; })
                .when(mapper).upsertExpectedReturnCalibration(any());
        var result = service(mapper).refreshExpectedReturnSnapshots("SP500", 1000, 1, 50);
        assertEquals(300, result.savedRows());
        assertTrue(saved.isEmpty(), "Unknown benchmark means unknown outcome, not a negative outcome");
    }

    @Test
    void coldPortfolioGetDoesNotRunOrPersistResearch() {
        var mapper = mock(StockBacktestMapper.class);
        var view = service(mapper).build("SP500");
        assertTrue(view.strategies().isEmpty());
        verify(mapper, never()).findResults(anyString(), anyInt());
        verify(mapper, never()).upsertPortfolioViewSnapshot(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 7})
    void historicalCalibrationCannotReadFullHistoryAggregate(int version) throws Exception {
        var mapper = mock(StockBacktestMapper.class);
        when(mapper.findResults(eq("SP500"), anyInt())).thenReturn(trainingRows());
        when(mapper.findBenchmarkReturns(eq("SP500"), any(), any())).thenReturn(benchmark());
        var source = new StockExpectedReturnSnapshot();
        source.setIndexCode("SP500"); source.setSignalDate(TARGET); source.setSymbol("TARGET");
        source.setHorizonDays(20); source.setSampleCount(300); source.setConfidence(60);
        source.setModelVersion("EXPECTED_RETURN_V" + (version - 1) + "_CORRECTED_20261017");
        source.setExpectedReturnPct(BigDecimal.valueOf(4));
        source.setExpectedExcessReturnPct(BigDecimal.valueOf(2));
        source.setUpsideProbabilityPct(BigDecimal.valueOf(60));
        source.setCalibratedUpsideProbabilityPct(BigDecimal.valueOf(60)); source.setCalibrationBucket(60);
        when(mapper.findExpectedReturnSnapshots(eq("SP500"), any(), any())).thenReturn(List.of(source));
        var futureAggregate = new StockExpectedReturnCalibration();
        futureAggregate.setHorizonDays(20); futureAggregate.setProbabilityBucket(60);
        futureAggregate.setSampleCount(500); futureAggregate.setCalibrationErrorPct(BigDecimal.valueOf(40));
        when(mapper.findExpectedReturnCalibrations(eq("SP500"), anyString()))
                .thenReturn(List.of(futureAggregate));
        List<StockExpectedReturnSnapshot> saved = new ArrayList<>();
        doAnswer(call -> { saved.add(call.getArgument(0)); return null; })
                .when(mapper).upsertExpectedReturnSnapshot(any());
        var method = StockPortfolioBacktestService.class.getMethod("refreshExpectedReturnV" + version + "Snapshots",
                String.class, int.class, int.class, int.class);
        var service = service(mapper);
        method.invoke(service, "SP500", 1000, 1, 50);
        futureAggregate.setCalibrationErrorPct(BigDecimal.valueOf(-40));
        method.invoke(service, "SP500", 1000, 1, 50);
        assertEquals(2, saved.size());
        assertEquals(saved.get(0).getCalibratedUpsideProbabilityPct(), saved.get(1).getCalibratedUpsideProbabilityPct());
    }

    private static List<StockExpectedReturnSnapshot> predict(int version, List<StockBacktestResult> rows)
            throws Exception {
        var mapper = mock(StockBacktestMapper.class);
        when(mapper.findResults(eq("SP500"), anyInt())).thenReturn(rows);
        when(mapper.findBenchmarkReturns(eq("SP500"), any(), any())).thenReturn(benchmark());
        List<StockExpectedReturnSnapshot> saved = new ArrayList<>();
        doAnswer(call -> { saved.add(call.getArgument(0)); return null; })
                .when(mapper).upsertExpectedReturnSnapshot(any());
        String method = version == 2 ? "refreshExpectedReturnSnapshots"
                : "refreshExpectedReturnV" + version + "Snapshots";
        StockPortfolioBacktestService.class.getMethod(method, String.class, int.class, int.class, int.class)
                .invoke(service(mapper), "SP500", 1000, 1, 50);
        return saved;
    }

    private static List<StockBacktestResult> trainingRows() {
        List<StockBacktestResult> rows = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            rows.add(row("M" + i, TARGET.minusDays(80), TARGET.minusDays(60), 4));
            rows.add(row("F" + i, TARGET.minusDays(10), TARGET.plusDays(10), 40));
        }
        rows.add(row("TARGET", TARGET, TARGET.plusDays(20), 4));
        return rows;
    }

    static StockBacktestResult row(String symbol, LocalDate entry, LocalDate exit, double ret) {
        var row = new StockBacktestResult();
        row.setSymbol(symbol); row.setSignalDate(entry); row.setEntryTradeDate(entry);
        row.setExitTradeDate(exit); row.setHorizonDays(20); row.setIntegratedScore(70);
        row.setForwardReturnPct(BigDecimal.valueOf(ret)); row.setEntryClose(BigDecimal.valueOf(100));
        row.setExitClose(BigDecimal.valueOf(100 + ret)); row.setSector("Tech");
        return row;
    }

    private static List<StockBenchmarkReturn> benchmark() {
        List<StockBenchmarkReturn> rows = new ArrayList<>();
        for (LocalDate start : List.of(TARGET.minusDays(80), TARGET.minusDays(10), TARGET)) {
            for (int i = 1; i <= 20; i++) {
                var row = new StockBenchmarkReturn(); row.setIndexCode("SP500");
                row.setTradeDate(start.plusDays(i)); row.setReturnPct(BigDecimal.valueOf(i == 20 ? 6 : 0));
                row.setSource("MARKET_SNAPSHOT_WEIGHTED"); rows.add(row);
            }
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    static StockPortfolioBacktestService service(StockBacktestMapper mapper) {
        ObjectProvider<StockBacktestMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        return new StockPortfolioBacktestService(provider, new ObjectMapper(), 0);
    }
}
