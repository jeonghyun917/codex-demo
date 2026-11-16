package com.kingyurina.demo.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;

class StockQuantSourceHealthTest {
    @Test
    void currentPredictionQueryRejectsLegacyOldAndFutureSignals() throws Exception {
        String sql = sql("findLatestSignalRows");
        assertThat(sql).contains("sl.signal_version = 'q4-fin-20261031'",
                "sl.calculated_at >= CURRENT_DATE", "sl.calculated_at <= CURRENT_TIMESTAMP",
                "DATE(sl.calculated_at) AS signalDate").doesNotContain("CURRENT_DATE AS signalDate");
    }

    @Test
    void healthQueryInspectsActualInputDatesAndOnlyUsableCurrentUniverseCoverage() throws Exception {
        String sql = sql("findQuantModelHealth");
        assertThat(sql).contains("'QUOTE' AS metricKey", "'CANDLE' AS metricKey",
                "MIN(DATE(q.quote_time)) AS latestDate", "MIN(c.latest_trade_date) AS latestDate",
                "q.quote_time <= CURRENT_TIMESTAMP", "trade_date <= CURRENT_DATE",
                "sl.signal_version = 'q4-fin-20261031'", "JSON_LENGTH(dq.stale_sources_json) = 0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUOTE", "CANDLE", "SIGNAL_LATEST", "DATA_QUALITY"})
    void partialCurrentUniverseCoverageCannotBeHealthy(String key) {
        var metric = metric(key, 1, LocalDate.now());
        var view = build(List.of(metric("UNIVERSE", 2, null), metric,
                metric("QUOTE", 2, LocalDate.now()), metric("CANDLE", 2, LocalDate.now())));
        assertThat(view.rows().get(1).tone()).isEqualTo("warning");
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUOTE", "CANDLE"})
    void freshRecalculationCannotHideStaleInputDates(String staleKey) {
        var quote = metric("QUOTE", 2, LocalDate.now());
        var candle = metric("CANDLE", 2, LocalDate.now());
        (staleKey.equals("QUOTE") ? quote : candle).setLatestDate(LocalDate.now().minusDays(30));
        var view = build(List.of(metric("UNIVERSE", 2, null), quote, candle,
                metric("SIGNAL_LATEST", 2, LocalDate.now()), metric("DATA_QUALITY", 2, LocalDate.now())));
        assertThat(view.rows().get(3).tone()).isEqualTo("warning");
        assertThat(view.rows().get(4).tone()).isEqualTo("warning");
    }

    @Test
    void missingSourceMetricsCannotMakeFreshSignalsHealthy() {
        var view = build(List.of(metric("UNIVERSE", 2, null), metric("SIGNAL_LATEST", 2, LocalDate.now())));
        assertThat(view.rows().get(1).tone()).isEqualTo("warning");
    }

    @Test
    void completeFreshSourcesAndSignalsAreHealthy() {
        var view = build(List.of(metric("UNIVERSE", 2, null), metric("QUOTE", 2, LocalDate.now()),
                metric("CANDLE", 2, LocalDate.now()), metric("SIGNAL_LATEST", 2, LocalDate.now()),
                metric("DATA_QUALITY", 2, LocalDate.now())));
        assertThat(view.rows()).allSatisfy(row -> assertThat(row.tone()).isEqualTo("positive"));
    }

    private static StockQuantModelHealthMetric metric(String key, long symbols, LocalDate date) {
        var metric = new StockQuantModelHealthMetric();
        metric.setMetricKey(key);
        metric.setLayer(key);
        metric.setRowCount(2L);
        metric.setSymbolCount(symbols);
        metric.setLatestDate(date);
        metric.setLatestCalculatedAt(LocalDateTime.now());
        return metric;
    }

    @SuppressWarnings("unchecked")
    private static StockQuantModelHealthView build(List<StockQuantModelHealthMetric> metrics) {
        var mapper = mock(StockBacktestMapper.class);
        when(mapper.findQuantModelHealth("SP500")).thenReturn(metrics);
        ObjectProvider<StockBacktestMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        return new StockQuantModelHealthService(provider).build("SP500");
    }

    private static String sql(String id) throws Exception {
        var configuration = new Configuration();
        String resource = "mapper/stock/StockBacktestMapper.xml";
        try (var input = StockQuantSourceHealthTest.class.getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement(StockBacktestMapper.class.getName() + "." + id)
                .getBoundSql(Map.of("indexCode", "SP500")).getSql().replaceAll("\\s+", " ");
    }
}
