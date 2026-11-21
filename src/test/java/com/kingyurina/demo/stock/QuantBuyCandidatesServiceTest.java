package com.kingyurina.demo.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.Test;

class QuantBuyCandidatesServiceTest {
    @Test void selectedHistoricalDateKeepsFrozenRankAndDoesNotBecomeCurrentBuySignal() {
        var old = new HashMap<>(day("2026-10-30", "2026-11-02T14:30:00Z", List.of(row(1, false))));
        old.put("tracking", Map.of("available", true, "asof", "2026-11-13", "rows", List.of()));
        when(ledger.read()).thenReturn(Map.of("available", true, "days", List.of(old,
                day("2026-11-13", "2026-11-16T14:30:00Z", List.of(row(2, true))))));
        var service = new QuantBuyCandidatesService(ledger, clock);
        var historical = service.build("SP500", "2026-10-30");
        assertThat(historical.status()).isEqualTo("ARCHIVED");
        assertThat(historical.date()).isEqualTo("2026-10-30");
        assertThat(historical.rows().getFirst()).containsEntry("symbol", "S1");
        assertThat(historical.dates()).containsExactly("2026-11-13", "2026-10-30");
        assertThat(historical.tracking()).containsEntry("asof", "2026-11-13");
        assertThat(service.build("SP500", "2026-10-29").rows()).isEmpty();
        assertThat(service.build("SP500").date()).isEqualTo("2026-11-13");
    }
    @Test void referenceCloseMustMatchDecisionDateAndCompanyNameIsPreserved() {
        var input = new HashMap<>(row(1, false));
        input.put("referenceClose", 123.45);
        input.put("referenceDate", "2026-11-13");
        input.put("companyName", "Example Corp");
        when(ledger.read()).thenReturn(Map.of("available", true, "days", List.of(
                day("2026-11-13", "2026-11-16T14:30:00Z", List.of(input)))));
        var service = new QuantBuyCandidatesService(ledger, clock);
        assertThat(service.build("SP500").rows().getFirst()).containsEntry("referencePrice", "$123.45")
                .containsEntry("referenceTakeProfit", "$141.97").containsEntry("referenceStopLoss", "$117.28")
                .containsEntry("companyName", "Example Corp").containsEntry("buyEligible", false);
        input.put("referenceDate", "2026-11-12");
        assertThat(service.build("SP500").rows().getFirst()).containsEntry("referencePrice", "미기록");
        assertThat(service.build("SP500").rows().getFirst()).containsEntry("referenceTakeProfit", "-")
                .containsEntry("referenceStopLoss", "-");
    }
    private final PaperLedgerViewService ledger = mock(PaperLedgerViewService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-14T06:00:00Z"), ZoneOffset.UTC);

    private Map<String, Object> day(String date, String nextOpen, List<Map<String, Object>> rows) {
        return Map.of("date", date, "recordedAt", date + "T22:00:00Z", "nextOpen", nextOpen,
                "predictions", rows, "trend", "1.20%", "volatilityCap", "2.00%");
    }

    private Map<String, Object> row(int rank, boolean selected) {
        return Map.of("rank", rank, "symbol", "S" + rank, "selected", selected,
                "interest", true, "probability", "52.80%", "volatility", "1.00%");
    }

    @Test void showsProbabilityTopTwentyRegardlessOfEligibilityWithoutMutation() {
        var rows = new ArrayList<Map<String, Object>>();
        for (int i = 25; i >= 1; i--) rows.add(row(i, i != 1));
        when(ledger.read()).thenReturn(Map.of("available", true, "days", List.of(
                day("2026-11-13", "2026-11-16T14:30:00Z", rows))));
        var view = new QuantBuyCandidatesService(ledger, clock).build("SP500");
        assertThat(view.status()).isEqualTo("READY");
        assertThat(view.rows()).hasSize(20);
        assertThat(view.rows().getFirst().get("symbol")).isEqualTo("S1");
        assertThat(view.rows().getLast().get("symbol")).isEqualTo("S20");
        assertThat(view.eligibleCount()).isEqualTo(24);
        assertThat(view.rows().getFirst()).containsEntry("buyEligible", false);
        assertThat(rows.getFirst().get("symbol")).isEqualTo("S25");
        verify(ledger).read();
    }

    @Test void stalePredictionIsNeverPresentedAsNextSessionCandidate() {
        when(ledger.read()).thenReturn(Map.of("available", true, "days", List.of(
                day("2026-10-30", "2026-11-02T14:30:00Z", List.of(row(1, true))))));
        var view = new QuantBuyCandidatesService(ledger, clock).build("SP500");
        assertThat(view.status()).isEqualTo("STALE");
        assertThat(view.rows()).isEmpty();
        assertThat(view.date()).isEqualTo("2026-10-30");
    }

    @Test void usesLatestDateWithoutFallingBackToOlderPassingDay() {
        when(ledger.read()).thenReturn(Map.of("available", true, "days", List.of(
                day("2026-11-12", "2026-11-13T14:30:00Z", List.of(row(1, true))),
                day("2026-11-13", "2026-11-16T14:30:00Z", List.of(row(1, false))))));
        var view = new QuantBuyCandidatesService(ledger, clock).build("SP500");
        assertThat(view.status()).isEqualTo("NO_CANDIDATES");
        assertThat(view.rows()).hasSize(1);
        assertThat(view.rows().getFirst()).containsEntry("buyEligible", false);
    }

    @Test void preservesExclusionReasonsAndDoesNotTurnWatchlistIntoOrders() {
        var excluded = new HashMap<>(row(1, false));
        excluded.put("interest", false);
        excluded.put("reason", "확률 상위 20% 밖 · 시장 추세 미충족 · 변동성 초과");
        when(ledger.read()).thenReturn(Map.of("available", true, "days", List.of(
                day("2026-11-13", "2026-11-16T14:30:00Z", List.of(excluded)))));
        var view = new QuantBuyCandidatesService(ledger, clock).build("SP500");
        assertThat(view.eligibleCount()).isZero();
        assertThat(view.rows().getFirst()).containsEntry("buyEligible", false)
                .containsEntry("buyReason", excluded.get("reason"));
        assertThat(excluded).doesNotContainKeys("buyEligible", "buyReason");
    }

    @Test void missingOrInvalidDataFailsClosedAndOtherIndexesAreNotSubstituted() {
        var service = new QuantBuyCandidatesService(ledger, clock);
        assertThat(service.build("NASDAQ100").status()).isEqualTo("UNSUPPORTED");
        verifyNoInteractions(ledger);
        when(ledger.read()).thenReturn(Map.of("available", false));
        assertThat(service.build("SP500").rows()).isEmpty();
        when(ledger.read()).thenReturn(Map.of("available", true, "days", List.of(
                day("2026-11-13", "bad-time", List.of(row(1, true))))));
        assertThat(service.build("SP500").status()).isEqualTo("UNAVAILABLE");
    }

    @Test void linksHistoricalBinsWithoutTurningMissingSamplesIntoZero() {
        var bins = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 10; i++) bins.add(Map.of("count", 0));
        bins.set(5, Map.of("count", 12, "tpRate", "25.00%", "slRate", "50.00%", "meanReturn", "1.20%"));
        var boundary = new HashMap<>(row(2, true));
        boundary.put("probability", "50.00%");
        when(ledger.read()).thenReturn(Map.of("available", true, "days", List.of(
                day("2026-11-13", "2026-11-16T14:30:00Z", List.of(row(1, true), boundary))),
                "probabilityEvidence", Map.of("available", true, "groups", List.of(
                        Map.of("key", "baseline-0.001", "rows", bins)))));
        var view = new QuantBuyCandidatesService(ledger, clock).build("SP500");
        assertThat(view.rows().getFirst()).containsEntry("tpRate", "25.00%").containsEntry("sample", "12건 · 소표본");
        assertThat(view.rows().get(1)).containsEntry("tpRate", "-").containsEntry("referencePrice", "미기록");
    }

    @Test void rejectsFutureRecordAndOpenTimeBoundaryAndDuplicateSymbols() {
        var service = new QuantBuyCandidatesService(ledger, clock);
        for (var invalid : List.of(
                day("2026-11-14", "2026-11-16T14:30:00Z", List.of(row(1, true))),
                day("2026-11-13", "2026-11-16T14:30:00Z", List.of(row(1, true), row(1, true))))) {
            when(ledger.read()).thenReturn(Map.of("available", true, "days", List.of(invalid)));
            assertThat(service.build("SP500").status()).isEqualTo("UNAVAILABLE");
        }
        when(ledger.read()).thenReturn(Map.of("available", true, "days", List.of(
                day("2026-11-13", "2026-11-14T06:00:00Z", List.of(row(1, true))))));
        assertThat(service.build("SP500").status()).isEqualTo("STALE");
    }
}
