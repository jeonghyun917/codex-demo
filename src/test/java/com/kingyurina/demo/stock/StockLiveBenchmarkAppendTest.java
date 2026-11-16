package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import tools.jackson.databind.ObjectMapper;

class StockLiveBenchmarkAppendTest {
    private static final LocalDate ANCHOR = LocalDate.of(2026, 9, 21);

    @Test void appendsCompoundedLevelsWithoutOverwritingStoredHistory() {
        var fixture = new Fixture();
        var older = benchmark(ANCHOR.minusDays(3), "9000");
        fixture.history.addFirst(older);
        var result = fixture.refresh();
        assertEquals(2, result.savedBenchmarkRows());
        assertEquals(List.of(ANCHOR.plusDays(1), ANCHOR.plusDays(2)),
                fixture.saved.stream().map(StockBenchmarkReturn::getTradeDate).toList());
        assertEquals(10100, fixture.saved.get(0).getBenchmarkLevel().doubleValue(), 0.000001);
        assertEquals(10302, fixture.saved.get(1).getBenchmarkLevel().doubleValue(), 0.000001);
        assertEquals(1, fixture.saved.get(0).getReturnPct().doubleValue(), 0.000001);
        assertEquals(2, fixture.saved.get(1).getReturnPct().doubleValue(), 0.000001);
        assertEquals(new BigDecimal("10000"), fixture.history.getLast().getBenchmarkLevel());
        assertEquals("MARKET_SNAPSHOT_WEIGHTED", fixture.saved.getFirst().getSource());
    }

    @Test void rejectsAnUncoveredSessionBeforeSavingAnyBenchmarkRows() {
        var fixture = new Fixture();
        fixture.snapshots.removeIf(row -> row.getSnapshotDate().equals(ANCHOR.plusDays(2))
                && !row.getSymbol().equals("S0"));
        assertThrows(IllegalStateException.class, fixture::refresh);
        assertTrue(fixture.saved.isEmpty());
    }

    @Test void rejectsMissingAnchorSnapshotsRatherThanSkippingTheFirstForwardReturn() {
        var fixture = new Fixture();
        fixture.snapshots.removeIf(row -> row.getSnapshotDate().equals(ANCHOR));
        assertThrows(IllegalStateException.class, fixture::refresh);
        assertTrue(fixture.saved.isEmpty());
    }

    @Test void rejectsMissingStoredAnchorWithoutInventingABaseline() {
        var fixture = new Fixture();
        fixture.history.clear();
        assertThrows(IllegalStateException.class, fixture::refresh);
        assertEquals(0, fixture.generatedRanges);
        assertTrue(fixture.saved.isEmpty());
    }

    @Test void rejectsNonfiniteAnchorBeforeGeneratingSnapshots() {
        var fixture = new Fixture();
        fixture.history.getFirst().setBenchmarkLevel(new BigDecimal("1E1000"));
        assertThrows(IllegalStateException.class, fixture::refresh);
        assertEquals(0, fixture.generatedRanges);
        assertTrue(fixture.saved.isEmpty());
    }

    @Test void rejectsAnAnchorFromADifferentMethodBeforeGeneratingSnapshots() {
        var fixture = new Fixture();
        fixture.history.getFirst().setSource("OFFICIAL_INDEX_PRICE");
        assertThrows(IllegalStateException.class, fixture::refresh);
        assertEquals(0, fixture.generatedRanges);
        assertTrue(fixture.saved.isEmpty());
    }

    @Test void rejectsNonfiniteSourceValuesBeforeSavingAnyBenchmarkRows() {
        var fixture = new Fixture();
        fixture.snapshots.getLast().setClosePrice(new BigDecimal("1E1000"));
        assertThrows(IllegalStateException.class, fixture::refresh);
        assertTrue(fixture.saved.isEmpty());
    }

    @Test void rejectsFutureSnapshotsBeforeSavingAnyBenchmarkRows() {
        var fixture = new Fixture();
        fixture.snapshots.getLast().setSnapshotDate(ANCHOR.plusDays(3));
        assertThrows(IllegalStateException.class, fixture::refresh);
        assertTrue(fixture.saved.isEmpty());
    }

