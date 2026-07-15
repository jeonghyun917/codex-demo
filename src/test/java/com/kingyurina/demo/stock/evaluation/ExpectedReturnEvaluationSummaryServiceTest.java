package com.kingyurina.demo.stock.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.kingyurina.demo.stock.StockBacktestMapper;

class ExpectedReturnEvaluationSummaryServiceTest {

    @Test
    void noStoredRunReturnsExplicitNotEvaluatedView() {
        StockBacktestMapper mapper = mock(StockBacktestMapper.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<StockBacktestMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);

        ExpectedReturnEvaluationSummaryView view =
                new ExpectedReturnEvaluationSummaryService(provider).build("SP500");

        assertEquals("Not evaluated", view.status());
        assertEquals("neutral", view.tone());
    }

    @Test
    void completedRunExposesDecisionAndCoreMetrics() {
        StockBacktestMapper mapper = mock(StockBacktestMapper.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<StockBacktestMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        ExpectedReturnEvaluationRun run = new ExpectedReturnEvaluationRun();
        run.setContractVersion("SP500_EXCESS_20D_V1");
        run.setIndexCode("SP500");
        run.setBaselineModelVersion("EXPECTED_RETURN_V9");
        run.setAsOfDate(LocalDate.of(2026, 7, 15));
        run.setStatus("COMPLETED");
        run.setDecision("BASELINE_QUALIFIED");
        run.setValidRowCount(5000);
        run.setTargetRowCount(5500);
        run.setCoveragePct(90.9d);
        run.setRankIc(0.035d);
        run.setBrierScore(0.21d);
        run.setAnnualizedExcessReturnPct(3.2d);
        run.setChecksJson("[{\"name\":\"RANK_IC_POSITIVE\",\"passed\":true}]");
        when(mapper.findLatestExpectedReturnEvaluationRun("SP500", "SP500_EXCESS_20D_V1"))
                .thenReturn(run);

        ExpectedReturnEvaluationSummaryView view =
                new ExpectedReturnEvaluationSummaryService(provider).build("SP500");

        assertEquals("BASELINE_QUALIFIED", view.decision());
        assertEquals("positive", view.tone());
        assertEquals("5,000 / 5,500", view.samples());
        assertEquals("90.9%", view.coverage());
        assertEquals("0.0350", view.rankIc());
        assertEquals("0.2100", view.brierScore());
        assertEquals("+3.20%", view.annualizedExcessReturn());
    }
}

