package com.kingyurina.demo.stock;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class StockCacheServiceCandleValidationTest {

    @Test
    void preservesSourceRowsButOnlyPromotesValidCanonicalCandles() {
        StockCandleMapper mapper = mock(StockCandleMapper.class);
        StockCacheService service = service(mapper);
        StockCandleDaily good = candle("yahoo-chart", "10", "12", "9", "11", 0L);
        StockCandleDaily stooq = candle("stooq", "10", "12", "9", "11", null);
        List<StockCandleDaily> rows = List.of(good, stooq,
                candle("yahoo-chart", "0", "12", "9", "11", 10L),
                candle("yahoo-chart", "10", "9", "8", "11", 10L),
                candle("yahoo-chart", "10", "12", "11", "11", 10L),
                candle("yahoo-chart", "10", "12", "9", "-1", 10L),
                candle("yahoo-chart", "10", "12", "9", "11", -1L),
                candle("yahoo-chart", null, "12", "9", "11", 10L),
                candle("TOSS", "10", "12", "9", "11", 10L));

        service.saveCandles(rows);

        verify(mapper).upsertSourceBatch(rows.stream().filter(c -> c.getOpenPrice() != null).toList());
        verify(mapper).upsertBatch(List.of(good)); // One symbol must not mix provider adjustment bases.
    }

    @Test
    void allInvalidRowsRemainSourceOnly() {
        StockCandleMapper mapper = mock(StockCandleMapper.class);
        List<StockCandleDaily> rows = List.of(candle("yahoo-chart", "0", "12", "9", "11", 10L));

        service(mapper).saveCandles(rows);

        verify(mapper).upsertSourceBatch(rows);
        verify(mapper, never()).upsertBatch(org.mockito.ArgumentMatchers.anyList());
    }

    private static StockCacheService service(StockCandleMapper mapper) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("candles", mapper);
        return new StockCacheService(null, null, null, null, null, null,
                factory.getBeanProvider(StockCandleMapper.class), null, null, null, null, null, null);
    }

    private static StockCandleDaily candle(String source, String open, String high, String low,
            String close, Long volume) {
        StockCandleDaily row = new StockCandleDaily();
        row.setSymbol("TEST");
        row.setTradeDate(LocalDate.of(2026, 10, 5));
        row.setSource(source);
        row.setOpenPrice(open == null ? null : new BigDecimal(open));
        row.setHighPrice(new BigDecimal(high));
        row.setLowPrice(new BigDecimal(low));
        row.setClosePrice(new BigDecimal(close));
        row.setVolume(volume);
        return row;
    }
}
