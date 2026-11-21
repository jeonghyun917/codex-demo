package com.kingyurina.demo.stock;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Explicit offline import; no provider requests or canonical-history writes. */
@Component
public class StockHistoricalEvidenceImportBatchRunner implements ApplicationRunner {
    private final String bundlePath;
    private final boolean exit;
    private final ObjectProvider<DataSource> dataSource;
    private final ObjectMapper mapper;
    private final ConfigurableApplicationContext context;
    public StockHistoricalEvidenceImportBatchRunner(
            @Value("${app.batch.historical-evidence.bundle:}") String bundlePath,
            @Value("${app.batch.historical-evidence.exit-on-complete:false}") boolean exit,
            ObjectProvider<DataSource> dataSource, ObjectMapper mapper, ConfigurableApplicationContext context) {
        this.bundlePath = bundlePath; this.exit = exit; this.dataSource = dataSource;
        this.mapper = mapper; this.context = context;
    }
    @Override public void run(ApplicationArguments args) throws Exception {
        if (bundlePath == null || bundlePath.isBlank()) return;
        int code = 0;
        try {
            var bundle = StockHistoricalEvidenceBundle.read(Path.of(bundlePath), mapper);
            DataSource db = dataSource.getIfAvailable();
            if (db == null) throw new IllegalStateException("Evidence import requires a database");
            importBundle(db, bundle, mapper);
            System.out.println("Historical evidence imported: sources=" + bundle.sources().size()
                    + ", events=" + bundle.events().size() + ", coverage=" + bundle.coverage()
                    + "; canonical prices and PIT membership unchanged");
        } catch (Exception ex) {
            code = 1;
            throw ex;
        } finally {
            if (exit) {
                int result = code;
                new Thread(() -> System.exit(SpringApplication.exit(context, () -> result))).start();
            }
        }
    }
    static void importBundle(DataSource db, StockHistoricalEvidenceBundle bundle, ObjectMapper mapper) throws Exception {
        try (Connection connection = db.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var manifest = connection.prepareStatement("""
                        INSERT INTO stock_historical_evidence_bundle (bundle_sha256,local_path,coverage_claim,manifest_json)
                        VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE bundle_sha256=VALUES(bundle_sha256)
                        """)) {
                    manifest.setString(1, bundle.manifest().sha256()); manifest.setString(2, bundle.manifest().path());
                    manifest.setString(3, bundle.coverage()); manifest.setString(4, bundle.manifest().json());
                    manifest.executeUpdate();
                }
                try (var lookup = connection.prepareStatement("""
                        SELECT source_url,publisher,publication_date,content_sha256,coverage_claim
                        FROM stock_historical_evidence_source WHERE source_key=?
                        """); var insert = connection.prepareStatement("""
                        INSERT INTO stock_historical_evidence_source
                        (source_key,source_url,publisher,publication_date,retrieved_at_utc,content_sha256,local_path,coverage_claim)
                        VALUES (?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE source_key=VALUES(source_key)
                        """)) {
                    for (var source : bundle.sources()) {
                        lookup.setString(1, source.key());
                        try (var old = lookup.executeQuery()) {
                            if (old.next() && (!java.util.Objects.equals(old.getString(1), source.url())
                                    || !java.util.Objects.equals(old.getString(2), source.publisher())
                                    || !java.util.Objects.equals(old.getString(3), source.publishedDate())
                                    || !java.util.Objects.equals(old.getString(4), source.sha256())
                                    || !java.util.Objects.equals(old.getString(5), bundle.coverage()))) {
                                throw new IllegalArgumentException("Conflicting previously imported source: " + source.key());
                            }
                        }
                        // Same content may be fetched again or relocated; keep its first retrieval/path.
                        // Every subsequent full manifest is preserved separately, including new locations.
                        insert.setString(1, source.key()); insert.setString(2, source.url());
                        insert.setString(3, source.publisher());
                        insert.setDate(4, source.publishedDate() == null ? null : Date.valueOf(source.publishedDate()));
                        insert.setTimestamp(5, Timestamp.valueOf(Instant.parse(source.retrievedAt()).atOffset(ZoneOffset.UTC).toLocalDateTime()));
                        insert.setString(6, source.sha256()); insert.setString(7, source.path());
                        insert.setString(8, bundle.coverage()); insert.addBatch();
                    }
                    insert.executeBatch();
                }
                try (var lookup = connection.prepareStatement(
                            "SELECT evidence_json FROM stock_historical_evidence_event WHERE event_key=?");
                        var insert = connection.prepareStatement("""
                            INSERT INTO stock_historical_evidence_event
                            (event_key,source_key,symbol,index_code,action_kind,effective_date,date_basis,market_timing,evidence_json)
                            VALUES (?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE event_key=VALUES(event_key)
                            """)) {
                    for (var event : bundle.events()) {
                        String json = mapper.writeValueAsString(event);
                        lookup.setString(1, event.key());
                        try (var old = lookup.executeQuery()) {
                            if (old.next() && !mapper.readTree(old.getString(1)).equals(mapper.readTree(json))) {
                                throw new IllegalArgumentException("Conflicting previously imported event: " + event.key());
                            }
                        }
                        insert.setString(1, event.key()); insert.setString(2, event.sourceKey());
                        insert.setString(3, event.symbol()); insert.setString(4, event.indexCode());
                        insert.setString(5, event.action()); insert.setDate(6, Date.valueOf(event.effectiveDate()));
                        insert.setString(7, event.dateBasis()); insert.setString(8, event.timing());
                        insert.setString(9, json); insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            }
        }
    }
}
