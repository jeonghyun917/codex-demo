package com.kingyurina.demo.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

class PaperLedgerViewServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void readsDedicatedDbSnapshotWithoutInventingPerformance() {
        ObjectProvider<StockBacktestMapper> provider = mock(ObjectProvider.class);
        StockBacktestMapper mapper = mock(StockBacktestMapper.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        var service = new PaperLedgerViewService(provider, new ObjectMapper());
        assertThat(service.read().get("available")).isEqualTo(false);
        var snapshot = new StockDashboardViewSnapshot();
        snapshot.setPayloadJson("{\"version\":\"SP500_PAPER_WEB_V1\",\"days\":[],\"accounts\":[],\"capturedDays\":0}");
        when(mapper.findLatestDashboardViewSnapshot("SP500", "SP500_PAPER_WEB_V1")).thenReturn(snapshot);
        assertThat(service.read().get("capturedDays")).isEqualTo(0);
        assertThat(service.read().get("available")).isEqualTo(true);
        verify(mapper, never()).upsertDashboardViewSnapshot(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsCorruptSnapshot() {
        ObjectProvider<StockBacktestMapper> provider = mock(ObjectProvider.class);
        StockBacktestMapper mapper = mock(StockBacktestMapper.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        var snapshot = new StockDashboardViewSnapshot();
        snapshot.setPayloadJson("not json");
        when(mapper.findLatestDashboardViewSnapshot("SP500", "SP500_PAPER_WEB_V1")).thenReturn(snapshot);
        assertThat(new PaperLedgerViewService(provider, new ObjectMapper()).read().get("available")).isEqualTo(false);
    }
}
