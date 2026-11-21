import com.kingyurina.demo.CodexDemoApplication;
import com.kingyurina.demo.stock.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Explicit archived-chart append. Full existing OHLCV must match, including volume. */
public final class QuantAppendCompatibleCandles {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: <audit.json> dry-run|apply");
        boolean apply = "apply".equals(args[1]);
        if (!apply && !"dry-run".equals(args[1])) throw new IllegalArgumentException("Explicit mode required");
        var app = new SpringApplication(CodexDemoApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        try (var context = app.run("--spring.profiles.active=mariadb", "--spring.sql.init.mode=never",
                "--app.signal.refresh.enabled=false", "--app.signal.refresh.run-on-startup=false")) {
            var json = context.getBean(ObjectMapper.class);
            var audit = json.readTree(Files.readString(Path.of(args[0])));
            String symbol = audit.path("metadata").path("symbol").asText();
            var source = audit.path("source");
            var evidence = new StockHistoricalEvidenceBundle.Source("compatible-append-" + symbol + "-20261018",
                    source.path("url").asText(), "Yahoo Finance", null, source.path("retrievedAt").asText(),
                    source.path("sha256").asText(), source.path("path").asText());
            var snapshot = StockCandleReconciliation.readSnapshot(evidence, symbol, json);
            var candles = context.getBean(StockCandleMapper.class);
            var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            tx.execute(status -> {
                candles.lockCanonicalSymbol(symbol);
                var before = candles.findRecentBySymbol(symbol, Integer.MAX_VALUE);
                var plan = StockCandleReconciliation.plan(symbol, before, snapshot);
                if (!plan.changes().isEmpty()) throw new IllegalStateException("Existing history differs: explicit reconciliation needed");
                var endpoint = before.getFirst().getTradeDate();
                var append = snapshot.stream().filter(c -> c.getTradeDate().isAfter(endpoint)).toList();
                System.out.printf("COMPATIBLE_APPEND mode=%s symbol=%s compared=%d changes=0 appendRows=%d oldEnd=%s%n",
                        args[1], symbol, plan.comparedRows(), append.size(), endpoint);
                if (!apply || append.isEmpty()) return null;
                jdbc.update("INSERT INTO stock_historical_evidence_source "
                        + "(source_key,source_url,publisher,publication_date,retrieved_at_utc,content_sha256,local_path,coverage_claim) "
                        + "VALUES(?,?,?,NULL,?,?,?,?)", evidence.key(), evidence.url(), evidence.publisher(),
                        java.sql.Timestamp.valueOf(java.time.Instant.parse(evidence.retrievedAt())
                                .atOffset(java.time.ZoneOffset.UTC).toLocalDateTime()), evidence.sha256(),
                        evidence.path(), "COMPATIBLE_PRICE_HISTORY_APPEND_NOT_PIT");
                // Existing endpoint supplies the ordinary collector's mandatory overlap check.
                var suffix = snapshot.stream().filter(c -> !c.getTradeDate().isBefore(endpoint)).toList();
                var saved = context.getBean(StockCacheService.class).saveCandles(suffix);
                if (!"PERSISTED".equals(saved.status()) || saved.rejectedRows() != 0)
                    throw new IllegalStateException("Append rejected: " + saved);
                var after = candles.findRecentBySymbol(symbol, Integer.MAX_VALUE);
                if (after.size() != before.size() + append.size()
                        || !StockCandleReconciliation.plan(symbol, before, after).changes().isEmpty())
                    throw new IllegalStateException("Append changed existing OHLCV or count");
                System.out.println("COMPATIBLE_APPEND_APPLIED " + saved + " oldRowsUnchanged=" + before.size());
                return null;
            });
            if (apply) {
                var service = context.getBean(StockBacktestService.class);
                for (int i = 0; i < 3; i++) {
                    int saved = service.refreshCompletedResults(2500);
                    System.out.println("POST_APPEND_OUTCOMES " + saved);
                    if (saved == 0) break;
                }
            }
        }
    }
}
