package com.kingyurina.demo.stock;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class TossInvestClientService {

    private final TossInvestProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private String accessToken;
    private Instant expiresAt = Instant.EPOCH;

    public TossInvestClientService(TossInvestProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .build();
    }

    public TossInvestResponse get(String endpoint, Map<String, String> queryParams) {
        if (!properties.configured()) {
            return new TossInvestResponse(0, null, "TOSS_API_KEY or TOSS_SECRET_KEY is not configured.");
        }

        String token = accessToken();
        if (token == null || token.isBlank()) {
            return new TossInvestResponse(0, null, "Failed to issue Toss access token.");
        }

        String query = queryParams == null || queryParams.isEmpty() ? "" : "?" + encode(queryParams);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.baseUrl() + endpoint + query))
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new TossInvestResponse(response.statusCode(), response.body(), null);
        } catch (IOException ex) {
            return new TossInvestResponse(0, null, ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new TossInvestResponse(0, null, ex.getMessage());
        }
    }

    private synchronized String accessToken() {
        if (accessToken != null && Instant.now().isBefore(expiresAt.minusSeconds(60))) {
            return accessToken;
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "client_credentials");
        form.put("client_id", properties.apiKey());
        form.put("client_secret", properties.secretKey());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.baseUrl() + "/oauth2/token"))
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encode(form)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                accessToken = null;
                expiresAt = Instant.EPOCH;
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            accessToken = text(root, "access_token");
            long expiresIn = longValue(root, "expires_in", 3600L);
            expiresAt = Instant.now().plusSeconds(Math.max(60, expiresIn));
            return accessToken;
        } catch (IOException ex) {
            accessToken = null;
            expiresAt = Instant.EPOCH;
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            accessToken = null;
            expiresAt = Instant.EPOCH;
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static long longValue(JsonNode node, String field, long fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? fallback : value.asLong(fallback);
    }

    private static String encode(Map<String, String> params) {
        StringJoiner joiner = new StringJoiner("&");
        params.forEach((key, value) -> {
            if (value != null) {
                joiner.add(urlEncode(key) + "=" + urlEncode(value));
            }
        });
        return joiner.toString();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
