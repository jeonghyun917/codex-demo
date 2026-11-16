package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Exercise production SQL and DDL on connection-local tables; persistent data is never changed. */
@EnabledIfEnvironmentVariable(named = "QUANT_EVIDENCE_DB_TEST", matches = "true")
class StockQuantHealthQueryPlanTest {
    private static final List<String> TABLES = List.of("stock_expected_return_snapshot",
            "stock_expected_return_factor_contribution", "stock_market_snapshot",
            "stock_index_membership_snapshot", "stock_shares_outstanding_snapshot",
            "stock_factor_exposure_snapshot", "stock_covariance_snapshot");
    private Connection connection;
    private JdbcTemplate jdbc;
    private String healthSql;
    private String pitSql;

    @BeforeEach void setup() throws Exception {
        connection = DriverManager.getConnection("jdbc:mariadb://127.0.0.1:3306/king_yurina", "root", "");
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        String schema = Files.readString(Path.of("src/main/resources/schema-mariadb.sql"));
        for (String table : TABLES) {
            var matcher = Pattern.compile("CREATE TABLE IF NOT EXISTS " + table + " \\(.*?\\);", Pattern.DOTALL)
                    .matcher(schema);
            assertTrue(matcher.find(), "Production table definition must exist");
            jdbc.execute(matcher.group().replaceFirst("CREATE TABLE IF NOT EXISTS", "CREATE TEMPORARY TABLE"));
        }
        for (String table : TABLES.subList(0, 2)) {
            var rows = new ArrayList<Object[]>();
            for (int i = 0; i < 6000; i++) rows.add(new Object[]{"SP500", "S" + i, "OLD_VERSION"});
            rows.add(new Object[]{"SP500", "A", "EXPECTED_RETURN_V9_CORRECTED_20261017"});
            rows.add(new Object[]{"SP500", "B", "EXPECTED_RETURN_V9_CORRECTED_20261017"});
            rows.add(new Object[]{"OTHER", "C", "EXPECTED_RETURN_V9_CORRECTED_20261017"});
            String factor = table.endsWith("factor_contribution") ? ",factor" : "";
            String factorValue = factor.isEmpty() ? "" : ",'QUALITY'";
            jdbc.batchUpdate("INSERT INTO " + table + "(index_code,symbol,model_version,signal_date,horizon_days"
                    + factor + ",calculated_at) VALUES(?,?,?,'2025-01-02',20" + factorValue + ",'2025-01-03 12:00:00')", rows);
        }
        var configuration = new Configuration();
        String resource = "mapper/stock/StockBacktestMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        healthSql = configuration.getMappedStatement(StockBacktestMapper.class.getName() + ".findQuantModelHealth")
                .getBoundSql(Map.of("indexCode", "SP500")).getSql();
        pitSql = configuration.getMappedStatement(StockBacktestMapper.class.getName() + ".findPitQuality")
                .getBoundSql(Map.of("indexCode", "SP500")).getSql();
    }

    @AfterEach void close() throws Exception { if (connection != null) connection.close(); }

    @Test void expectedReturnHealthUsesModelScopedLookupRatherThanAllLegacyModels() {
        assertSelectiveLookup("EXPECTED_RETURN_V9", "idx_stock_er_health_cover");
    }

    @Test void contributionHealthUsesModelScopedLookupRatherThanAllLegacyFactors() {
        assertSelectiveLookup("FACTOR_CONTRIBUTION", "idx_stock_er_factor_health_cover");
    }

    @Test void absentModelsRetainZeroCountsAndNullDates() {
        var row = jdbc.queryForMap(branch("EXPECTED_RETURN_V4"), "SP500");
        assertEquals(0, ((Number) row.get("rowCount")).intValue());
        assertEquals(0, ((Number) row.get("symbolCount")).intValue());
        assertNull(row.get("latestDate"));
        assertNull(row.get("latestCalculatedAt"));
    }

