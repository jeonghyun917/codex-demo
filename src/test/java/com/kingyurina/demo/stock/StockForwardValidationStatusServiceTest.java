package com.kingyurina.demo.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

class StockForwardValidationStatusServiceTest {
    private static final String PENDING = """
        {"format":"QUANT_FORWARD_VALIDATION_STATUS_V1","indexCode":"SP500","generatedAtUtc":"2026-01-03T12:00:00Z",
         "status":"AWAITING_MATURITY","promotionAllowed":false,
         "research":{"classification":"EXPOSED_TRAINING_NOT_FORWARD","baselineRawMae":5.749702649,
          "baselineExcessMae":4.405489367,"candidateRawMae":5.757472208,"candidateExcessMae":4.410768581,"improved":false},
         "forward":{"originalObservations":498,"scoredObservations":0,"cohorts":1,"maturedCohorts":0,
          "firstEarliestEvaluationUtc":"2026-12-01T21:00:00Z","entryNonTradable":1,"missingEntryClose":0,
          "rawMae":null,"excessMae":null},
         "benchmark":{"kind":"MARKET_SNAPSHOT_WEIGHTED_PRICE_PROXY","totalReturn":false},
         "limitations":["중복된 20D 창은 독립 표본이 아닙니다.","비용·배당 제외; 청산가 미확보는 평가에서 분리합니다."]}
        """;

    @Test
    void pendingSnapshotShowsNoErrorScoreAndRetainsOriginalDenominator() {
        var view = service(PENDING).build("SP500");
        assertThat(view.available()).isTrue();
        assertThat(view.status()).isEqualTo("만기 대기");
        assertThat(view.rawMae()).isEqualTo("미평가");
        assertThat(view.excessMae()).isEqualTo("미평가");
        assertThat(view.forwardSummary()).contains("498", "0 / 1");
        assertThat(view.entrySummary()).contains("1", "0");
        assertThat(view.researchSummary()).contains("악화", "학습");
        assertThat(view.firstEvaluation()).contains("2026-12-02 06:00");
        assertThat(view.modelStatus()).contains("미승격");
    }

    @Test
    void zeroMaturedErrorsAreNotConfusedWithMissingOrPending() {
        String matured = PENDING.replace("AWAITING_MATURITY", "MATURED")
                .replace("2026-01-03T12:00:00Z", "2026-01-05T12:00:00Z")
                .replace("\"maturedCohorts\":0", "\"maturedCohorts\":1")
                .replace("\"scoredObservations\":0", "\"scoredObservations\":498")
                .replace("2026-12-01T21:00:00Z", "2026-01-04T21:00:00Z")
                .replace("\"rawMae\":null,\"excessMae\":null", "\"rawMae\":0,\"excessMae\":0");
        var view = service(matured).build("SP500");
        assertThat(view.available()).isTrue();
        assertThat(view.rawMae()).isEqualTo("0.0000%p");
        assertThat(view.excessMae()).isEqualTo("0.0000%p");
    }

    @Test
    void immatureScoresInvalidCountsAndUnprovenPromotionAreRejected() {
        for (String invalid : new String[] {
                PENDING.replace("\"rawMae\":null", "\"rawMae\":0.2"),
                PENDING.replace("\"originalObservations\":498", "\"originalObservations\":-1"),
                PENDING.replace("\"promotionAllowed\":false", "\"promotionAllowed\":true"),
                PENDING.replace("\"maturedCohorts\":0", "\"maturedCohorts\":2"),
                PENDING.replace("AWAITING_MATURITY", "MATURED").replace("\"maturedCohorts\":0", "\"maturedCohorts\":1")
                    .replace("\"rawMae\":null,\"excessMae\":null", "\"rawMae\":0,\"excessMae\":0"),
                PENDING.replace("\"totalReturn\":false", "\"totalReturn\":true"),
                PENDING.replace("\"indexCode\":\"SP500\"", "\"indexCode\":\"DOW30\""),
                PENDING.replace("\"scoredObservations\":0", "\"scoredObservations\":499"),
                PENDING.replace("\"baselineRawMae\":5.749702649", "\"baselineRawMae\":null"),
                "{}", "not json" }) {
            assertThat(service(invalid).build("SP500").available()).as(invalid).isFalse();
        }
    }

    @Test
    void expiredButUnscoredCohortIsPartialAndNeverGetsZeroMae() {
        String partial = PENDING.replace("AWAITING_MATURITY", "PARTIAL")
                .replace("2026-01-03T12:00:00Z", "2026-01-05T12:00:00Z")
                .replace("2026-12-01T21:00:00Z", "2026-01-04T21:00:00Z")
                .replace("\"maturedCohorts\":0", "\"maturedCohorts\":1");
        var view = service(partial).build("SP500");
        assertThat(view.available()).isTrue();
        assertThat(view.status()).isEqualTo("일부 만기 평가");
        assertThat(view.rawMae()).isEqualTo("미평가");
        assertThat(view.forwardSummary()).contains("평가 0 / 498");
    }

    @Test
    void completeCaseScoresRemainPartialAgainstOriginalPopulation() {
        String partial = PENDING.replace("AWAITING_MATURITY", "PARTIAL")
                .replace("2026-01-03T12:00:00Z", "2026-01-05T12:00:00Z")
                .replace("2026-12-01T21:00:00Z", "2026-01-04T21:00:00Z")
                .replace("\"maturedCohorts\":0", "\"maturedCohorts\":1")
                .replace("\"scoredObservations\":0", "\"scoredObservations\":497")
                .replace("\"rawMae\":null,\"excessMae\":null", "\"rawMae\":1.2,\"excessMae\":2.3");
        var view = service(partial).build("SP500");
        assertThat(view.available()).isTrue();
        assertThat(view.status()).isEqualTo("일부 만기 평가");
        assertThat(view.rawMae()).isEqualTo("1.2000%p");
        assertThat(view.forwardSummary()).contains("평가 497 / 498");
    }

    @Test
    void absentDatabaseSnapshotIsExplicitlyUnpublishedNotZero() {
        var view = service(null).build("SP500");
        assertThat(view.available()).isFalse();
        assertThat(view.status()).contains("미발행");
        assertThat(view.rawMae()).isEqualTo("미평가");
    }

    private static StockForwardValidationStatusService service(String payload) {
        StockBacktestMapper mapper = mock(StockBacktestMapper.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<StockBacktestMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        if (payload != null) {
            var snapshot = new StockDashboardViewSnapshot();
            snapshot.setIndexCode("SP500");
            snapshot.setViewVersion("QUANT_FORWARD_VALIDATION_STATUS_V1");
            snapshot.setPayloadJson(payload);
            when(mapper.findLatestDashboardViewSnapshot("SP500", "QUANT_FORWARD_VALIDATION_STATUS_V1"))
                    .thenReturn(snapshot);
        }
        return new StockForwardValidationStatusService(provider, new ObjectMapper());
    }
}
