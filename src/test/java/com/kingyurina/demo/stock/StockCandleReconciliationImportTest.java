package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import tools.jackson.databind.ObjectMapper;

@EnabledIfEnvironmentVariable(named="QUANT_EVIDENCE_DB_TEST", matches="true")
class StockCandleReconciliationImportTest {
    @TempDir Path directory;
    Connection connection;
    SingleConnectionDataSource db;
    JdbcTemplate jdbc;
    ObjectMapper mapper = new ObjectMapper();
    @BeforeEach void setup() throws Exception {
        connection = DriverManager.getConnection("jdbc:mariadb://127.0.0.1:3306/king_yurina", "root", "");
        db = new SingleConnectionDataSource(connection, true); jdbc = new JdbcTemplate(db);
        for (String table : List.of("stock_symbol", "stock_candle_daily", "stock_candle_daily_source",
                "stock_candle_reconciliation_run", "stock_candle_reconciliation_backup")) {
            String ddl=jdbc.queryForObject("SHOW CREATE TABLE "+table, (rs,n)->rs.getString(2));
            jdbc.execute(ddl.replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE")
                    .replaceAll("(?m)^\\s*CONSTRAINT [^\\n]+\\n", "").replaceAll(",\\s*\\)", ")"));
        }
        jdbc.update("INSERT INTO stock_symbol(symbol) VALUES ('APH'),('HON')");
        jdbc.update("INSERT INTO stock_candle_daily(symbol,trade_date,open_price,high_price,low_price,close_price,volume,source) VALUES ('APH','2021-06-07',40,40,40,40,100,'yahoo-chart')");
    }
    @AfterEach void cleanup() throws Exception { if(connection!=null) connection.close(); }
    private StockHistoricalEvidenceBundle bundle() throws Exception {
        long timestamp = java.time.Instant.parse("2021-06-07T13:30:00Z").getEpochSecond();
        String raw="""
                {"chart":{"error":null,"result":[{"meta":{"symbol":"APH","currency":"USD","instrumentType":"EQUITY","exchangeTimezoneName":"America/New_York"},
                "timestamp":[%d],"indicators":{"quote":[{"open":[20],"high":[20],"low":[20],"close":[20],"volume":[200]}],"adjclose":[{"adjclose":[15]}]}}]}}
                """.formatted(timestamp);
        Files.writeString(directory.resolve("APH.json"),raw);
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var source=Map.of("id","aph","url","https://query1.finance.yahoo.com/v8/finance/chart/APH?events=div,splits",
                "publisher","Yahoo Finance","retrievedAt","2026-10-17T14:00:00Z","sha256",hash,"path","APH.json");
        Path path=directory.resolve("bundle.json");
        Files.writeString(path,mapper.writeValueAsString(Map.of("sources",List.of(source),"events",List.of(),"coverage","PROVIDER_REPORTED_EVENTS")));
        return StockHistoricalEvidenceBundle.read(path,mapper);
    }
    @Test void dryRunDoesNotWritePricesOrAuditTables() throws Exception {
        var result=StockCandleReconciliationBatchRunner.reconcile(db,bundle(),List.of("APH"),false,mapper);
        assertEquals("DRY_RUN",result.status()); assertEquals(1,result.symbols().getFirst().changedRows());
        assertEquals(40,jdbc.queryForObject("SELECT close_price FROM stock_candle_daily",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM stock_candle_reconciliation_run",Integer.class));
    }
    @Test void writesProviderOhlcWithBackupAndIdempotentReplay() throws Exception {
        var bundle=bundle();
        var result=StockCandleReconciliationBatchRunner.reconcile(db,bundle,List.of("APH"),true,mapper);
        assertEquals("COMMITTED",result.status());
        assertEquals(20,jdbc.queryForObject("SELECT close_price FROM stock_candle_daily",Integer.class),"Never substitute adjusted close=15");
        assertEquals(200L,jdbc.queryForObject("SELECT volume FROM stock_candle_daily",Long.class));
        assertEquals(40,jdbc.queryForObject("SELECT old_close FROM stock_candle_reconciliation_backup",Integer.class));
        assertEquals(20,jdbc.queryForObject("SELECT close_price FROM stock_candle_daily_source",Integer.class));
        assertEquals("NO_CHANGES",StockCandleReconciliationBatchRunner.reconcile(db,bundle,List.of("APH"),true,mapper).status());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM stock_candle_reconciliation_run",Integer.class));
    }
    @Test void incompleteSecondSymbolRollsBackEntireRepair() throws Exception {
        assertThrows(IllegalArgumentException.class,()->StockCandleReconciliationBatchRunner.reconcile(db,bundle(),List.of("APH","HON"),true,mapper));
        assertEquals(40,jdbc.queryForObject("SELECT close_price FROM stock_candle_daily",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM stock_candle_reconciliation_backup",Integer.class));
    }
    @Test void rejectsProviderIdentityMismatchAndTamperedRawBytes() throws Exception {
        var bundle=bundle();
        Files.writeString(directory.resolve("APH.json"),Files.readString(directory.resolve("APH.json")).replace("\"APH\"","\"HON\""));
        assertThrows(IllegalArgumentException.class,()->StockCandleReconciliationBatchRunner.reconcile(db,bundle,List.of("APH"),true,mapper));
        assertEquals(40,jdbc.queryForObject("SELECT close_price FROM stock_candle_daily",Integer.class));
    }
    @Test void databaseFailureAfterPriceUpdateRollsBackPriceBackupAndRun() throws Exception {
        jdbc.update("INSERT INTO stock_candle_daily_source(symbol,trade_date,source,open_price,high_price,low_price,close_price,volume) VALUES ('APH','2021-06-07','yahoo-chart',40,40,40,40,100)");
        jdbc.execute("ALTER TABLE stock_candle_daily_source ADD CONSTRAINT test_volume_limit CHECK (volume<150)");
        assertThrows(java.sql.SQLException.class,()->StockCandleReconciliationBatchRunner.reconcile(db,bundle(),List.of("APH"),true,mapper));
        assertEquals(40,jdbc.queryForObject("SELECT close_price FROM stock_candle_daily",Integer.class));
        assertEquals(40,jdbc.queryForObject("SELECT close_price FROM stock_candle_daily_source",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM stock_candle_reconciliation_backup",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM stock_candle_reconciliation_run",Integer.class));
    }
    @Test void rejectsWrongIdentityEvenWhenNewBytesHaveAValidHash() throws Exception {
        var bundle=bundle(); var s=bundle.sources().getFirst();
        byte[] modified=Files.readString(Path.of(s.path())).replace("\"APH\"","\"HON\"").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(Path.of(s.path()),modified);
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(modified));
        var rehashed=new StockHistoricalEvidenceBundle.Source(s.key(),s.url(),s.publisher(),s.publishedDate(),s.retrievedAt(),hash,s.path());
        var error=assertThrows(IllegalArgumentException.class,()->StockCandleReconciliation.readSnapshot(rehashed,"APH",mapper));
        assertTrue(error.getMessage().contains("Provider symbol mismatch"));
    }
    @Test void committedRepairPreservesExistingSourceRowBeforeImage() throws Exception {
        jdbc.update("INSERT INTO stock_candle_daily_source(symbol,trade_date,source,open_price,high_price,low_price,close_price,volume,created_at,updated_at) VALUES ('APH','2021-06-07','yahoo-chart',40,40,40,40,100,'2026-10-17 01:02:03','2026-10-17 04:05:06')");
        StockCandleReconciliationBatchRunner.reconcile(db,bundle(),List.of("APH"),true,mapper);
        var backup=mapper.readTree(jdbc.queryForObject("SELECT old_source_row_json FROM stock_candle_reconciliation_backup",String.class));
        assertEquals(40,backup.path("close").intValue()); assertEquals(100,backup.path("volume").longValue());
        assertEquals("2026-10-17 01:02:03",backup.path("createdAt").asText());
        assertEquals("2026-10-17 04:05:06",backup.path("updatedAt").asText());
    }
}
