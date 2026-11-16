package com.kingyurina.demo.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;
import com.kingyurina.demo.stock.evaluation.ExpectedReturnEvaluationSummaryService;
import com.kingyurina.demo.stock.evaluation.ExpectedReturnEvaluationSummaryView;

class QuantReadIntegrityTest {
    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    @Test
    void staleMaterializedDashboardDoesNotMaskNewDbPricesAndGetNeverPersists() {
        StockBacktestMapper mapper = mock(StockBacktestMapper.class);
        ObjectMapper json = new ObjectMapper();
        StockMarketView oldMarket = market("STALE");
        StockMarketView newMarket = market("CURRENT");
        StockDashboardViewSnapshot snapshot = new StockDashboardViewSnapshot();
        snapshot.setGeneratedAt(LocalDateTime.now().minusDays(7));
        snapshot.setPayloadJson(json.writeValueAsString(payload(oldMarket)));
        when(mapper.findLatestDashboardViewSnapshot(anyString(), anyString())).thenReturn(snapshot);
        StockMarketViewService marketService = mock(StockMarketViewService.class);
        when(marketService.build("SP500")).thenReturn(newMarket);
        StockDashboardViewSnapshotService service = new StockDashboardViewSnapshotService(provider(mapper), json,
                marketService, mock(StockMacroRegimeService.class), mock(StockQuantModelHealthService.class),
                mock(StockApiDataSourceService.class), mock(ExpectedReturnEvaluationSummaryService.class));
        assertThat(service.build("SP500").market().title()).isEqualTo("CURRENT");
        verify(mapper, never()).upsertDashboardViewSnapshot(any());
    }

    @Test
    void missingBacktestSnapshotDoesNotRunResearchOrWriteOnGet() {
        StockBacktestMapper mapper = mock(StockBacktestMapper.class);
        StockBacktestService service = new StockBacktestService(provider(mapper), provider(null), new ObjectMapper());
        assertThat(service.build("SP500").result().hasResults()).isFalse();
        verify(mapper, never()).findResults(anyString(), anyInt());
        verify(mapper, never()).upsertBacktestViewSnapshot(any());
    }

    @Test
    void signalAndSuccessfulBatchFromLastMonthAreNotHealthyToday() {
        StockBacktestMapper mapper = mock(StockBacktestMapper.class);
        StockQuantModelHealthMetric metric = new StockQuantModelHealthMetric();
        metric.setMetricKey("SIGNAL_LATEST");
        metric.setLayer("Signal");
        metric.setRowCount(500L);
        metric.setLatestCalculatedAt(LocalDateTime.now().minusDays(30));
        StockQuantOperationHealthMetric operation = new StockQuantOperationHealthMetric();
        operation.setOperation("prices");
        operation.setStatus("SUCCESS");
        operation.setSuccessCount(500L);
        operation.setFinishedAt(LocalDateTime.now().minusDays(30));
        when(mapper.findQuantModelHealth("SP500")).thenReturn(List.of(metric));
        when(mapper.findQuantOperationHealth()).thenReturn(List.of(operation));
        StockQuantModelHealthView view = new StockQuantModelHealthService(provider(mapper)).build("SP500");
        assertThat(view.rows().getFirst().tone()).isEqualTo("warning");
        assertThat(view.operations().getFirst().tone()).isEqualTo("warning");
    }

    @Test
    void successWithPartialFailuresDoesNotBecomeHealthy() {
        StockBacktestMapper mapper = mock(StockBacktestMapper.class);
        StockQuantModelHealthMetric metric = new StockQuantModelHealthMetric();
        metric.setMetricKey("UNIVERSE"); metric.setRowCount(500L);
        StockQuantOperationHealthMetric operation = new StockQuantOperationHealthMetric();
        operation.setStatus("SUCCESS"); operation.setSuccessCount(499L); operation.setFailCount(1L);
        operation.setFinishedAt(LocalDateTime.now());
        when(mapper.findQuantModelHealth("SP500")).thenReturn(List.of(metric));
        when(mapper.findQuantOperationHealth()).thenReturn(List.of(operation));
        assertThat(new StockQuantModelHealthService(provider(mapper)).build("SP500")
                .operations().getFirst().tone()).isEqualTo("warning");
    }

    @Test
    void sourcePanelDoesNotLabelAnOldSuccessfulCollectionFresh() {
        StockBacktestMapper mapper = mock(StockBacktestMapper.class);
        StockQuantOperationHealthMetric operation = new StockQuantOperationHealthMetric();
        operation.setOperation("Yahoo candle batch"); operation.setStatus("SUCCESS");
        operation.setSuccessCount(500L); operation.setFinishedAt(LocalDateTime.now().minusDays(30));
        when(mapper.findQuantOperationHealth()).thenReturn(List.of(operation));
        StockApiDataSourceView source = new StockApiDataSourceService(provider(mapper)).build("SP500")
                .stream().filter(row -> "YAHOO".equals(row.provider())).findFirst().orElseThrow();
        assertThat(source.tone()).isEqualTo("warning");
        assertThat(source.status()).isNotEqualTo("Fresh");
    }

