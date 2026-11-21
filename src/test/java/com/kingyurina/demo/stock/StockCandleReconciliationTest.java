package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StockCandleReconciliationTest {
    static StockCandleDaily candle(String symbol, String date, String price, long volume) {
        var row = new StockCandleDaily(); row.setSymbol(symbol); row.setTradeDate(LocalDate.parse(date));
        var p = new BigDecimal(price); row.setOpenPrice(p); row.setHighPrice(p); row.setLowPrice(p);
        row.setClosePrice(p); row.setVolume(volume); row.setSource("yahoo-chart"); return row;
    }
    @Test void fixesWholeTuplesOnOldDatesWithoutScalingAlreadyCorrectLaterDates() {
        var old = List.of(candle("APH", "2021-06-07", "40", 100), candle("APH", "2026-10-15", "81.49", 200));
        var fresh = List.of(candle("APH", "2021-06-07", "20", 200), candle("APH", "2026-10-15", "81.49", 200));
        var plan = StockCandleReconciliation.plan("APH", old, fresh);
        assertEquals(2, plan.comparedRows()); assertEquals(1, plan.changes().size());
        assertEquals(new BigDecimal("20.000000"), plan.changes().getFirst().after().getOpenPrice());
        assertEquals(200L, plan.changes().getFirst().after().getVolume());
        assertEquals(new BigDecimal("40"), old.getFirst().getClosePrice(), "planning must not mutate stored inputs");
    }
    @Test void repeatOnReconciledValuesIsANoOpAtDatabasePrecision() {
        var stored = List.of(candle("APH", "2026-10-15", "81.490000", 200));
        var fresh = List.of(candle("APH", "2026-10-15", "81.4900001", 200));
        assertEquals(0, StockCandleReconciliation.plan("APH", stored, fresh).changes().size());
    }
    @Test void rejectsPartialSnapshotInsteadOfMixingAdjustmentBases() {
        var old = List.of(candle("APH", "2021-06-07", "40", 100), candle("APH", "2026-10-15", "81.49", 200));
        assertThrows(IllegalArgumentException.class, () -> StockCandleReconciliation.plan("APH", old,
                List.of(candle("APH", "2026-10-15", "81.49", 200))));
    }
    @Test void doesNotAppendNewDatesDuringHistoricalRepair() {
        var old = List.of(candle("APH", "2026-10-15", "81.49", 200));
        var fresh = List.of(candle("APH", "2026-10-15", "81.49", 200), candle("APH", "2026-10-16", "82", 300));
        var plan = StockCandleReconciliation.plan("APH", old, fresh);
        assertEquals(1, plan.comparedRows()); assertTrue(plan.changes().isEmpty());
    }
    @Test void rejectsDuplicateDatesWrongSymbolsAndInvalidOhlc() {
        var old = List.of(candle("APH", "2026-10-15", "81.49", 200));
        assertThrows(IllegalArgumentException.class, () -> StockCandleReconciliation.plan("APH", old,
                List.of(old.getFirst(), old.getFirst())));
        assertThrows(IllegalArgumentException.class, () -> StockCandleReconciliation.plan("APH", old,
                List.of(candle("HON", "2026-10-15", "81.49", 200))));
        var invalid = candle("APH", "2026-10-15", "81.49", 200); invalid.setLowPrice(new BigDecimal("90"));
        assertThrows(IllegalArgumentException.class, () -> StockCandleReconciliation.plan("APH", old, List.of(invalid)));
    }
}
