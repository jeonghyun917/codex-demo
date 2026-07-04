package com.kingyurina.demo.stock;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class StockMacroVintageBatchRunner implements ApplicationRunner {

    private static final String FRED_OBSERVATIONS_URL =
            "https://api.stlouisfed.org/fred/series/observations";
    private static final ZoneId FRED_REALTIME_ZONE = ZoneId.of("America/Chicago");

    private final boolean enabled;
    private final boolean exitOnComplete;
    private final boolean fetchFredApi;
    private final String indexCode;
    private final List<String> seriesCodes;
    private final String apiKey;
    private final String csvPath;
    private final int years;
    private final String fromDate;
    private final String toDate;
    private final ObjectProvider<StockBacktestMapper> mapperProvider;
    private final ConfigurableApplicationContext applicationContext;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public StockMacroVintageBatchRunner(
            @Value("${app.batch.macro-vintage.enabled:false}") boolean enabled,
            @Value("${app.batch.macro-vintage.exit-on-complete:false}") boolean exitOnComplete,
            @Value("${app.batch.macro-vintage.fetch-fred-api:false}") boolean fetchFredApi,
            @Value("${app.batch.macro-vintage.index-code:SP500}") String indexCode,
            @Value("${app.batch.macro-vintage.series-codes:DGS3MO,FEDFUNDS,CPIAUCSL,UNRATE}") String seriesCodes,
            @Value("${app.batch.macro-vintage.api-key:${FRED_API_KEY:}}") String apiKey,
            @Value("${app.batch.macro-vintage.csv-path:}") String csvPath,
            @Value("${app.batch.macro-vintage.years:5}") int years,
            @Value("${app.batch.macro-vintage.from-date:}") String fromDate,
            @Value("${app.batch.macro-vintage.to-date:}") String toDate,
            ObjectProvider<StockBacktestMapper> mapperProvider,
            ConfigurableApplicationContext applicationContext,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.exitOnComplete = exitOnComplete;
        this.fetchFredApi = fetchFredApi;
        this.indexCode = indexCode == null || indexCode.isBlank() ? "SP500" : indexCode.trim();
        this.seriesCodes = parseSeriesCodes(seriesCodes);
        this.apiKey = apiKey;
        this.csvPath = csvPath;
        this.years = years;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.mapperProvider = mapperProvider;
        this.applicationContext = applicationContext;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        int exitCode = 0;
        try {
            StockBacktestMapper mapper = mapperProvider.getIfAvailable();
            if (mapper == null) {
                throw new IllegalStateException("StockBacktestMapper is not available.");
            }
            LocalDate fredToday = LocalDate.now(FRED_REALTIME_ZONE);
            LocalDate end = parseDate(toDate, fetchFredApi ? fredToday : LocalDate.now());
            if (fetchFredApi && end.isAfter(fredToday)) {
                System.out.println("Clamping macro vintage realtime_end from " + end
                        + " to FRED date " + fredToday + ".");
                end = fredToday;
            }
            LocalDate start = parseDate(fromDate, end.minusYears(Math.max(1, years)));
            int saved = 0;
            int riskFreeSaved = 0;
            if (csvPath != null && !csvPath.isBlank()) {
                List<StockMacroVintageSnapshot> rows =
                        parseCsv(Files.readString(Path.of(csvPath.trim()), StandardCharsets.UTF_8));
                saved += saveAll(mapper, rows);
                riskFreeSaved += syncRiskFreeFromDgs3mo(mapper, rows);
            }
            if (fetchFredApi) {
                if (apiKey == null || apiKey.isBlank()) {
                    throw new IllegalStateException("FRED_API_KEY is required for macro vintage API fetch.");
                }
                for (String seriesCode : seriesCodes) {
                    List<StockMacroVintageSnapshot> rows = fetchSeries(seriesCode, start, end);
                    saved += saveAll(mapper, rows);
                    riskFreeSaved += syncRiskFreeFromDgs3mo(mapper, rows);
                }
            }
            System.out.println("Stock macro vintage refresh indexCode=" + indexCode
                    + ", series=" + seriesCodes
                    + ", from=" + start
                    + ", to=" + end
                    + ", saved=" + saved
                    + ", riskFreeSaved=" + riskFreeSaved);
        } catch (RuntimeException | IOException ex) {
            exitCode = 1;
            System.err.println("Stock macro vintage refresh failed. fetchFredApi=" + fetchFredApi
                    + ", indexCode=" + indexCode
                    + ", series=" + seriesCodes
                    + ", cause=" + ex.getMessage());
            ex.printStackTrace(System.err);
            throw new IllegalStateException("Stock macro vintage refresh failed: " + ex.getMessage(), ex);
        } finally {
            if (exitOnComplete) {
                int code = exitCode;
                Thread shutdown = new Thread(() -> System.exit(SpringApplication.exit(applicationContext, () -> code)));
                shutdown.setDaemon(false);
                shutdown.start();
            }
        }
    }

    private List<StockMacroVintageSnapshot> fetchSeries(String seriesCode, LocalDate start, LocalDate end) {
        String url = FRED_OBSERVATIONS_URL
                + "?series_id=" + encode(seriesCode)
                + "&api_key=" + encode(apiKey)
                + "&file_type=json"
                + "&observation_start=" + start
                + "&observation_end=" + end
                + "&realtime_start=" + start
                + "&realtime_end=" + end
                + "&limit=100000";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("User-Agent", "king-yurina-quant-ai/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("FRED observations returned HTTP " + response.statusCode()
                        + ", body=" + shortBody(response.body()));
            }
            return parseFredJson(seriesCode, response.body());
        } catch (IOException ex) {
            throw new IllegalStateException("FRED observations request failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FRED observations request interrupted.", ex);
        }
    }

    private List<StockMacroVintageSnapshot> parseFredJson(String seriesCode, String body) throws IOException {
        List<StockMacroVintageSnapshot> rows = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return rows;
        }
        JsonNode observations = objectMapper.readTree(body).path("observations");
        if (!observations.isArray()) {
            return rows;
        }
        for (JsonNode item : observations) {
            String value = text(item, "value");
            if (value == null || value.isBlank() || ".".equals(value)) {
                continue;
            }
            String date = text(item, "date");
            String realtimeStart = text(item, "realtime_start");
            String realtimeEnd = text(item, "realtime_end");
            if (date == null || realtimeStart == null || realtimeEnd == null) {
                continue;
            }
            StockMacroVintageSnapshot snapshot = new StockMacroVintageSnapshot();
            snapshot.setIndexCode(indexCode);
            snapshot.setSeriesCode(seriesCode.toUpperCase(Locale.ROOT));
            snapshot.setObservationDate(LocalDate.parse(date));
            snapshot.setRealtimeStart(LocalDate.parse(realtimeStart));
            snapshot.setRealtimeEnd(LocalDate.parse(realtimeEnd));
            snapshot.setValue(new BigDecimal(value));
            snapshot.setSource("FRED_API_VINTAGE");
            rows.add(snapshot);
        }
        return rows;
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private List<StockMacroVintageSnapshot> parseCsv(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String[] lines = content.split("\\R");
        if (lines.length <= 1) {
            return List.of();
        }
        List<String> headers = parseCsvLine(lines[0]).stream()
                .map(StockMacroVintageBatchRunner::normalizeHeader)
                .toList();
        List<StockMacroVintageSnapshot> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.isBlank()) {
                continue;
            }
            List<String> values = parseCsvLine(line);
            Map<String, String> row = new HashMap<>();
            for (int j = 0; j < headers.size() && j < values.size(); j++) {
                row.put(headers.get(j), values.get(j).trim());
            }
            StockMacroVintageSnapshot snapshot = fromCsvRow(row);
            if (snapshot != null) {
                rows.add(snapshot);
            }
        }
        return rows;
    }

    private StockMacroVintageSnapshot fromCsvRow(Map<String, String> row) {
        String seriesCode = value(row, "series_code", "series_id", "series");
        String observationDate = value(row, "observation_date", "date");
        String realtimeStart = value(row, "realtime_start", "vintage_date");
        String realtimeEnd = value(row, "realtime_end", "vintage_end");
        String rawValue = value(row, "value", "observation_value");
        if (seriesCode == null || observationDate == null || realtimeStart == null
                || rawValue == null || ".".equals(rawValue)) {
            return null;
        }
        StockMacroVintageSnapshot snapshot = new StockMacroVintageSnapshot();
        snapshot.setIndexCode(defaulted(value(row, "index_code", "index"), indexCode));
        snapshot.setSeriesCode(seriesCode.toUpperCase(Locale.ROOT));
        snapshot.setObservationDate(LocalDate.parse(observationDate));
        snapshot.setRealtimeStart(LocalDate.parse(realtimeStart));
        snapshot.setRealtimeEnd(realtimeEnd == null ? LocalDate.parse(realtimeStart) : LocalDate.parse(realtimeEnd));
        snapshot.setValue(new BigDecimal(rawValue.replace(",", "")));
        snapshot.setSource(defaulted(value(row, "source"), "CSV_IMPORT_MACRO_VINTAGE"));
        return snapshot;
    }

    private int saveAll(StockBacktestMapper mapper, List<StockMacroVintageSnapshot> rows) {
        int saved = 0;
        for (StockMacroVintageSnapshot row : rows) {
            mapper.upsertMacroVintageSnapshot(row);
            saved++;
        }
        return saved;
    }

    private int syncRiskFreeFromDgs3mo(StockBacktestMapper mapper, List<StockMacroVintageSnapshot> rows) {
        Map<LocalDate, StockMacroVintageSnapshot> latestByDate = new LinkedHashMap<>();
        for (StockMacroVintageSnapshot row : rows) {
            if (row == null || row.getValue() == null || row.getObservationDate() == null
                    || !"DGS3MO".equalsIgnoreCase(row.getSeriesCode())) {
                continue;
            }
            latestByDate.merge(row.getObservationDate(), row, StockMacroVintageBatchRunner::newerVintage);
        }
        int saved = 0;
        for (StockMacroVintageSnapshot row : latestByDate.values()) {
            StockRiskFreeRateSnapshot snapshot = new StockRiskFreeRateSnapshot();
            snapshot.setIndexCode(row.getIndexCode());
            snapshot.setSeriesCode(row.getSeriesCode());
            snapshot.setRateDate(row.getObservationDate());
            snapshot.setAnnualRatePct(row.getValue());
            snapshot.setSource("FRED_API_VINTAGE_DGS3MO");
            mapper.upsertRiskFreeRateSnapshot(snapshot);
            saved++;
        }
        return saved;
    }

    private static StockMacroVintageSnapshot newerVintage(StockMacroVintageSnapshot first,
            StockMacroVintageSnapshot second) {
        int realtimeEnd = nullSafeDate(first.getRealtimeEnd()).compareTo(nullSafeDate(second.getRealtimeEnd()));
        if (realtimeEnd < 0) {
            return second;
        }
        if (realtimeEnd > 0) {
            return first;
        }
        return nullSafeDate(first.getRealtimeStart()).compareTo(nullSafeDate(second.getRealtimeStart())) < 0
                ? second : first;
    }

    private static List<String> parseSeriesCodes(String value) {
        if (value == null || value.isBlank()) {
            return List.of("DGS3MO");
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .map(item -> item.toUpperCase(Locale.ROOT))
                .toList();
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private static String value(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = row.get(normalizeHeader(key));
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return LocalDate.parse(value.trim());
    }

    private static String defaulted(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String normalizeHeader(String value) {
        return value == null ? "" : value.replace("\uFEFF", "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace("-", "_")
                .replace(" ", "_");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String shortBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        return compact.length() > 500 ? compact.substring(0, 500) + "..." : compact;
    }

    private static LocalDate nullSafeDate(LocalDate value) {
        return value == null ? LocalDate.MIN : value;
    }
}