    @Test void alreadyCurrentAnchorIsAnIdempotentNoOp() {
        var fixture = new Fixture();
        fixture.history.add(benchmark(ANCHOR.plusDays(2), "10302"));
        assertEquals(0, fixture.refresh().savedBenchmarkRows());
        assertEquals(0, fixture.generatedRanges);
        assertTrue(fixture.saved.isEmpty());
    }

    @Test void rejectsUnorderedStoredHistoryBeforeAnySnapshotWrites() {
        var fixture = new Fixture();
        fixture.history.add(benchmark(ANCHOR.minusDays(1), "9000"));
        assertThrows(IllegalStateException.class, fixture::refresh);
        assertEquals(0, fixture.generatedRanges);
        assertTrue(fixture.saved.isEmpty());
    }

    @Test void rejectsDuplicateSymbolSessionsBeforeSavingAnyBenchmarkRows() {
        var fixture = new Fixture();
        fixture.snapshots.add(fixture.snapshots.getFirst());
        assertThrows(IllegalStateException.class, fixture::refresh);
        assertTrue(fixture.saved.isEmpty());
    }

    private static StockBenchmarkReturn benchmark(LocalDate date, String level) {
        var row = new StockBenchmarkReturn();
        row.setIndexCode("SP500"); row.setTradeDate(date); row.setBenchmarkLevel(new BigDecimal(level));
        row.setSource("MARKET_SNAPSHOT_WEIGHTED");
        return row;
    }

    private static final class Fixture {
        final List<StockBenchmarkReturn> history = new ArrayList<>(List.of(benchmark(ANCHOR, "10000")));
        final List<StockMarketSnapshot> snapshots = new ArrayList<>();
        final List<StockBenchmarkReturn> saved = new ArrayList<>();
        final StockPortfolioBacktestService service;
        int generatedRanges;

        Fixture() {
            for (int i = 0; i < 30; i++) for (int day = 0; day < 3; day++) {
                var row = new StockMarketSnapshot(); row.setIndexCode("SP500"); row.setSymbol("S" + i);
                row.setSnapshotDate(ANCHOR.plusDays(day)); row.setCurrency("USD");
                row.setClosePrice(new BigDecimal(List.of("100", "101", "103.02").get(day)));
                row.setMarketCapUsd(new BigDecimal("1000000")); snapshots.add(row);
            }
            var mapper = (StockBacktestMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{StockBacktestMapper.class}, (proxy, method, args) -> {
                        if (method.getName().equals("findBenchmarkReturns")) return history;
                        if (method.getName().equals("upsertBenchmarkReturn")) {
                            var row = (StockBenchmarkReturn) args[0];
                            assertTrue(row.getTradeDate().isAfter(history.getLast().getTradeDate()),
                                    "Existing benchmark history must never be upserted");
                            saved.add(row); return null;
                        }
                        assertEquals(List.of("SP500", ANCHOR, ANCHOR.plusDays(2)), List.of(args));
                        return switch (method.getName()) {
                            case "findMarketSnapshots" -> snapshots;
                            case "refreshIndexMembershipSnapshots", "deactivateStaleCurrentMembershipSnapshots",
                                    "deleteStaleMarketSnapshots", "refreshSecSharesOutstandingSnapshots",
                                    "refreshSharesOutstandingSnapshots" -> 0;
                            case "refreshMarketSnapshots" -> { generatedRanges++; yield snapshots.size(); }
                            default -> throw new AssertionError("Unexpected operation: " + method.getName());
                        };
                    });
            var beans = new StaticListableBeanFactory(); beans.addBean("mapper", mapper);
            service = new StockPortfolioBacktestService(beans.getBeanProvider(StockBacktestMapper.class), new ObjectMapper(), 0);
        }

        StockPortfolioBacktestService.MarketSnapshotRefreshResult refresh() {
            return service.refreshLatestMarketSnapshotsAndBenchmarks("SP500", ANCHOR.plusDays(2));
        }
    }
}
