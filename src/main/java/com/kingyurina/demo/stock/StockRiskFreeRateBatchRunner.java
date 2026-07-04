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
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class StockRiskFreeRateBatchRunner implements ApplicationRunner {

    private static final String DEFAULT_SERIES_CODE = "DGS3MO";

    private final boolean enabled;
    private final boolean exitOnComplete;
    private final boolean fetchFred;
    private final boolean fallbackOnFailure;
    private final String indexCode;
    private final String seriesCode;
    private final String fredUrl;
    private final String csvPath;
    private final int years;
    private final String fromDate;
    private final String toDate;
    private final double fixedAnnualRatePct;
    private final StockPortfolioBacktestService stockPortfolioBacktestService;
    private final ConfigurableApplicationContext applicationContext;
    private final HttpClient httpClient;

    public StockRiskFreeRateBatchRunner(
            @Value("${app.batch.risk-free-rate.enabled:false}") boolean enabled,
            @Value("${app.batch.risk-free-rate.exit-on-complete:false}") boolean exitOnComplete,
            @Value("${app.batch.risk-free-rate.fetch-fred:false}") boolean fetchFred,
            @Value("${app.batch.risk-free-rate.fallback-on-failure:true}") boolean fallbackOnFailure,
            @Value("${app.batch.risk-free-rate.index-code:SP500}") String indexCode,
            @Value("${app.batch.risk-free-rate.series-code:DGS3MO}") String seriesCode,
            @Value("${app.batch.risk-free-rate.fred-url:}") String fredUrl,
            @Value("${app.batch.risk-free-rate.csv-path:}") String csvPath,
            @Value("${app.batch.risk-free-rate.years:5}") int years,
            @Value("${app.batch.risk-free-rate.from-date:}") String fromDate,
            @Value("${app.batch.risk-free-rate.to-date:}") String toDate,
            @Value("${quant.portfolio.risk-free-rate-pct:0}") double fixedAnnualRatePct,
            StockPortfolioBacktestService stockPortfolioBacktestService,
            ConfigurableApplicationContext applicationContext) {
        this.enabled = enabled;
        this.exitOnComplete = exitOnComplete;
        this.fetchFred = fetchFred;
        this.fallbackOnFailure = fallbackOnFailure;
        this.indexCode = indexCode;
        this.seriesCode = seriesCode == null || seriesCode.isBlank() ? DEFAULT_SERIES_CODE : seriesCode.trim();
        this.fredUrl = fredUrl;
        this.csvPath = csvPath;
        this.years = years;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.fixedAnnualRatePct = fixedAnnualRatePct;
        this.stockPortfolioBacktestService = stockPortfolioBacktestService;
        this.applicationContext = applicationContext;
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
            LocalDate end = parseDate(toDate, LocalDate.now());
            LocalDate start = parseDate(fromDate, end.minusYears(Math.max(1, years)));
            StockPortfolioBacktestService.RiskFreeRateRefreshResult result;
            if (fetchFred) {
                try {
                    result = stockPortfolioBacktestService.refreshRiskFreeRateSnapshots(indexCode,
                            parseCsv(fetchFredCsv(), start, end, "FRED_CSV"));
                } catch (RuntimeException ex) {
                    if (!fallbackOnFailure) {
                        throw ex;
                    }
                    System.out.println("FRED risk-free refresh failed; writing CONFIG_FALLBACK rows instead. cause="
                            + ex.getMessage());
                    result = refreshFixed(start, end, "CONFIG_FALLBACK");
                }
            } else if (csvPath != null && !csvPath.isBlank()) {
                result = stockPortfolioBacktestService.refreshRiskFreeRateSnapshots(indexCode,
                        parseCsv(Files.readString(Path.of(csvPath.trim()), StandardCharsets.UTF_8), start, end,
                                "CSV_IMPORT"));
            } else {
                result = refreshFixed(start, end, "CONFIG_FIXED");
            }
            System.out.println("Stock risk-free rate refresh indexCode=" + indexCode
                    + ", seriesCode=" + seriesCode
                    + ", from=" + start
                    + ", to=" + end
                    + ", requested=" + result.requestedRows()
                    + ", saved=" + result.savedRows()
                    + ", minDate=" + result.minDate()
                    + ", maxDate=" + result.maxDate());
        } catch (RuntimeException | IOException ex) {
            exitCode = 1;
            System.err.println("Stock risk-free rate refresh failed. fetchFred=" + fetchFred
                    + ", indexCode=" + indexCode
                    + ", seriesCode=" + seriesCode
                    + ", cause=" + ex.getMessage());
            throw new IllegalStateException("Stock risk-free rate refresh failed: " + ex.getMessage(), ex);
        } finally {
            if (exitOnComplete) {
                int code = exitCode;
                Thread shutdown = new Thread(() -> System.exit(SpringApplication.exit(applicationContext, () -> code)));
                shutdown.setDaemon(false);
                shutdown.start();
            }
        }
    }

    private StockPortfolioBacktestService.RiskFreeRateRefreshResult refreshFixed(LocalDate start, LocalDate end,
            String source) {
        return stockPortfolioBacktestService.refreshFixedRiskFreeRateSnapshots(indexCode, seriesCode,
                start, end, fixedAnnualRatePct, source);
    }

    private String fetchFredCsv() {
        String url = fredUrl == null || fredUrl.isBlank()
                ? "https://fred.stlouisfed.org/graph/fredgraph.csv?id="
                        + URLEncoder.encode(seriesCode, StandardCharsets.UTF_8)
                : fredUrl.trim();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(45))
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("User-Agent", "king-yurina-quant-ai/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("FRED CSV returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException ex) {
            throw new IllegalStateException("FRED CSV request failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FRED CSV request interrupted.", ex);
        }
    }

    private List<StockRiskFreeRateSnapshot> parseCsv(String content, LocalDate start, LocalDate end, String source) {
        List<StockRiskFreeRateSnapshot> rows = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return rows;
        }
        String[] lines = content.split("\\R");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.isBlank()) {
                continue;
            }
            String[] columns = line.replace("\uFEFF", "").split(",", -1);
            if (columns.length < 2 || columns[0].isBlank() || columns[1].isBlank() || ".".equals(columns[1].trim())) {
                continue;
            }
            LocalDate date = LocalDate.parse(columns[0].trim());
            if (date.isBefore(start) || date.isAfter(end)) {
                continue;
            }
            StockRiskFreeRateSnapshot snapshot = new StockRiskFreeRateSnapshot();
            snapshot.setIndexCode(indexCode);
            snapshot.setSeriesCode(seriesCode);
            snapshot.setRateDate(date);
            snapshot.setAnnualRatePct(new BigDecimal(columns[1].trim()));
            snapshot.setSource(source);
            rows.add(snapshot);
        }
        return rows;
    }

    private static LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return LocalDate.parse(value.trim());
    }
}