    @Test void additiveMigrationIsRepeatableAndPreservesExistingRows() throws Exception {
        // Upgrade genuinely unindexed temporary fixtures, then prove a repeat is a no-op.
        for (String table : TABLES) {
            var indexes = jdbc.queryForList("SHOW INDEX FROM " + table).stream()
                    .map(row -> row.get("Key_name").toString()).distinct()
                    .filter(name -> name.contains("health_cover")).toList();
            for (String index : indexes) jdbc.execute("DROP INDEX " + index + " ON " + table);
        }
        String migration = Files.readString(Path.of("src/main/resources/db/quant-health-query-indexes.sql"));
        for (int run = 0; run < 2; run++) {
            for (String statement : migration.replaceAll("(?m)^--[^\\n]*", "").split(";")) {
                if (!statement.isBlank()) jdbc.execute(statement);
            }
        }
        assertEquals(6003, jdbc.queryForObject("SELECT COUNT(*) FROM stock_expected_return_snapshot", Integer.class));
        assertEquals(6003, jdbc.queryForObject("SELECT COUNT(*) FROM stock_expected_return_factor_contribution", Integer.class));
        assertSelectiveLookup("EXPECTED_RETURN_V9", "idx_stock_er_health_cover");
        assertSelectiveLookup("FACTOR_CONTRIBUTION", "idx_stock_er_factor_health_cover");
    }

    @Test void historicalHealthCountsDoNotFetchEveryWideSnapshotRow() {
        var definitions = List.of(
                new String[]{"stock_market_snapshot", "MARKET_SNAPSHOT", "symbol,snapshot_date", "?,'2025-01-02'"},
                new String[]{"stock_index_membership_snapshot", "MEMBERSHIP_PIT", "symbol,snapshot_date", "?,'2025-01-02'"},
                new String[]{"stock_shares_outstanding_snapshot", "SHARES_PIT", "symbol,snapshot_date", "?,'2025-01-02'"},
                new String[]{"stock_factor_exposure_snapshot", "FACTOR_EXPOSURE", "symbol,signal_date,factor", "?,'2025-01-02','QUALITY'"},
                new String[]{"stock_covariance_snapshot", "COVARIANCE", "symbol_a,symbol_b,snapshot_date", "?,'OTHER','2025-01-02'"});
        for (var definition : definitions) {
            var rows = new ArrayList<Object[]>();
            for (int i = 0; i < 2000; i++) rows.add(new Object[]{"S" + i});
            jdbc.batchUpdate("INSERT INTO " + definition[0] + "(index_code," + definition[2]
                    + ",calculated_at) VALUES('SP500'," + definition[3] + ",'2025-01-03 12:00:00')", rows);
            var actual = jdbc.queryForMap(branch(definition[1]), "SP500");
            assertEquals(2000, ((Number) actual.get("rowCount")).intValue());
            assertEquals(2000, ((Number) actual.get("symbolCount")).intValue());
            assertEquals(1, ((Number) actual.get("dateCount")).intValue());
            assertIndexOnly(branch(definition[1]));
        }
    }

