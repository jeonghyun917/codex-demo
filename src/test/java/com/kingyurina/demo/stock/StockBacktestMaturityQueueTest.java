package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Real MariaDB fixtures shadow production names only within this connection. */
@EnabledIfEnvironmentVariable(named = "QUANT_EVIDENCE_DB_TEST", matches = "true")
class StockBacktestMaturityQueueTest {
    private static final LocalDate OLD = LocalDate.of(2020, 1, 2);
    private Connection connection;
    private JdbcTemplate jdbc;
    private String pendingSql;

    @BeforeEach
    void setup() throws Exception {
        connection = DriverManager.getConnection("jdbc:mariadb://127.0.0.1:3306/king_yurina", "root", "");
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        for (String table : List.of("stock_signal_snapshot", "stock_backtest_result_v2", "stock_candle_daily")) {
            String ddl = jdbc.queryForObject("SHOW CREATE TABLE " + table, (rs, row) -> rs.getString(2));
            jdbc.execute(ddl.replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE")
                    .replaceAll("(?m)^\\s*CONSTRAINT [^\\n]+\\n", "")
                    .replaceAll(",\\s*\\)", ")"));
        }
        Configuration configuration = new Configuration();
        String resource = "mapper/stock/StockBacktestMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        pendingSql = configuration.getMappedStatement(StockBacktestMapper.class.getName()
                + ".findSnapshotsNeedingResults").getBoundSql(Map.of("limit", 5000)).getSql();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (connection != null) connection.close();
    }

    @Test
    void immatureNewestFiveThousandDoNotStarveAnOlderComputableSnapshot() {
        List<Object[]> recent = new ArrayList<>();
        for (int id = 1; id <= 5001; id++) {
            recent.add(new Object[] {id, "REC" + id, "2026-10-17", "2026-10-17 10:00:00"});
        }
        jdbc.batchUpdate("INSERT INTO stock_signal_snapshot(id,symbol,signal_date,calculated_at) VALUES (?,?,?,?)", recent);
        signal(6000, "ELIGIBLE", OLD);
        candles("ELIGIBLE", OLD, 6);

        long started = System.nanoTime();
        List<String> selected = pending(5000);
        assertEquals(1, selected.size(), "Only one older snapshot has a computable missing horizon");
        assertEquals(List.of("ELIGIBLE"), selected);
        System.out.printf("Maturity queue fixture: 5002 snapshots, query %.2f ms%n",
                (System.nanoTime() - started) / 1_000_000.0);
        System.out.println("Maturity queue EXPLAIN: " + jdbc.queryForList("EXPLAIN " + pendingSql, 5000));
    }

    @ParameterizedTest
    @CsvSource({"5,5", "20,20", "60,60"})
    void missingHorizonNeedsOneEntryClosePlusAllLaterHorizonCloses(int horizon, int insufficientBars) {
        signal(1, "IMMATURE", OLD);
        signal(2, "ELIGIBLE", OLD);
        candles("IMMATURE", OLD, insufficientBars);
        candles("ELIGIBLE", OLD, insufficientBars + 1);
        for (int completed : List.of(5, 20, 60)) {
            if (completed < horizon) {
                result(1, "IMMATURE", completed, true, 50);
                result(2, "ELIGIBLE", completed, true, 50);
            }
        }
        assertEquals(List.of("ELIGIBLE"), pending(10));
    }

    @Test
    void currentFiveAndTwentyDayResultsWaitForSixtyDayMaturity() {
        signal(1, "WAITING", OLD);
        candles("WAITING", OLD, 60);
        result(1, "WAITING", 5, true, 50);
        result(1, "WAITING", 20, true, 50);
        assertTrue(pending(10).isEmpty(), "Missing 60D must not crowd the queue until the 61st future close");
        candle("WAITING", OLD.plusDays(61));
        assertEquals(List.of("WAITING"), pending(10));
    }

    @Test
    void currentCompleteResultsAreNotRequeued() {
        signal(1, "COMPLETE", OLD);
        candles("COMPLETE", OLD, 61);
        for (int horizon : List.of(5, 20, 60)) result(1, "COMPLETE", horizon, true, 50);
        assertTrue(pending(10).isEmpty());
    }

    @Test
    void staleOrMissingInstitutionResultsRequireAtLeastFiveDayMaturity() {
        signal(1, "STALE", OLD);
        signal(2, "INSTITUTION", OLD);
        signal(3, "TOO_NEW", OLD);
        jdbc.update("UPDATE stock_signal_snapshot SET institution_score=55 WHERE id IN (2,3)");
        candles("STALE", OLD, 6);
        candles("INSTITUTION", OLD, 6);
        candles("TOO_NEW", OLD, 5);
        for (int horizon : List.of(5, 20, 60)) {
            result(1, "STALE", horizon, horizon != 5, 50);
            result(2, "INSTITUTION", horizon, true, null);
            result(3, "TOO_NEW", horizon, false, null);
        }
        assertEquals(List.of("INSTITUTION", "STALE"), pending(10));
    }

    @Test
    void oneStaleHorizonIsNotHiddenByAnotherFreshHorizon() {
        signal(1, "STALE_ONE", OLD);
        candles("STALE_ONE", OLD, 61);
        result(1, "STALE_ONE", 5, true, 50);
        result(1, "STALE_ONE", 20, false, 50);
        result(1, "STALE_ONE", 60, true, 50);
        assertEquals(List.of("STALE_ONE"), pending(10));
    }

    private List<String> pending(int limit) {
        return jdbc.query(pendingSql, (rs, row) -> rs.getString("symbol"), limit);
    }

    private void signal(long id, String symbol, LocalDate day) {
        jdbc.update("INSERT INTO stock_signal_snapshot(id,symbol,signal_date,calculated_at) VALUES (?,?,?,?)",
                id, symbol, day, "2026-10-17 10:00:00");
    }

    private void candles(String symbol, LocalDate signalDate, int count) {
        List<Object[]> rows = new ArrayList<>();
        // Candle rows, not calendar days, define maturity. The signal-day row must not count.
        rows.add(new Object[] {symbol, signalDate});
        for (int i = 1; i <= count; i++) rows.add(new Object[] {symbol, signalDate.plusDays(i)});
        jdbc.batchUpdate("INSERT INTO stock_candle_daily(symbol,trade_date,open_price,high_price,low_price,close_price,volume,source) VALUES (?,?,100,101,99,100,100,'fixture')", rows);
    }

    private void candle(String symbol, LocalDate date) {
        jdbc.update("INSERT INTO stock_candle_daily(symbol,trade_date,open_price,high_price,low_price,close_price,volume,source) VALUES (?,?,100,101,99,100,100,'fixture')", symbol, date);
    }

    private void result(long id, String symbol, int horizon, boolean current, Integer institution) {
        jdbc.update("INSERT INTO stock_backtest_result_v2(snapshot_id,symbol,signal_date,horizon_days,entry_trade_date,entry_close,exit_trade_date,exit_close,forward_return_pct,calculated_at,institution_score) VALUES (?,?,?,?,?,100,?,100,0,?,?)",
                id, symbol, OLD, horizon, OLD.plusDays(1), OLD.plusDays(horizon + 1),
                current ? "2026-10-17 11:00:00" : "2026-10-17 09:00:00", institution);
    }
}
