package com.kingyurina.demo.stock;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import tools.jackson.databind.ObjectMapper;

/** Opt-in real-MariaDB tests. Session-local temporary tables shadow staging tables; persistent rows are untouched. */
@EnabledIfEnvironmentVariable(named = "QUANT_EVIDENCE_DB_TEST", matches = "true")
class StockHistoricalEvidenceImportTest {
    @TempDir Path directory;
    private Connection connection;
    private SingleConnectionDataSource source;
    private JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();
    @BeforeEach void connect() throws Exception {
        connection = DriverManager.getConnection("jdbc:mariadb://127.0.0.1:3306/king_yurina", "root", "");
        source = new SingleConnectionDataSource(connection, true);
        jdbc = new JdbcTemplate(source);
        for (String table : List.of("stock_historical_evidence_bundle", "stock_historical_evidence_source", "stock_historical_evidence_event")) {
            String ddl = jdbc.queryForObject("SHOW CREATE TABLE " + table, (rs, row) -> rs.getString(2));
            jdbc.execute(ddl.replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE")
                    .replaceAll("(?m)^\\s*CONSTRAINT [^\\n]+\\n", "").replaceAll(",\\s*\\)", ")"));
        }
    }
    @AfterEach void close() throws Exception { if (connection != null) connection.close(); }
    private StockHistoricalEvidenceBundle fixture() throws Exception {
        Files.writeString(directory.resolve("raw"), "abc");
        Path path = directory.resolve("bundle.json");
        Files.writeString(path, """
                {"coverage":"PARTIAL_OFFICIAL_EVENTS","sources":[{"id":"s","url":"https://example.org/APH",
                "publisher":"Issuer","publishedDate":"2026-08-06","retrievedAt":"2026-10-17T00:00:00Z",
                "sha256":"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad","path":"raw"}],
                "events":[{"sourceId":"s","symbol":"APH","action":"DIVIDEND","effectiveDate":"2026-10-14",
                "dateBasis":"PAYMENT_DATE","amount":"0.125","currency":"USD","amountBasis":"POST_SPLIT_PER_SHARE"}]}
                """);
        return StockHistoricalEvidenceBundle.read(path, mapper);
    }
    private void save(StockHistoricalEvidenceBundle bundle) throws Exception {
        StockHistoricalEvidenceImportBatchRunner.importBundle(source, bundle, mapper);
    }
    @Test void replayIsIdempotentAndPreservesExactEventSemantics() throws Exception {
        var bundle = fixture(); save(bundle); save(bundle);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM stock_historical_evidence_event", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM stock_historical_evidence_source", Integer.class));
        assertEquals(bundle.manifest().json(), jdbc.queryForObject("SELECT manifest_json FROM stock_historical_evidence_bundle", String.class));
        var payload = mapper.readTree(jdbc.queryForObject("SELECT evidence_json FROM stock_historical_evidence_event", String.class));
        assertEquals("0.125", payload.path("amount").asText());
        assertEquals("PAYMENT_DATE", payload.path("dateBasis").asText());
    }
    @Test void conflictingEventRollsBackPreviouslyInsertedNewSources() throws Exception {
        var bundle = fixture(); save(bundle);
        var original = bundle.sources().getFirst();
        var sources = new ArrayList<>(bundle.sources());
        sources.add(new StockHistoricalEvidenceBundle.Source("b".repeat(64), "https://example.org/new", "Issuer",
                original.publishedDate(), original.retrievedAt(), original.sha256(), original.path()));
        var e = bundle.events().getFirst();
        var changed = new StockHistoricalEvidenceBundle.Event(e.key(), e.sourceKey(), e.indexCode(), e.symbol(),
                e.action(), e.effectiveDate(), e.timing(), e.dateBasis(), e.numerator(), e.denominator(),
                "999", e.currency(), e.amountBasis(), e.ratioBasis());
        assertThrows(IllegalArgumentException.class,
                () -> save(new StockHistoricalEvidenceBundle(sources, List.of(changed), bundle.coverage(), bundle.manifest())));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM stock_historical_evidence_source", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM stock_historical_evidence_event", Integer.class));
    }
    @Test void changedPublicationDateCannotSilentlyReuseSameDocumentIdentity() throws Exception {
        var bundle = fixture(); save(bundle);
        var s = bundle.sources().getFirst();
        var changed = new StockHistoricalEvidenceBundle.Source(s.key(), s.url(), s.publisher(), "2026-08-07",
                s.retrievedAt(), s.sha256(), s.path());
        assertThrows(IllegalArgumentException.class,
                () -> save(new StockHistoricalEvidenceBundle(List.of(changed), bundle.events(), bundle.coverage(), bundle.manifest())));
        assertEquals("2026-08-06", jdbc.queryForObject(
                "SELECT CAST(publication_date AS CHAR) FROM stock_historical_evidence_source", String.class));
    }
}
