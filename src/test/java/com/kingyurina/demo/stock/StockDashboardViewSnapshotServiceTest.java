package com.kingyurina.demo.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.kingyurina.demo.stock.evaluation.ExpectedReturnEvaluationSummaryService;

import tools.jackson.databind.ObjectMapper;

class StockDashboardViewSnapshotServiceTest {

    @Test
    void buildInvalidatesPreRatioV3SnapshotAndRebuildsDashboard() {
        StockBacktestMapper mapper = mock(StockBacktestMapper.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<StockBacktestMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);

        StockMarketViewService marketViewService = mock(StockMarketViewService.class);
        StockDashboardViewSnapshotService service = new StockDashboardViewSnapshotService(
                provider,
                new ObjectMapper(),
                marketViewService,
                mock(StockMacroRegimeService.class),
                mock(StockQuantModelHealthService.class),
                mock(StockApiDataSourceService.class),
                mock(ExpectedReturnEvaluationSummaryService.class));

        StockDashboardViewPayload payload = service.build("SP500");

        assertThat(payload.activeIndex()).isEqualTo("SP500");
        verify(mapper).findLatestDashboardViewSnapshot("SP500", "DASHBOARD_QUANT_AI_EVALUATION_V4");
        verify(mapper, never()).findLatestDashboardViewSnapshot("SP500", "DASHBOARD_QUANT_AI_EVALUATION_V3");
        verify(marketViewService).build("SP500");
    }
}
