package com.kingyurina.demo.stock;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Objects;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Complete-snapshot offline reconciliation; ordinary collection continues to quarantine revisions. */
public final class StockCandleReconciliation {
    public record Change(StockCandleDaily before, StockCandleDaily after) {}
    public record Plan(String symbol, int comparedRows, List<Change> changes) {}
    public static Plan plan(String symbol, List<StockCandleDaily> before, List<StockCandleDaily> snapshot) {
        require(before != null && !before.isEmpty(), "No existing canonical history: " + symbol);
        Map<LocalDate, StockCandleDaily> incoming = new TreeMap<>();
        for (var row : snapshot) {
            require(row != null && symbol.equals(row.getSymbol()) && row.getTradeDate() != null
                    && "yahoo-chart".equals(row.getSource()), "Mixed snapshot identity/source");
            var normalized = normalized(row);
            require(valid(normalized), "Invalid OHLC/volume: " + symbol + " " + row.getTradeDate());
            require(incoming.putIfAbsent(row.getTradeDate(), normalized) == null, "Duplicate snapshot date");
        }
        var changes = new ArrayList<Change>();
        var seen = new java.util.HashSet<LocalDate>();
        for (var row : before) {
            require(symbol.equals(row.getSymbol()) && "yahoo-chart".equals(row.getSource())
                    && row.getTradeDate() != null && seen.add(row.getTradeDate()), "Invalid canonical identity/source/date");
            var next = incoming.get(row.getTradeDate());
            require(next != null, "Snapshot does not cover canonical date: " + symbol + " " + row.getTradeDate());
            if (!same(row, next)) changes.add(new Change(row, next));
        }
        return new Plan(symbol, before.size(), List.copyOf(changes));
    }
    public static List<StockCandleDaily> readSnapshot(StockHistoricalEvidenceBundle.Source source,
            String symbol, ObjectMapper mapper) {
        try {
            URI uri = URI.create(source.url());
            String host = uri.getHost();
            require("query1.finance.yahoo.com".equals(host) || "query2.finance.yahoo.com".equals(host),
                    "Reconciliation requires an archived Yahoo chart source");
            require(uri.getPath().equals("/v8/finance/chart/" + symbol.replace('.', '-')), "Source URL symbol mismatch");
            byte[] bytes = Files.readAllBytes(Path.of(source.path()));
            require(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(source.sha256()),
                    "Snapshot hash changed");
            JsonNode root = mapper.readTree(bytes).path("chart");
            require(root.path("error").isNull() && root.path("result").isArray()
                    && root.path("result").size() == 1, "Invalid chart response");
            JsonNode chart = root.path("result").get(0);
            require(symbol.replace('.', '-').equals(chart.path("meta").path("symbol").asText()), "Provider symbol mismatch");
            require("USD".equals(chart.path("meta").path("currency").asText())
                    && "EQUITY".equals(chart.path("meta").path("instrumentType").asText()), "Unexpected currency/instrument");
            ZoneId zone = ZoneId.of(chart.path("meta").path("exchangeTimezoneName").asText());
            JsonNode dates = chart.path("timestamp");
            JsonNode quotes = chart.path("indicators").path("quote").path(0);
            require(dates.isArray() && !dates.isEmpty(), "Empty price snapshot");
            for (String field : List.of("open", "high", "low", "close", "volume")) {
                require(quotes.path(field).isArray() && quotes.path(field).size() == dates.size(), "Incomplete quote arrays");
            }
            var rows = new ArrayList<StockCandleDaily>();
            for (int i = 0; i < dates.size(); i++) {
                LocalDate date = Instant.ofEpochSecond(dates.get(i).longValue()).atZone(zone).toLocalDate();
                if (!date.isBefore(LocalDate.now(zone))) continue;
                var row = new StockCandleDaily(); row.setSymbol(symbol); row.setTradeDate(date); row.setSource("yahoo-chart");
                // quote.close is split-adjusted OHLC. adjclose has a different dividend basis and is never substituted.
                row.setOpenPrice(decimal(quotes.path("open").get(i))); row.setHighPrice(decimal(quotes.path("high").get(i)));
                row.setLowPrice(decimal(quotes.path("low").get(i))); row.setClosePrice(decimal(quotes.path("close").get(i)));
                JsonNode volume = quotes.path("volume").get(i);
                require(volume.isNull() || volume.isIntegralNumber() && volume.canConvertToLong(), "Invalid volume");
                row.setVolume(volume.isNull() ? null : volume.longValue()); rows.add(row);
            }
            return List.copyOf(rows);
        } catch (IllegalArgumentException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalArgumentException("Snapshot validation failed: " + symbol, ex); }
    }
    private static BigDecimal decimal(JsonNode value) {
        require(value != null && value.isNumber(), "Missing/non-numeric OHLC value");
        return value.decimalValue();
    }
    private static StockCandleDaily normalized(StockCandleDaily row) {
        var n = new StockCandleDaily(); n.setSymbol(row.getSymbol()); n.setTradeDate(row.getTradeDate());
        n.setOpenPrice(rounded(row.getOpenPrice())); n.setHighPrice(rounded(row.getHighPrice()));
        n.setLowPrice(rounded(row.getLowPrice())); n.setClosePrice(rounded(row.getClosePrice()));
        n.setVolume(row.getVolume()); n.setSource(row.getSource()); return n;
    }
    private static BigDecimal rounded(BigDecimal n) { return n == null ? null : n.setScale(6, RoundingMode.HALF_UP); }
    private static boolean valid(StockCandleDaily row) {
        BigDecimal o=row.getOpenPrice(), h=row.getHighPrice(), l=row.getLowPrice(), c=row.getClosePrice();
        return o!=null && h!=null && l!=null && c!=null && o.signum()>0 && l.signum()>0 && c.signum()>0
                && h.compareTo(o.max(c).max(l))>=0 && l.compareTo(o.min(c).min(h))<=0
                && (row.getVolume()==null || row.getVolume()>=0);
    }
    private static boolean same(StockCandleDaily a, StockCandleDaily b) {
        return Objects.equals(rounded(a.getOpenPrice()), b.getOpenPrice()) && Objects.equals(rounded(a.getHighPrice()), b.getHighPrice())
                && Objects.equals(rounded(a.getLowPrice()), b.getLowPrice()) && Objects.equals(rounded(a.getClosePrice()), b.getClosePrice())
                && Objects.equals(a.getVolume(), b.getVolume()) && Objects.equals(a.getSource(), b.getSource());
    }
    private static void require(boolean test, String message) { if (!test) throw new IllegalArgumentException(message); }
}
