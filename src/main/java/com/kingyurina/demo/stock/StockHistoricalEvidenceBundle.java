package com.kingyurina.demo.stock;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Offline evidence only: importing a bundle never certifies complete PIT coverage. */
public record StockHistoricalEvidenceBundle(List<Source> sources, List<Event> events, String coverage, Manifest manifest) {
    /** Original normalized manifest preserves provider warnings, announcement links, licenses and precision. */
    public record Manifest(String sha256, String path, String json) {}
    public record Source(String key, String url, String publisher, String publishedDate,
            String retrievedAt, String sha256, String path) {}
    public record Event(String key, String sourceKey, String indexCode, String symbol,
            String action, String effectiveDate, String timing, String dateBasis,
            String numerator, String denominator, String amount, String currency, String amountBasis, String ratioBasis) {}
    public static StockHistoricalEvidenceBundle read(Path path, ObjectMapper mapper) {
        try {
            Path root = path.toRealPath().getParent();
            byte[] manifestBytes = Files.readAllBytes(path);
            String manifestJson = new String(manifestBytes, StandardCharsets.UTF_8);
            JsonNode bundle = mapper.readTree(manifestJson);
            String coverage = required(bundle, "coverage", 80);
            require(Set.of("PARTIAL_OFFICIAL_EVENTS", "PARTIAL_OFFICIAL_CHANGE_EVENTS",
                    "PROVIDER_REPORTED_EVENTS").contains(coverage), "Unsupported evidence coverage claim");
            require(bundle.path("sources").isArray() && bundle.path("events").isArray(), "Missing source/event arrays");
            var sources = new ArrayList<Source>();
            var sourceIds = new HashMap<String, Source>();
            var sourceKeys = new HashSet<String>();
            for (JsonNode node : bundle.path("sources")) {
                String id = required(node, "id", 200);
                String url = required(node, "url", 4000);
                URI uri = URI.create(url);
                require("https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null,
                        "Evidence URL must be public HTTPS without credentials");
                String publisher = required(node, "publisher", 200);
                String published = optional(node, "publishedDate");
                Instant retrieved = Instant.parse(required(node, "retrievedAt", 60));
                require(!retrieved.isAfter(Instant.now().plusSeconds(300)), "Future retrieval timestamp");
                if (published != null) {
                    require(!LocalDate.parse(published).isAfter(retrieved.atZone(ZoneOffset.UTC).toLocalDate()),
                            "Publication after retrieval");
                }
                String digest = required(node, "sha256", 64).toLowerCase(java.util.Locale.ROOT);
                require(digest.matches("[0-9a-f]{64}"), "Invalid source hash");
                Path relative = Path.of(required(node, "path", 2000));
                Path raw = root.resolve(relative).normalize();
                require(!relative.isAbsolute() && raw.startsWith(root), "Source path escapes bundle directory");
                raw = raw.toRealPath();
                require(raw.startsWith(root) && Files.isRegularFile(raw), "Source path escapes bundle directory");
                require(digest.equals(hash(Files.readAllBytes(raw))), "Source hash mismatch: " + id);
                String key = hash((url + "\n" + digest).getBytes(StandardCharsets.UTF_8));
                Source source = new Source(key, url, publisher, published, retrieved.toString(), digest, raw.toString());
                require(sourceIds.putIfAbsent(id, source) == null, "Duplicate source id");
                require(sourceKeys.add(key), "Multiple source ids alias the same document");
                sources.add(source);
            }
            require(!sources.isEmpty(), "No sources");
            var events = new ArrayList<Event>();
            var eventKeys = new HashSet<String>();
            for (JsonNode node : bundle.path("events")) {
                Source source = sourceIds.get(required(node, "sourceId", 200));
                require(source != null, "Unknown event source");
                String announcement = optional(node, "announcementSourceId");
                require(announcement == null || sourceIds.containsKey(announcement), "Unknown announcement source");
                String symbol = required(node, "symbol", 32);
                require(symbol.matches("[A-Z0-9.^=-]+"), "Invalid symbol");
                String action = required(node, "action", 16);
                String date = required(node, "effectiveDate", 10);
                LocalDate.parse(date);
                String index = null, timing = null, basis = null, numerator = null, denominator = null;
                String amount = null, currency = null, amountBasis = null, ratioBasis = null;
                if (Set.of("ADD", "REMOVE").contains(action)) {
                    index = required(node, "indexCode", 16);
                    require(Set.of("SP500", "NASDAQ100", "DOW30").contains(index), "Unsupported index");
                    timing = required(node, "timing", 24);
                    require(Set.of("BEFORE_OPEN", "AFTER_CLOSE", "UNSPECIFIED").contains(timing), "Invalid market timing");
                    require(source.publishedDate() != null, "Membership event requires publication day");
                } else {
                    require(Set.of("SPLIT", "DIVIDEND").contains(action), "Unsupported corporate action");
                    basis = required(node, "dateBasis", 32);
                    require(Set.of("EX_DATE", "DISTRIBUTION_DATE", "PAYMENT_DATE", "RECORD_DATE").contains(basis),
                            "Unknown corporate-action date basis");
                    if ("SPLIT".equals(action)) {
                        numerator = positiveDecimal(node, "numerator");
                        denominator = positiveDecimal(node, "denominator");
                        ratioBasis = required(node, "ratioBasis", 80);
                        require(Set.of("OFFICIAL_SHARE_RATIO", "PROVIDER_ADJUSTMENT_FACTOR_UNVERIFIED").contains(ratioBasis),
                                "Unknown ratio basis");
                        require(!"PROVIDER_REPORTED_EVENTS".equals(coverage)
                                || "PROVIDER_ADJUSTMENT_FACTOR_UNVERIFIED".equals(ratioBasis),
                                "Provider factor cannot be certified as a share ratio");
                    } else {
                        amount = positiveDecimal(node, "amount");
                        currency = required(node, "currency", 3);
                        require(currency.matches("[A-Z]{3}"), "Invalid dividend currency");
                        amountBasis = required(node, "amountBasis", 80);
                    }
                }
                String identity = String.join("|", source.key(), symbol, action, date,
                        String.valueOf(index), String.valueOf(timing), String.valueOf(basis));
                String key = hash(identity.getBytes(StandardCharsets.UTF_8));
                require(eventKeys.add(key), "Duplicate or conflicting event key");
                events.add(new Event(key, source.key(), index, symbol, action, date, timing, basis,
                        numerator, denominator, amount, currency, amountBasis, ratioBasis));
            }
            return new StockHistoricalEvidenceBundle(List.copyOf(sources), List.copyOf(events), coverage,
                    new Manifest(hash(manifestBytes), path.toAbsolutePath().normalize().toString(), manifestJson));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid historical evidence bundle: " + ex.getMessage(), ex);
        }
    }

    private static String positiveDecimal(JsonNode node, String field) {
        BigDecimal value = new BigDecimal(required(node, field, 60)).stripTrailingZeros();
        require(value.signum() > 0 && value.precision() - value.scale() <= 18 && value.scale() <= 12,
                "Invalid positive amount/ratio: " + field);
        return value.toPlainString();
    }
    private static String optional(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText().trim();
    }
    private static String required(JsonNode node, String field, int limit) {
        String value = optional(node, field);
        require(value != null && !value.isBlank() && value.length() <= limit, "Missing/invalid " + field);
        return value;
    }
    private static String hash(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
