package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StockExpectedReturnNeighborhoodReuseTest {
    private static final LocalDate EARLY = LocalDate.of(2025, 6, 1);
    private static final LocalDate LATER = EARLY.plusDays(60);

    @Test
    void orderedReuseExactlyMatchesOriginalMathAcrossTiesFallbacksSectorsAndConfidence() throws Exception {
        Method stats = method("expectedReturnStats", StockBacktestResult.class, List.class);
        Method reuse = method("orderedNeighborhood", List.class, int.class, Map.class);
        Method observations = method("trainingObservations", List.class);
        for (int size : List.of(49, 180, 1800)) {
            List<?> raw = (List<?>) observations.invoke(null, trainingRows(size));
            Map<Integer, List<?>> local = new HashMap<>();
            for (Integer score : java.util.Arrays.asList(null, 0, 7, 20, 35, 49, 50, 51, 65, 80, 93, 100,
                    Integer.MIN_VALUE, Integer.MAX_VALUE)) {
                for (String sector : java.util.Arrays.asList(null, "Tech", "Health", "Absent")) {
                    for (Integer quality : java.util.Arrays.asList(null, 0, 50, 100)) {
                        var target = target(score, sector, quality);
                        Object original = stats.invoke(null, target, raw);
                        List<?> ordered = (List<?>) reuse.invoke(null, raw, score == null ? 50 : score, local);
                        assertEquals(original, stats.invoke(null, target, ordered),
                                "All stats, probabilities, confidence and bucket fields must match bit-for-bit");
                    }
                }
            }
            assertTrue(local.size() <= 14);
        }
    }

    @Test
    void reuseIsLocalToSuppliedTrainingInventoryAndPreservesStableTieOrder() throws Exception {
        Method observations = method("trainingObservations", List.class);
        Method reuse = method("orderedNeighborhood", List.class, int.class, Map.class);
        List<StockBacktestResult> rows = new ArrayList<>();
        rows.add(trainingRow("FIRST", 49, "Tech", 1));
        rows.add(trainingRow("SECOND", 51, "Tech", 2));
        rows.add(trainingRow("THIRD", 49, "Health", 3));
        List<?> raw = (List<?>) observations.invoke(null, rows);
        Map<Integer, List<?>> group = new HashMap<>();
        List<?> first = (List<?>) reuse.invoke(null, raw, 50, group);
        assertEquals(raw, first, "Equal distances retain original date/symbol encounter order");
        assertSame(first, reuse.invoke(null, raw, 50, group), "Repeated score must reuse its ordered inventory");
        List<StockBacktestResult> changed = new ArrayList<>(rows);
        changed.add(trainingRow("LATE", 50, "Tech", 900));
        List<?> newInventory = (List<?>) observations.invoke(null, changed);
        List<?> later = (List<?>) reuse.invoke(null, newInventory, 50, new HashMap<>());
        assertNotSame(first, later);
        assertEquals(4, later.size());
        assertSame(newInventory.getLast(), later.getFirst());
        assertEquals(3, first.size(), "Later inventory must not mutate or pollute the earlier cutoff");
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    void actualRefreshKeepsDateHorizonAndInvocationCutoffsIndependent(int version) throws Exception {
        List<StockBacktestResult> rows = refreshRows(40);
        Map<String, StockExpectedReturnSnapshot> before = refresh(version, rows);
        Map<String, StockExpectedReturnSnapshot> after = refresh(version, refreshRows(-40));
        assertEquals(4, before.size());
        for (int horizon : List.of(5, 20)) {
            var early = before.get(EARLY + ":" + horizon);
            var earlyAfter = after.get(EARLY + ":" + horizon);
            assertNotNull(early);
            assertEquals(early.getExpectedReturnPct(), earlyAfter.getExpectedReturnPct());
            assertEquals(early.getCalibratedUpsideProbabilityPct(), earlyAfter.getCalibratedUpsideProbabilityPct());
            assertEquals(early.getConfidence(), earlyAfter.getConfidence());
            assertEquals(50, early.getSampleCount());
            assertNotEquals(before.get(LATER + ":" + horizon).getExpectedReturnPct(),
                    after.get(LATER + ":" + horizon).getExpectedReturnPct(),
                    "Later-matured observations must affect only the later cutoff, not reuse an earlier cache");
            assertTrue(early.getSource().contains("CUTOFF_LT_20250601"));
            assertEquals("EXPECTED_RETURN_V" + version + "_CORRECTED_20261017", early.getModelVersion());
        }
        assertNotEquals(before.get(EARLY + ":5").getExpectedReturnPct(), before.get(EARLY + ":20").getExpectedReturnPct(),
                "The same score and sector at different horizons must use distinct training inventories");
    }

    @Test
    void boundedCpuComparisonIsAdvisoryAndStillProducesIdenticalStats() throws Exception {
        Method stats = method("expectedReturnStats", StockBacktestResult.class, List.class);
        Method reuse = method("orderedNeighborhood", List.class, int.class, Map.class);
        List<?> observations = (List<?>) method("trainingObservations", List.class).invoke(null, trainingRows(15000));
        List<StockBacktestResult> targets = new ArrayList<>();
        for (int i = 0; i < 240; i++) targets.add(target(30 + i % 24, i % 2 == 0 ? "Tech" : "Health", 50 + i % 40));
        for (int i = 0; i < 8; i++) stats.invoke(null, targets.get(i), observations);
        List<Object> expected = new ArrayList<>();
        long start = System.nanoTime();
        for (var target : targets) expected.add(stats.invoke(null, target, observations));
        double originalMs = (System.nanoTime() - start) / 1_000_000.0;
        Map<Integer, List<?>> local = new HashMap<>();
        start = System.nanoTime();
        for (int i = 0; i < targets.size(); i++) {
            var target = targets.get(i);
            List<?> ordered = (List<?>) reuse.invoke(null, observations, target.getIntegratedScore(), local);
            assertEquals(expected.get(i), stats.invoke(null, target, ordered));
        }
        double reusedMs = (System.nanoTime() - start) / 1_000_000.0;
        System.out.printf("Neighborhood CPU comparison: 15000 observations, 240 targets, original=%.2f ms, local-reuse=%.2f ms, score-inventories=%d; advisory only%n",
                originalMs, reusedMs, local.size());
    }

    private static Method method(String name, Class<?>... parameters) {
        try {
            Method method = StockPortfolioBacktestService.class.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Missing production neighborhood behavior: " + name, ex);
        }
    }

    private static StockBacktestResult target(Integer score, String sector, Integer quality) {
        var row = new StockBacktestResult();
        row.setIntegratedScore(score); row.setSector(sector); row.setDataQualityScore(quality);
        return row;
    }

    private static List<StockBacktestResult> trainingRows(int size) {
        List<StockBacktestResult> rows = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int score = size == 180 ? (i % 6) * 20 : (i * 37) % 101;
            rows.add(trainingRow("T" + i, score, i % 3 == 0 ? "Tech" : "Health", (i % 73 - 31) * 0.37));
        }
        return rows;
    }

    private static StockBacktestResult trainingRow(String symbol, int score, String sector, double value) {
        var row = new StockBacktestResult();
        row.setSymbol(symbol); row.setIntegratedScore(score); row.setSector(sector);
        row.setForwardReturnPct(BigDecimal.valueOf(value)); row.setBenchmarkReturnPct(BigDecimal.valueOf(1.25));
        return row;
    }

    private static List<StockBacktestResult> refreshRows(double lateReturn) {
        List<StockBacktestResult> rows = new ArrayList<>();
        for (int horizon : List.of(5, 20)) {
            for (int i = 0; i < 50; i++) {
                rows.add(actualRow("BASE" + i, EARLY.minusDays(80), horizon, horizon == 5 ? 2 : 8));
                rows.add(actualRow("LATE" + i, EARLY.minusDays(2), horizon, lateReturn));
            }
            rows.add(actualRow("TARGET", EARLY, horizon, 3));
            rows.add(actualRow("TARGET", LATER, horizon, 3));
        }
        return rows;
    }

    private static StockBacktestResult actualRow(String symbol, LocalDate date, int horizon, double value) {
        var row = trainingRow(symbol, 70, "Tech", value);
        row.setSignalDate(date); row.setEntryTradeDate(date.plusDays(1));
        row.setExitTradeDate(date.plusDays(horizon + 1)); row.setHorizonDays(horizon);
        row.setEntryClose(BigDecimal.valueOf(100)); row.setExitClose(BigDecimal.valueOf(100 + value));
        row.setDataQualityScore(80);
        return row;
    }

    private static Map<String, StockExpectedReturnSnapshot> refresh(int version, List<StockBacktestResult> rows) throws Exception {
        var mapper = mock(StockBacktestMapper.class);
        when(mapper.findResults(eq("SP500"), anyInt())).thenReturn(rows);
        List<StockBenchmarkReturn> benchmark = new ArrayList<>();
        for (LocalDate date = EARLY.minusDays(100); !date.isAfter(LATER.plusDays(30)); date = date.plusDays(1)) {
            var item = new StockBenchmarkReturn();
            item.setIndexCode("SP500"); item.setTradeDate(date); item.setReturnPct(BigDecimal.ZERO);
            item.setSource("FIXTURE_COMPLETE_PRICE_RETURN"); benchmark.add(item);
        }
        when(mapper.findBenchmarkReturns(eq("SP500"), any(), any())).thenReturn(benchmark);
        Map<String, StockExpectedReturnSnapshot> saved = new LinkedHashMap<>();
        doAnswer(call -> { StockExpectedReturnSnapshot item = call.getArgument(0);
            saved.put(item.getSignalDate() + ":" + item.getHorizonDays(), item); return null;
        }).when(mapper).upsertExpectedReturnSnapshot(any());
        String name = version == 2 ? "refreshExpectedReturnSnapshots" : "refreshExpectedReturnV" + version + "Snapshots";
        StockPortfolioBacktestService.class.getMethod(name, String.class, int.class, int.class, int.class)
                .invoke(StockQuantModelCorrectnessTest.service(mapper), "SP500", 1000, 2, 50);
        return saved;
    }
}
