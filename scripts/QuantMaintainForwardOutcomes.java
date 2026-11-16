import com.kingyurina.demo.CodexDemoApplication;
import com.kingyurina.demo.stock.StockPortfolioBacktestService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Outcome-price proxy continuation only. Never generates signals or forecasts. */
public final class QuantMaintainForwardOutcomes {
    record Request(LocalDate completed, Set<LocalDate> known, List<String> indices) { }

    static Request validateRequest(JsonNode request, LocalDate todayNy) {
        LocalDate completed = LocalDate.parse(request.path("completedSession").asText());
        if (!completed.isBefore(todayNy)) throw new IllegalArgumentException("Only completed prior exchange dates");
        Set<LocalDate> known = new HashSet<>();
        request.path("knownSessions").forEach(n -> {
            if (!known.add(LocalDate.parse(n.asText()))) throw new IllegalArgumentException("Duplicate exchange date");
        });
        if (!known.contains(completed)) throw new IllegalArgumentException("Unknown exchange date");
        List<String> indices = new ArrayList<>();
        request.path("indices").forEach(n -> indices.add(n.asText()));
        if (indices.isEmpty() || new HashSet<>(indices).size() != indices.size()
                || !Set.of("DOW30", "SP500", "NASDAQ100").containsAll(indices))
            throw new IllegalArgumentException("Invalid benchmark identifiers");
        return new Request(completed, Set.copyOf(known), List.copyOf(indices));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected request and NEW result file");
        var json = new ObjectMapper();
        var request = json.readTree(Files.readString(Path.of(args[0])));
        var policy = validateRequest(request, LocalDate.now(ZoneId.of("America/New_York")));
        var completed = policy.completed();
        Path output = Path.of(args[1]);
        if (Files.exists(output)) throw new IllegalArgumentException("Existing result cannot be replaced");
        var known = policy.known();
        var indices = policy.indices();
        var app = new SpringApplication(CodexDemoApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        try (var context = app.run("--spring.profiles.active=mariadb", "--spring.sql.init.mode=never",
                "--app.signal.refresh.enabled=false", "--app.signal.refresh.run-on-startup=false")) {
            var jdbc = context.getBean(JdbcTemplate.class);
            var service = context.getBean(StockPortfolioBacktestService.class);
            Map<String, Object> results = new TreeMap<>();
            for (String index : indices) {
                LocalDate last = jdbc.queryForObject("SELECT MAX(trade_date) FROM stock_benchmark_return_series WHERE index_code=?",
                        LocalDate.class, index);
                if (last == null || !known.contains(last)) throw new IllegalStateException("Known stored benchmark anchor required");
                for (LocalDate day : jdbc.queryForList("SELECT DISTINCT trade_date FROM stock_candle_daily WHERE trade_date>? AND trade_date<=?",
                        LocalDate.class, last, completed)) {
                    if (!known.contains(day)) throw new IllegalStateException("Price contains an unknown exchange date");
                }
                results.put(index, service.refreshLatestMarketSnapshotsAndBenchmarks(index, completed));
                LocalDate after = jdbc.queryForObject("SELECT MAX(trade_date) FROM stock_benchmark_return_series WHERE index_code=?",
                        LocalDate.class, index);
                if (!completed.equals(after)) throw new IllegalStateException("Outcome benchmark did not reach requested close");
            }
            Files.writeString(output, json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                    "completedSession", completed.toString(), "results", results,
                    "forecastGeneration", false, "modelPromotion", false)), StandardOpenOption.CREATE_NEW);
        }
    }
}
