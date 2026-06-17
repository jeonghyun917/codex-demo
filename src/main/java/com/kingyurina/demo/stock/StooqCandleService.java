package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StooqCandleService {

    private static final Logger log = LoggerFactory.getLogger(StooqCandleService.class);
    private static final String PROVIDER = "stooq";
    private static final DateTimeFormatter STOOQ_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern VERIFY_PATTERN = Pattern.compile("const c=\"([^\"]+)\",d=(\\d+)");

    private final StooqProperties properties;
    private final StockCacheService cacheService;
    private final HttpClient httpClient;
    private final CookieManager cookieManager;

    public StooqCandleService(StooqProperties properties, StockCacheService cacheService) {
        this.properties = properties;
        this.cacheService = cacheService;
        this.cookieManager = new CookieManager();
        this.cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .cookieHandler(cookieManager)
                .build();
    }

    public SyncSummary syncSymbols(List<String> symbols, int years, long delayMillis) {
        int requested = 0;
        int success = 0;
        int fail = 0;
        int savedRows = 0;
        List<String> failedSymbols = new ArrayList<>();
        int resolvedYears = years <= 0 ? properties.defaultYears() : years;
        long resolvedDelay = delayMillis < 0 ? properties.requestDelayMillis() : delayMillis;
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusYears(resolvedYears);

        for (String symbol : symbols) {
            if (symbol == null || symbol.isBlank()) {
                continue;
            }
            requested++;
            try {
                List<StockCandleDaily> candles = fetch(symbol, from, to);
                if (candles.isEmpty()) {
                    fail++;
                    failedSymbols.add(symbol);
                } else {
                    cacheService.saveCandles(candles);
                    success++;
                    savedRows += candles.size();
                }
            } catch (RuntimeException ex) {
                fail++;
                failedSymbols.add(symbol);
                log.warn("Failed to sync Stooq candles for {}", symbol, ex);
            }
            sleep(resolvedDelay);
        }
        return new SyncSummary(requested, success, fail, savedRows, failedSymbols);
    }

    private List<StockCandleDaily> fetch(String symbol, LocalDate from, LocalDate to) {
        String normalized = normalizeSymbol(symbol);
        URI uri = URI.create(properties.baseUrl()
                + "?s=" + encode(normalized)
                + "&i=d"
                + "&d1=" + from.format(STOOQ_DATE)
                + "&d2=" + to.format(STOOQ_DATE));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "king-yurina-stock-research/0.1")
                .GET()
                .build();
        try {
            HttpResponse<String> response = sendWithVerification(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Stooq returned HTTP " + response.statusCode());
            }
            return parse(symbol, response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stooq request interrupted.", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Stooq request failed: " + ex.getMessage(), ex);
        }
    }

    private HttpResponse<String> sendWithVerification(HttpRequest request) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        for (int attempts = 0; attempts < 3 && requiresVerification(response.body()); attempts++) {
            solveVerification(response.body(), request.uri());
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
        return response;
    }

    private boolean requiresVerification(String body) {
        return body != null && body.contains("/__verify") && body.contains("crypto.subtle.digest");
    }

    private void solveVerification(String body, URI referer) throws Exception {
        Matcher matcher = VERIFY_PATTERN.matcher(body == null ? "" : body);
        if (!matcher.find()) {
            throw new IllegalStateException("Stooq verification challenge was not recognized.");
        }
        String challenge = matcher.group(1);
        int difficulty = Integer.parseInt(matcher.group(2));
        String nonce = solveNonce(challenge, difficulty);
        String form = "c=" + encode(challenge) + "&n=" + nonce;
        HttpRequest verifyRequest = HttpRequest.newBuilder(URI.create("https://stooq.com/__verify"))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "king-yurina-stock-research/0.1")
                .header("Origin", "https://stooq.com")
                .header("Referer", referer.toString())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> verifyResponse = httpClient.send(verifyRequest, HttpResponse.BodyHandlers.ofString());
        if (verifyResponse.statusCode() < 200 || verifyResponse.statusCode() >= 300) {
            throw new IllegalStateException("Stooq verification failed with HTTP " + verifyResponse.statusCode());
        }
    }

    private static String solveNonce(String challenge, int difficulty) throws Exception {
        String prefix = "0".repeat(Math.max(0, difficulty));
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (long nonce = 0; nonce < 20_000_000L; nonce++) {
            byte[] hash = digest.digest((challenge + nonce).getBytes(StandardCharsets.UTF_8));
            if (toHex(hash).startsWith(prefix)) {
                return String.valueOf(nonce);
            }
        }
        throw new IllegalStateException("Stooq verification nonce was not found.");
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private List<StockCandleDaily> parse(String symbol, String csv) {
        if (csv == null || csv.isBlank() || csv.toLowerCase(Locale.ROOT).contains("no data")) {
            return List.of();
        }
        String[] lines = csv.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        if (lines.length < 2 || !lines[0].toLowerCase(Locale.ROOT).startsWith("date,")) {
            String preview = csv.length() > 160 ? csv.substring(0, 160) : csv;
            log.warn("Stooq response for {} was not CSV. Preview={}", symbol, preview);
            return List.of();
        }
        List<StockCandleDaily> candles = new ArrayList<>();
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            if (line == null || line.isBlank()) {
                continue;
            }
            String[] values = line.split(",", -1);
            if (values.length < 6) {
                continue;
            }
            StockCandleDaily candle = new StockCandleDaily();
            candle.setSymbol(symbol.trim().toUpperCase(Locale.ROOT));
            candle.setTradeDate(LocalDate.parse(values[0].trim()));
            candle.setOpenPrice(decimal(values[1]));
            candle.setHighPrice(decimal(values[2]));
            candle.setLowPrice(decimal(values[3]));
            candle.setClosePrice(decimal(values[4]));
            candle.setVolume(longValue(values[5]));
            candle.setSource(PROVIDER);
            if (candle.getOpenPrice() != null && candle.getHighPrice() != null
                    && candle.getLowPrice() != null && candle.getClosePrice() != null) {
                candles.add(candle);
            }
        }
        return candles;
    }

    private static String normalizeSymbol(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".us")) {
            return normalized;
        }
        return normalized + ".us";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank() || "N/D".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return new BigDecimal(value.trim());
    }

    private static Long longValue(String value) {
        if (value == null || value.isBlank() || "N/D".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return Long.parseLong(value.trim());
    }

    private static void sleep(long delayMillis) {
        if (delayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stooq batch interrupted.", ex);
        }
    }

    public record SyncSummary(int requested, int success, int fail, int savedRows, List<String> failedSymbols) {
    }
}
