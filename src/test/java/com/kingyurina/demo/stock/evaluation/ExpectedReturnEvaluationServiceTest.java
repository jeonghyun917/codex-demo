package com.kingyurina.demo.stock.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.kingyurina.demo.stock.StockBacktestMapper;

import tools.jackson.databind.ObjectMapper;

class ExpectedReturnEvaluationServiceTest {

    @Test
    void emptyDatasetCreatesRunningRunAndCompletesAsInsufficient() {
        StockBacktestMapper mapper = mock(StockBacktestMapper.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<StockBacktestMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        when(mapper.findExpectedReturnSnapshots(anyString(), any(), any())).thenReturn(List.of());
        when(mapper.findResultsForEvaluation(anyString(), anyInt(), any(), any())).thenReturn(List.of());
        when(mapper.findBenchmarkReturns(anyString(), any(), any())).thenReturn(List.of());
        when(mapper.findIndexMembershipSnapshots(anyString(), any(), any())).thenReturn(List.of());
        doAnswer(invocation -> {
            ExpectedReturnEvaluationRun run = invocation.getArgument(0);
            run.setId(42L);
            return null;
        }).when(mapper).insertExpectedReturnEvaluationRun(any(ExpectedReturnEvaluationRun.class));
        when(mapper.completeExpectedReturnEvaluationRun(any(ExpectedReturnEvaluationRun.class))).thenReturn(1);

        ExpectedReturnEvaluationService service =
                new ExpectedReturnEvaluationService(provider, new ObjectMapper());

        ExpectedReturnEvaluationRun run =
                service.evaluateBaseline("SP500", LocalDate.of(2026, 7, 15));

        assertEquals(42L, run.getId());
        assertEquals("INSUFFICIENT_DATA", run.getStatus());
        assertEquals("INSUFFICIENT_DATA", run.getDecision());
        assertNotNull(run.getFinishedAt());
        verify(mapper).insertExpectedReturnEvaluationRun(any(ExpectedReturnEvaluationRun.class));
        verify(mapper).completeExpectedReturnEvaluationRun(run);
    }
}

