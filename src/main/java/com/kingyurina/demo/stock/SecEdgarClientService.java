package com.kingyurina.demo.stock;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.stereotype.Service;

@Service
public class SecEdgarClientService {

    private final SecEdgarProperties properties;
    private final HttpClient httpClient;

    public SecEdgarClientService(SecEdgarProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public FinnhubResponse companyTickers() {
        return get(properties.filesBaseUrl() + "/files/company_tickers.json");
    }

    public FinnhubResponse companyFacts(String cik) {
        return get(properties.baseUrl() + "/api/xbrl/companyfacts/CIK" + cik + ".json");
    }

    public FinnhubResponse submissions(String cik) {
        return get(properties.baseUrl() + "/submissions/CIK" + cik + ".json");
    }

    public FinnhubResponse filingIndex(String cik, String accessionNumber) {
        String pathCik = cik == null ? "" : cik.replaceFirst("^0+", "");
        String accessionPath = accessionNumber == null ? "" : accessionNumber.replace("-", "");
        return get(properties.filesBaseUrl() + "/Archives/edgar/data/" + pathCik + "/" + accessionPath + "/index.json");
    }

    public FinnhubResponse informationTable(String url) {
        return get(url);
    }

    private FinnhubResponse get(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", properties.userAgent())
                .header("Accept", "application/json")
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
}
