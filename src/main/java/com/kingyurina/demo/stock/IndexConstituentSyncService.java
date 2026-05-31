package com.kingyurina.demo.stock;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class IndexConstituentSyncService {

    private static final Pattern ROW_PATTERN = Pattern.compile("<tr[^>]*>(.*?)</tr>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TABLE_PATTERN = Pattern.compile("<table[^>]*>(.*?)</table>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CELL_PATTERN = Pattern.compile("<t[dh][^>]*>(.*?)</t[dh]>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9.-]{0,9}$");
    private static final Set<String> HEADER_SYMBOLS = Set.of("SYMBOL", "TICKER", "SECURITY", "ADDED", "NETCHANGE");

    private final boolean sp500Enabled;
    private final boolean nasdaq100Enabled;
    private final boolean dow30Enabled;
    private final int staleHours;
    private final ObjectProvider<IndexConstituentMapper> indexConstituentMapper;
    private final ObjectProvider<StockSymbolMapper> stockSymbolMapper;
    private final ObjectProvider<TransactionTemplate> transactionTemplate;
    private final HttpClient httpClient;

    public IndexConstituentSyncService(
            @Value("${app.batch.index.sp500-sync-enabled:false}") boolean sp500Enabled,
            @Value("${app.batch.index.nasdaq100-sync-enabled:false}") boolean nasdaq100Enabled,
            @Value("${app.batch.index.dow30-sync-enabled:false}") boolean dow30Enabled,
            @Value("${app.batch.index.stale-hours:24}") int staleHours,
            ObjectProvider<IndexConstituentMapper> indexConstituentMapper,
            ObjectProvider<StockSymbolMapper> stockSymbolMapper,
            ObjectProvider<TransactionTemplate> transactionTemplate) {
        this.sp500Enabled = sp500Enabled;
        this.nasdaq100Enabled = nasdaq100Enabled;
        this.dow30Enabled = dow30Enabled;
        this.staleHours = staleHours;
        this.indexConstituentMapper = indexConstituentMapper;
        this.stockSymbolMapper = stockSymbolMapper;
        this.transactionTemplate = transactionTemplate;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public SyncSummary syncEnabledIfStale() {
        IndexConstituentMapper constituentMapper = indexConstituentMapper.getIfAvailable();
        StockSymbolMapper symbolMapper = stockSymbolMapper.getIfAvailable();
        if (constituentMapper == null || symbolMapper == null) {
            return new SyncSummary(List.of(SyncResult.skipped("ALL", "Index mappers are not available.")));
        }

        List<SyncResult> results = new ArrayList<>();
        for (IndexSpec spec : enabledSpecs()) {
            try {
                results.add(syncOneIfStale(spec, constituentMapper, symbolMapper));
            } catch (RuntimeException ex) {
                results.add(SyncResult.failed(spec.indexCode(), ex.getMessage()));
            }
        }
        if (results.isEmpty()) {
            results.add(SyncResult.skipped("ALL", "Index sync is disabled."));
        }
        return new SyncSummary(results);
    }

    private SyncResult syncOneIfStale(IndexSpec spec, IndexConstituentMapper constituentMapper,
            StockSymbolMapper symbolMapper) {
        LocalDateTime latestSeenAt = constituentMapper.findLatestSeenAt(spec.indexCode());
        if (latestSeenAt != null && latestSeenAt.isAfter(LocalDateTime.now().minusHours(staleHours))) {
            return SyncResult.skipped(spec.indexCode(), spec.indexCode() + " universe is fresh.");
        }

        List<IndexConstituent> constituents = fetchConstituents(spec);
        if (constituents.size() < spec.minimumCount()) {
            throw new IllegalStateException(spec.indexCode() + " source returned too few symbols: "
                    + constituents.size());
        }

        return runInTransaction(() -> {
            constituentMapper.markAllNotCurrent(spec.indexCode());
            for (IndexConstituent constituent : constituents) {
                constituentMapper.upsertCurrent(constituent);
                symbolMapper.upsertSymbol(constituent.getSymbol(), constituent.getName(), constituent.getExchange(),
                        "USD", "INDEX");
            }
            constituentMapper.markRemoved(spec.indexCode());

            return SyncResult.synced(spec.indexCode(), constituents.size(),
                    constituentMapper.countTotal(spec.indexCode()));
        });
    }

    private <T> T runInTransaction(Supplier<T> action) {
        TransactionTemplate template = transactionTemplate.getIfAvailable();
        if (template == null) {
            return action.get();
        }
        return template.execute(status -> action.get());
    }

    private List<IndexSpec> enabledSpecs() {
        List<IndexSpec> specs = new ArrayList<>();
        if (sp500Enabled) {
            specs.add(new IndexSpec("SP500", "https://en.wikipedia.org/wiki/List_of_S%26P_500_companies",
                    0, 1, -1, 2, 450, 550, 503, null, null));
        }
        if (nasdaq100Enabled) {
            specs.add(new IndexSpec("NASDAQ100", "https://en.wikipedia.org/wiki/Nasdaq-100",
                    0, 1, -1, 2, 90, 120, 102, "id=\"Current_components\"", "id=\"Component_changes\""));
        }
        if (dow30Enabled) {
            specs.add(new IndexSpec("DOW30", "https://en.wikipedia.org/wiki/Dow_Jones_Industrial_Average",
                    2, 0, 1, 3, 30, 30, 30, "id=\"Components\"", "id=\"Former_components\""));
        }
        return specs;
    }

    private List<IndexConstituent> fetchConstituents(IndexSpec spec) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(spec.sourceUrl()))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Mozilla/5.0 compatible; codex-demo-index-sync/1.0")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(spec.indexCode() + " source HTTP " + response.statusCode());
            }
            return parseHtml(spec, response.body());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to fetch " + spec.indexCode() + " source: " + ex.getMessage(),
                    ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while fetching " + spec.indexCode() + " source.", ex);
        }
    }

    private static List<IndexConstituent> parseHtml(IndexSpec spec, String html) {
        String scopedHtml = scopeHtml(spec, html);
        List<IndexConstituent> best = List.of();
        Matcher tableMatcher = TABLE_PATTERN.matcher(scopedHtml);
        while (tableMatcher.find()) {
            List<IndexConstituent> tableRows = parseRows(spec, tableMatcher.group(1));
            if (tableRows.size() >= spec.minimumCount() && tableRows.size() <= spec.maximumCount()
                    && isBetterCandidate(spec, tableRows, best)) {
                best = tableRows;
            }
        }
        if (!best.isEmpty()) {
            return best;
        }
        return parseRows(spec, scopedHtml);
    }

    private static String scopeHtml(IndexSpec spec, String html) {
        if (!StringUtils.hasText(spec.startMarker())) {
            return html;
        }
        int start = html.indexOf(spec.startMarker());
        if (start < 0) {
            return html;
        }
        int end = StringUtils.hasText(spec.endMarker()) ? html.indexOf(spec.endMarker(), start + 1) : -1;
        return end > start ? html.substring(start, end) : html.substring(start);
    }

    private static boolean isBetterCandidate(IndexSpec spec, List<IndexConstituent> candidate,
            List<IndexConstituent> currentBest) {
        if (currentBest.isEmpty()) {
            return true;
        }
        return Math.abs(candidate.size() - spec.targetCount()) < Math.abs(currentBest.size() - spec.targetCount());
    }

    private static List<IndexConstituent> parseRows(IndexSpec spec, String html) {
        List<IndexConstituent> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Matcher rowMatcher = ROW_PATTERN.matcher(html);
        while (rowMatcher.find()) {
            List<String> cells = cells(rowMatcher.group(1));
            int maxColumn = Math.max(Math.max(spec.symbolColumn(), spec.nameColumn()),
                    Math.max(spec.exchangeColumn(), spec.sectorColumn()));
            if (cells.size() <= maxColumn) {
                continue;
            }
            String symbol = normalizeSymbol(cells.get(spec.symbolColumn()));
            if (!StringUtils.hasText(symbol) || HEADER_SYMBOLS.contains(symbol)
                    || !SYMBOL_PATTERN.matcher(symbol).matches() || !seen.add(symbol)) {
                continue;
            }
            if (isHeaderRow(cells, spec)) {
                continue;
            }

            IndexConstituent constituent = new IndexConstituent();
            constituent.setIndexCode(spec.indexCode());
            constituent.setSymbol(symbol);
            constituent.setName(valueAt(cells, spec.nameColumn()));
            constituent.setExchange(valueAt(cells, spec.exchangeColumn()));
            constituent.setSector(valueAt(cells, spec.sectorColumn()));
            constituent.setCurrentMember(true);
            constituent.setSource(spec.sourceUrl());
            result.add(constituent);
        }
        return result;
    }

    private static List<String> cells(String rowHtml) {
        List<String> cells = new ArrayList<>();
        Matcher cellMatcher = CELL_PATTERN.matcher(rowHtml);
        while (cellMatcher.find()) {
            String value = TAG_PATTERN.matcher(cellMatcher.group(1)).replaceAll("");
            cells.add(htmlDecode(value).trim());
        }
        return cells;
    }

    private static String valueAt(List<String> cells, int index) {
        if (index < 0 || index >= cells.size()) {
            return null;
        }
        String value = cells.get(index);
        return StringUtils.hasText(value) ? value : null;
    }

    private static String normalizeSymbol(String symbol) {
        return symbol == null ? null : symbol.trim().replace('.', '-').toUpperCase();
    }

    private static boolean isHeaderRow(List<String> cells, IndexSpec spec) {
        String name = uppercase(valueAt(cells, spec.nameColumn()));
        String sector = uppercase(valueAt(cells, spec.sectorColumn()));
        String exchange = uppercase(valueAt(cells, spec.exchangeColumn()));
        return HEADER_SYMBOLS.contains(name) || HEADER_SYMBOLS.contains(sector) || HEADER_SYMBOLS.contains(exchange);
    }

    private static String uppercase(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private static String htmlDecode(String value) {
        return value.replace("&amp;", "&")
                .replace("&#39;", "'")
                .replace("&quot;", "\"")
                .replace("&nbsp;", " ")
                .replace("&#160;", " ");
    }

    private record IndexSpec(String indexCode, String sourceUrl, int symbolColumn, int nameColumn,
            int exchangeColumn, int sectorColumn, int minimumCount, int maximumCount, int targetCount,
            String startMarker, String endMarker) {
    }

    public record SyncSummary(List<SyncResult> results) {

        public boolean hasSynced() {
            return results.stream().anyMatch(SyncResult::synced);
        }

        public String message() {
            return results.stream()
                    .filter(result -> result.synced() || result.failed())
                    .map(SyncResult::message)
                    .reduce((left, right) -> left + " " + right)
                    .orElse(null);
        }
    }

    public record SyncResult(String indexCode, boolean synced, boolean skipped, boolean failed,
            int currentCount, int totalCount, String message) {

        static SyncResult synced(String indexCode, int currentCount, int totalCount) {
            return new SyncResult(indexCode, true, false, false, currentCount, totalCount,
                    indexCode + " current=" + currentCount + ", total=" + totalCount + ".");
        }

        static SyncResult skipped(String indexCode, String message) {
            return new SyncResult(indexCode, false, true, false, 0, 0, message);
        }

        static SyncResult failed(String indexCode, String message) {
            return new SyncResult(indexCode, false, false, true, 0, 0,
                    indexCode + " sync failed: " + message + ".");
        }
    }
}
