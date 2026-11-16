package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

class YahooChartCandleBatchRunnerTest {

    @Test
    void collectionOnlyModeDoesNotRecalculateSignals() throws Exception {
        runCase(false, new YahooChartCandleService.SyncSummary(2, 2, 0, 20, List.of()), false, 0);
    }

    @Test
    void failuresSkipSignalsAndRecordUnattemptedSymbols() throws Exception {
        runCase(true, new YahooChartCandleService.SyncSummary(1, 0, 1, 0, List.of("AAPL")), false, 1);
    }

    @Test
    void successfulDefaultModeStillRecalculatesSignals() throws Exception {
        runCase(true, new YahooChartCandleService.SyncSummary(2, 2, 0, 20, List.of()), true, 0);
    }

    private void runCase(boolean refresh, YahooChartCandleService.SyncSummary summary,
            boolean expectRefresh, int expectedSkipped) throws Exception {
        var candles = mock(YahooChartCandleService.class);
        var signals = mock(StockSignalRefreshService.class);
        var runs = mock(YahooCandleBatchRunMapper.class);
        when(candles.syncSymbols(anyList(), eq(1), eq(0L))).thenReturn(summary);
        when(signals.recalculateSymbols(anyList())).thenReturn(new StockSignalRefreshService.RefreshResult(2, 2, 0));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("mariadb");
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                    "app.batch.yahoo-candle.enabled=true", "app.batch.yahoo-candle.exit-on-complete=false",
                    "app.batch.yahoo-candle.years=1", "app.batch.yahoo-candle.delay-millis=0",
                    "app.batch.yahoo-candle.symbols=AAPL,MSFT", "app.batch.yahoo-candle.refresh-signals=" + refresh);
            context.registerBean(YahooChartCandleService.class, () -> candles);
            context.registerBean(StockSignalRefreshService.class, () -> signals);
            context.registerBean(YahooCandleBatchRunMapper.class, () -> runs);
            context.registerBean(YahooChartCandleBatchRunner.class);
            context.refresh();
            context.getBean(YahooChartCandleBatchRunner.class).run(null);
        }
        var completed = ArgumentCaptor.forClass(YahooCandleBatchRun.class);
        verify(runs).finish(completed.capture());
        assertEquals(expectedSkipped, completed.getValue().getSkippedCount());
        assertEquals(expectRefresh ? 2 : 0, completed.getValue().getSignalRefreshedCount());
        if (expectRefresh) {
            verify(signals).recalculateSymbols(List.of("AAPL", "MSFT"));
        } else {
            verifyNoInteractions(signals);
            assertTrue(completed.getValue().getMessage().contains("Signal refresh skipped"));
        }
    }
}