    @Test void provenanceClassificationUsesSourceGroupsAndPreservesWeightedScores() {
        String[] membership = {"OFFICIAL_NOTICE", "CSV_IMPORT_VERIFIED_PIT", "WIKIPEDIA_ARCHIVE",
                "CURRENT_PROXY", "CURRENT_PROXY", "UNKNOWN", "official_notice", "CSV_IMPORT_PIT_PROXY"};
        String[] shares = {"OFFICIAL_NOTICE", "CSV_IMPORT_PIT", "SEC_FILING", "FOO_SEC_X",
                "FINNHUB_CURRENT", "CURRENT_PROXY", "MISSING_SHARES", "UNKNOWN", "SEC_MISSING"};
        for (int i = 0; i < membership.length; i++) jdbc.update(
                "INSERT INTO stock_index_membership_snapshot(index_code,symbol,snapshot_date,source) VALUES('SP500',?,'2025-01-02',?)",
                "M" + i, membership[i]);
        jdbc.update("INSERT INTO stock_index_membership_snapshot(index_code,symbol,snapshot_date,source,is_member) VALUES('SP500','NO','2025-01-02','OFFICIAL',0)");
        for (int i = 0; i < shares.length; i++) jdbc.update(
                "INSERT INTO stock_shares_outstanding_snapshot(index_code,symbol,snapshot_date,source) VALUES('SP500',?,'2025-01-02',?)",
                "M" + i, shares[i]);
        var rows = jdbc.queryForList(pitSql, "SP500", "SP500");
        assertEquals(8, ((Number) rows.get(0).get("totalRows")).intValue());
        assertEquals(4, ((Number) rows.get(0).get("officialRows")).intValue());
        assertEquals(1, ((Number) rows.get(0).get("wikipediaRows")).intValue());
        assertEquals(3, ((Number) rows.get(0).get("proxyRows")).intValue());
        assertEquals(70.625, ((Number) rows.get(0).get("score")).doubleValue(), 0.0001);
        assertEquals(9, ((Number) rows.get(1).get("totalRows")).intValue());
        assertEquals(2, ((Number) rows.get(1).get("officialRows")).intValue());
        assertEquals(3, ((Number) rows.get(1).get("secRows")).intValue());
        assertEquals(4, ((Number) rows.get(1).get("proxyRows")).intValue());
        assertEquals(63.8889, ((Number) rows.get(1).get("score")).doubleValue(), 0.0001);
        // Materialized GROUP BY prevents repeated source-pattern classification for every historical row.
        var plan = jdbc.queryForList("EXPLAIN " + pitSql, "SP500", "SP500");
        assertEquals(2, plan.stream().filter(row -> "DERIVED".equals(row.get("select_type"))).count(),
                "Classify each grouped source once, not every historical holding row");
    }

    @Test void emptyProvenanceKeepsZeroPopulationAndUnknownScore() {
        var rows = jdbc.queryForList(pitSql, "SP500", "SP500");
        assertEquals(2, rows.size());
        for (var row : rows) {
            assertEquals(0, ((Number) row.get("totalRows")).intValue());
            assertNull(row.get("score"));
            assertNull(row.get("officialRows"));
            assertNull(row.get("proxyRows"));
        }
    }

    private void assertSelectiveLookup(String metric, String index) {
        String sql = branch(metric);
        var actual = jdbc.queryForMap(sql, "SP500");
        assertEquals(2, ((Number) actual.get("rowCount")).intValue());
        assertEquals(2, ((Number) actual.get("symbolCount")).intValue());
        assertEquals(1, ((Number) actual.get("dateCount")).intValue());
        var plan = jdbc.queryForList("EXPLAIN " + sql, "SP500");
        assertEquals(index, plan.getFirst().get("key"), "Health must read the requested model, not scan old/research models");
        assertTrue(Long.parseLong(plan.getFirst().get("rows").toString()) < 20,
                "Lookup must stay bounded to the model fixture");
        assertIndexOnly(sql);
    }

    private void assertIndexOnly(String sql) {
        var plan = jdbc.queryForList("EXPLAIN " + sql, "SP500");
        String extra = plan.getFirst().get("Extra").toString();
        assertTrue(extra.contains("Using index") && !extra.contains("Using index condition"),
                "Counts and freshness must be covered without wide-row fetches: " + extra);
    }

    private String branch(String metric) {
        String result = Arrays.stream(healthSql.split("UNION ALL"))
                .filter(sql -> sql.contains("'" + metric + "' AS metricKey")).findFirst().orElseThrow();
        int order = result.indexOf("ORDER BY CASE metricKey");
        return order < 0 ? result : result.substring(0, order);
    }
}