    @Test
    void downstreamModelFailureDoesNotPreventDashboardPublishing() {
        StockBacktestService backtest = mock(StockBacktestService.class);
        when(backtest.refreshCompletedResults(anyInt())).thenThrow(new IllegalStateException("fixture model failure"));
        StockDashboardViewSnapshotService dashboard = mock(StockDashboardViewSnapshotService.class);
        StockSignalRefreshService service = new StockSignalRefreshService(provider(null), provider(null),
                mock(StockSignalService.class), mock(StockCacheService.class), backtest,
                mock(StockPortfolioBacktestService.class), mock(StockMacroFeatureService.class),
                mock(StockExpectedReturnV8Service.class), mock(StockExpectedReturnV9Service.class), dashboard);
        var result = service.recalculateSymbols(List.of());
        verify(dashboard).refreshDashboardViewSnapshot("SP500");
        verify(dashboard).refreshDashboardViewSnapshot("NASDAQ100");
        verify(dashboard).refreshDashboardViewSnapshot("DOW30");
        assertThat(new ObjectMapper().writeValueAsString(result)).contains("completed-results");
    }

    @Test
    void marketPriceUsesUsdWithoutAnUndatedExchangeRate() {
        IndexConstituentMapper mapper = mock(IndexConstituentMapper.class);
        StockMarketRow row = new StockMarketRow();
        row.setSymbol("APH"); row.setCurrentPrice(new BigDecimal("81.489998"));
        row.setMarketCap(new BigDecimal("1000"));
        when(mapper.findMarketRows("SP500")).thenReturn(List.of(row));
        StockMarketView view = new StockMarketViewService(provider(mapper)).build("SP500");
        assertThat(view.rows().getFirst().price()).isEqualTo("$81.49");
        assertThat(view.rows().getFirst().marketCap()).isEqualTo("$1.0B");
        assertThat(view.averageChange()).isEqualTo("-");
    }

    @Test
    void unsuccessfulPublicationIsReportedAndDoesNotStopOtherIndexes() {
        StockBacktestService backtest = mock(StockBacktestService.class);
        StockPortfolioBacktestService portfolio = mock(StockPortfolioBacktestService.class);
        StockDashboardViewSnapshotService dashboard = mock(StockDashboardViewSnapshotService.class);
        when(backtest.refreshBacktestViewSnapshot("SP500")).thenReturn(
                new StockBacktestService.BacktestViewSnapshotRefreshResult("SP500", false, "write failed", 0));
        when(portfolio.refreshPortfolioViewSnapshot("SP500")).thenReturn(
                new StockPortfolioBacktestService.PortfolioViewSnapshotRefreshResult("SP500", false, "write failed", 0));
        when(dashboard.refreshDashboardViewSnapshot("SP500")).thenReturn(
                new StockDashboardViewSnapshotService.DashboardViewSnapshotRefreshResult("SP500", false, "write failed", 0));
        when(dashboard.refreshDashboardViewSnapshot("NASDAQ100")).thenReturn(
                new StockDashboardViewSnapshotService.DashboardViewSnapshotRefreshResult("NASDAQ100", true, "saved", 0));
        when(dashboard.refreshDashboardViewSnapshot("DOW30")).thenReturn(
                new StockDashboardViewSnapshotService.DashboardViewSnapshotRefreshResult("DOW30", true, "saved", 0));
        var service = new StockSignalRefreshService(provider(null), provider(null),
                mock(StockSignalService.class), mock(StockCacheService.class), backtest, portfolio,
                mock(StockMacroFeatureService.class), mock(StockExpectedReturnV8Service.class),
                mock(StockExpectedReturnV9Service.class), dashboard);
        assertThat(service.recalculateSymbols(List.of()).failedStages())
                .containsExactly("backtest-view", "portfolio-view", "dashboard:SP500");
        verify(dashboard).refreshDashboardViewSnapshot("NASDAQ100");
        verify(dashboard).refreshDashboardViewSnapshot("DOW30");
    }

