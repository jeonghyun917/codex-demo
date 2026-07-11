package com.kingyurina.demo.stock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class StockApiDataSourceService {

    private final ObjectProvider<StockBacktestMapper> stockBacktestMapper;

    public StockApiDataSourceService(ObjectProvider<StockBacktestMapper> stockBacktestMapper) {
        this.stockBacktestMapper = stockBacktestMapper;
    }

    public List<StockApiDataSourceView> build(String indexCode) {
        return build(indexCode, null);
    }

    public List<StockApiDataSourceView> build(String indexCode, StockQuantModelHealthView modelHealth) {
        StockQuantModelHealthView health = modelHealth;
        if (health == null) {
            StockBacktestMapper mapper = stockBacktestMapper.getIfAvailable();
            health = mapper == null ? null : new StockQuantModelHealthView(
                    "Model Health",
                    "",
                    List.of(),
                    List.of(),
                    mapper.findQuantOperationHealth().stream().map(this::toOperationRow).toList(),
                    List.of());
        }
        RuntimeContext context = RuntimeContext.from(health);
        List<StockApiDataSourceView> rows = new ArrayList<>();
        for (SourceDefinition definition : definitions()) {
            rows.add(toView(definition, context));
        }
        return rows;
    }

    private StockQuantModelHealthView.OperationRow toOperationRow(StockQuantOperationHealthMetric metric) {
        String operation = metric.getOperation() == null ? "-" : metric.getOperation();
        String status = metric.getStatus() == null ? "-" : metric.getStatus();
        String tone = status.toUpperCase(Locale.ROOT).contains("FAIL") ? "negative" : "positive";
        return new StockQuantModelHealthView.OperationRow(
                operation,
                status,
                tone,
                metric.getStartedAt() == null ? "-" : metric.getStartedAt().toLocalDate().toString(),
                metric.getFinishedAt() == null ? "-" : metric.getFinishedAt().toLocalDate().toString(),
                "-",
                Long.toString(metric.getFailCount() == null ? 0 : metric.getFailCount()),
                metric.getMessage() == null ? "-" : metric.getMessage());
    }

    private static StockApiDataSourceView toView(SourceDefinition definition, RuntimeContext context) {
        SourceRuntime runtime = context.runtime(definition.provider());
        return new StockApiDataSourceView(
                definition.provider(),
                definition.label(),
                definition.description(),
                definition.datasets(),
                runtime.latestUpdate(),
                runtime.latestData(),
                runtime.coverage(),
                runtime.status(),
                runtime.tone());
    }

    private static List<SourceDefinition> definitions() {
        return List.of(
                new SourceDefinition("YAHOO", "Yahoo Chart",
                        "Daily price, volume, ETF candles, momentum, risk, and benchmark inputs.",
                        List.of("Stock/ETF daily candles", "Benchmark returns", "Moving averages / RSI / volatility")),
                new SourceDefinition("FINNHUB", "Finnhub",
                        "Company profile, quote, valuation metrics, news, recommendations, and EPS surprise.",
                        List.of("Company profile / logo", "Quote / 52W / valuation metric", "News / recommendation / EPS")),
                new SourceDefinition("SEC", "SEC EDGAR",
                        "Filing-based financial statements and filed-at point-in-time finance validation.",
                        List.of("CIK mapping", "companyfacts XBRL", "Standard financial mapping")),
                new SourceDefinition("TOSS", "Toss",
                        "Shares outstanding plus supplemental price, orderbook, trade, FX, and calendar data.",
                        List.of("Shares outstanding", "Price / orderbook / trades", "FX / calendar / warnings")),
                new SourceDefinition("FRED", "FRED / ALFRED",
                        "Macro time series used for regime features and expected-return adjustments.",
                        List.of("Rates / CPI / unemployment / dollar / VIX", "Risk-free rate", "Macro regime")),
                new SourceDefinition("INSTITUTION_13F", "SEC 13F",
                        "Institutional holding flows used for the institution factor.",
                        List.of("Manager filing", "Holding detail", "Quarterly institution flow")),
                new SourceDefinition("INTERNAL_QUANT", "Internal Quant",
                        "Derived model outputs built from external data and stored snapshots.",
                        List.of("Signal / data quality", "Expected Return v9", "Risk / covariance / view snapshots")));
    }

    private static RuntimeContext emptyContext() {
        return new RuntimeContext(List.of(), List.of());
    }

    private record SourceDefinition(String provider, String label, String description, List<String> datasets) {
    }

    private record RuntimeContext(
            List<StockQuantModelHealthView.Row> rows,
            List<StockQuantModelHealthView.OperationRow> operations) {

        static RuntimeContext from(StockQuantModelHealthView health) {
            if (health == null) {
                return emptyContext();
            }
            return new RuntimeContext(
                    health.rows() == null ? List.of() : health.rows(),
                    health.operations() == null ? List.of() : health.operations());
        }

        SourceRuntime runtime(String provider) {
            return switch (provider) {
                case "YAHOO" -> fromOperations("Yahoo candle batch", "Yahoo");
                case "FINNHUB" -> fromOperations("Finnhub batch", "Finnhub");
                case "SEC" -> firstNonEmpty(
                        fromOperations("SEC EDGAR batch", "SEC"),
                        fromRows(List.of("Factor contribution"), "SEC"));
                case "TOSS" -> fromOperations("Toss batch", "Toss");
                case "FRED" -> fromRows(List.of("Risk-free rate", "Macro vintage", "Macro feature", "Macro regime"),
                        "Macro");
                case "INSTITUTION_13F" -> fromOperations("13F batch", "Institution");
                case "INTERNAL_QUANT" -> fromRows(List.of("Signal latest", "Data quality", "Expected return v9",
                        "Risk snapshot", "Covariance snapshot", "Portfolio view snapshot", "Backtest view snapshot",
                        "Quant view snapshot"), "Quant");
                default -> SourceRuntime.empty();
            };
        }

        private SourceRuntime fromOperations(String... keywords) {
            return operations.stream()
                    .filter(row -> containsAny(row.operation(), keywords))
                    .max(Comparator.comparing(StockQuantModelHealthView.OperationRow::finishedAt,
                            Comparator.nullsLast(String::compareTo)))
                    .map(row -> new SourceRuntime(
                            emptyToDash(row.finishedAt()),
                            "-",
                            row.successRate() + " / fail " + row.failures(),
                            normalizeStatus(row.status()),
                            normalizeTone(row.tone())))
                    .orElseGet(SourceRuntime::empty);
        }

        private SourceRuntime fromRows(List<String> layerNames, String fallbackKeyword) {
            List<StockQuantModelHealthView.Row> matched = rows.stream()
                    .filter(row -> layerNames.stream().anyMatch(layer -> layer.equalsIgnoreCase(row.layer()))
                            || containsAny(row.layer(), fallbackKeyword))
                    .toList();
            if (matched.isEmpty()) {
                return SourceRuntime.empty();
            }
            String latest = matched.stream()
                    .map(StockQuantModelHealthView.Row::latest)
                    .filter(value -> value != null && !value.isBlank() && !"-".equals(value))
                    .max(String::compareTo)
                    .orElse("-");
            String coverage = matched.stream()
                    .map(row -> row.layer() + " " + row.rows())
                    .limit(3)
                    .reduce((left, right) -> left + " / " + right)
                    .orElse("-");
            StockQuantModelHealthView.Row worst = matched.stream()
                    .min(Comparator.comparingInt(row -> toneRank(row.tone())))
                    .orElse(matched.get(0));
            return new SourceRuntime(latest, latest, coverage, normalizeStatus(worst.status()), normalizeTone(worst.tone()));
        }

        private SourceRuntime firstNonEmpty(SourceRuntime left, SourceRuntime right) {
            return left.isEmpty() ? right : left;
        }
    }

    private record SourceRuntime(String latestUpdate, String latestData, String coverage, String status, String tone) {
        static SourceRuntime empty() {
            return new SourceRuntime("-", "-", "Data check required", "Missing", "negative");
        }

        boolean isEmpty() {
            return "-".equals(latestUpdate) && "-".equals(latestData) && "negative".equals(tone);
        }
    }

    private static boolean containsAny(String value, String... keywords) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (keyword != null && lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static int toneRank(String tone) {
        return switch (normalizeTone(tone)) {
            case "negative" -> 0;
            case "warning" -> 1;
            case "positive" -> 3;
            default -> 2;
        };
    }

    private static String normalizeTone(String tone) {
        if (tone == null || tone.isBlank()) {
            return "neutral";
        }
        return tone.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeStatus(String status) {
        String value = Optional.ofNullable(status).orElse("").trim();
        if (value.isBlank() || "-".equals(value)) {
            return "Check";
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.contains("SUCCESS") || upper.contains("DONE") || upper.contains("OK") || "정상".equals(value)) {
            return "Fresh";
        }
        if (upper.contains("FAIL") || upper.contains("ERROR")
                || "누락".equals(value) || "실패".equals(value) || "위험".equals(value)) {
            return "Check";
        }
        return value;
    }

    private static String emptyToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
