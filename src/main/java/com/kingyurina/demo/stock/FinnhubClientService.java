package com.kingyurina.demo.stock;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

import org.springframework.stereotype.Service;

@Service
public class FinnhubClientService {

    private final FinnhubProperties properties;
    private final HttpClient httpClient;

    public FinnhubClientService(FinnhubProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public FinnhubResponse get(String endpoint, Map<String, String> queryParams) {
        if (!properties.hasApiKey()) {
            return new FinnhubResponse(0, null, "FINNHUB_API_KEY is not configured.");
        }

        Map<String, String> params = new LinkedHashMap<>(queryParams);
        params.put("token", properties.apiKey());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.baseUrl() + endpoint + "?" + encode(params)))
                .timeout(Duration.ofSeconds(12))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new FinnhubResponse(response.statusCode(), response.body(), null);
        } catch (IOException ex) {
            return new FinnhubResponse(0, null, ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new FinnhubResponse(0, null, ex.getMessage());
        }
    }

    private static String encode(Map<String, String> params) {
        StringJoiner joiner = new StringJoiner("&");
        params.forEach((key, value) -> joiner.add(urlEncode(key) + "=" + urlEncode(value)));
        return joiner.toString();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
