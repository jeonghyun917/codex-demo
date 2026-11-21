package com.kingyurina.demo.stock.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        run.setContractVersion(ExpectedReturnPredictionContract.sp500Excess20dCorrected().version());
        run.setIndexCode("SP500");
        run.setBaselineModelVersion(ExpectedReturnPredictionContract.sp500Excess20dCorrected().baselineModel());
        run.setAsOfDate(LocalDate.of(2026, 7, 15));
        run.setStatus("COMPLETED");
        run.setDecision("BASELINE_QUALIFIED");
        run.setValidRowCount(5000);
        run.setTargetRowCount(5500);
        run.setCoveragePct(90.9d);
        run.setRankIc(0.035d);
        run.setBrierScore(0.21d);
        run.setAnnualizedExcessReturnPct(3.2d);
        run.setChecksJson("{\"checks\":[{\"name\":\"RANK_IC_POSITIVE\",\"passed\":true}],"
                + "\"exclusions\":{\"MISSING_PREDICTION\":42},\"datasetTargets\":5542}");
        when(mapper.findLatestExpectedReturnEvaluationRun("SP500", ExpectedReturnPredictionContract.sp500Excess20dCorrected().version()))
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
        assertTrue(view.gateSummary().contains("MISSING_PREDICTION=42"));
        assertTrue(view.gateSummary().contains("5542"));
    }
}