    @Test
    void opinionDoesNotFallbackToKnownInvalidLegacyModelVersions() {
        StockBacktestMapper mapper = mock(StockBacktestMapper.class);
        StockCacheService cache = mock(StockCacheService.class);
        StockSignalLatest signal = new StockSignalLatest(); signal.setIntegratedScore(60);
        signal.setSignalVersion("q4-fin-20261031");
        when(cache.findLatestSignal("APH")).thenReturn(signal);
        StockExpectedReturnSnapshot legacy = new StockExpectedReturnSnapshot();
        legacy.setSymbol("APH"); legacy.setSignalDate(LocalDate.now()); legacy.setHorizonDays(20);
        legacy.setModelVersion("EXPECTED_RETURN_V9"); legacy.setExpectedExcessReturnPct(new BigDecimal("99"));
        when(mapper.findLatestExpectedReturnSnapshotsBySymbol("APH", "EXPECTED_RETURN_V9"))
                .thenReturn(List.of(legacy));
        var view = new StockQuantOpinionService(cache, provider(mapper)).build("APH");
        assertThat(view.modelVersion()).endsWith("_CORRECTED_20261017");
        assertThat(view.horizonOpinions()).isEmpty();
    }

    @Test
    void marketResponseIncludesTheActualPriceDateAndSource() {
        IndexConstituentMapper mapper = mock(IndexConstituentMapper.class);
        StockMarketRow row = new StockMarketRow(); row.setSymbol("APH");
        row.setCurrentPrice(new BigDecimal("81.49")); row.setPriceKind("CLOSE");
        row.setPriceAsOf(LocalDate.of(2026, 10, 15).atTime(16, 0)); row.setPriceSource("yahoo-chart");
        when(mapper.findMarketRows("SP500")).thenReturn(List.of(row));
        String json = new ObjectMapper().writeValueAsString(new StockMarketViewService(provider(mapper)).build("SP500"));
        assertThat(json).contains("2026-10-15", "yahoo-chart");
    }

    @Test
    void opinionDoesNotUseLegacySignalAsCorrectedEvidence() {
        StockCacheService cache = mock(StockCacheService.class);
        StockSignalLatest legacy = new StockSignalLatest();
        legacy.setSignalVersion("quant-v2"); legacy.setIntegratedScore(99);
        when(cache.findLatestSignal("APH")).thenReturn(legacy);
        assertThat(new StockQuantOpinionService(cache, provider(null)).build("APH")).isNull();
    }

    @Test
    void twentyDayOutcomeHasTwentyActualCloseToCloseIntervals() {
        StockBacktestMapper mapper = mock(StockBacktestMapper.class);
        StockSignalSnapshot signal = new StockSignalSnapshot();
        signal.setId(1L); signal.setSymbol("APH"); signal.setSignalDate(LocalDate.of(2026, 1, 2));
        when(mapper.findSnapshotsNeedingResults(1)).thenReturn(List.of(signal));
        var candles = new java.util.ArrayList<StockCandleDaily>();
        LocalDate date = signal.getSignalDate();
        for (int i = 0; i < 21; i++) {
            do { date = date.plusDays(1); } while (date.getDayOfWeek().getValue() > 5);
            StockCandleDaily candle = new StockCandleDaily();
            candle.setTradeDate(date); candle.setClosePrice(BigDecimal.valueOf(100 + i));
            candles.add(candle);
        }
        when(mapper.findFutureCandles(eq("APH"), eq(signal.getSignalDate()), anyInt()))
                .thenAnswer(call -> candles.stream().limit((Integer) call.getArgument(2)).toList());
        new StockBacktestService(provider(mapper), provider(null), new ObjectMapper()).refreshCompletedResults(1);
        var saved = org.mockito.ArgumentCaptor.forClass(StockBacktestResult.class);
        verify(mapper, atLeastOnce()).upsertResult(saved.capture());
        StockBacktestResult twenty = saved.getAllValues().stream()
                .filter(row -> row.getHorizonDays() == 20).findFirst().orElseThrow();
        assertThat(twenty.getExitTradeDate()).isEqualTo(candles.get(20).getTradeDate());
        assertThat(twenty.getForwardReturnPct()).isEqualByComparingTo("20");
    }

    private static StockMarketView market(String title) {
        return new StockMarketView(title, "", "0", "0", "0", "0", "-", "-",
                List.of(), List.of(), List.of(), List.of());
    }

    private static StockDashboardViewPayload payload(StockMarketView market) {
        return new StockDashboardViewPayload("SP500", market,
                new StockMacroRegimeView("", "", "", "", "", List.of()),
                new StockQuantModelHealthView("", "", List.of(), List.of(
                        new StockQuantModelHealthView.Row("Backtest view snapshot", "", "", "", "", "", "")),
                        List.of(), List.of()), List.of(),
                new ExpectedReturnEvaluationSummaryView("", "", "", "", "", "", "", "", "", "", "", ""));
    }
}
