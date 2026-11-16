package com.kingyurina.demo.stock;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockCacheService {

    private final ObjectProvider<CompanyProfileMapper> profileMapper;
    private final ObjectProvider<StockQuoteMapper> quoteMapper;
    private final ObjectProvider<StockMetricMapper> metricMapper;
    private final ObjectProvider<CompanyNewsMapper> newsMapper;
    private final ObjectProvider<StockRecommendationTrendMapper> recommendationMapper;
    private final ObjectProvider<StockEpsSurpriseMapper> epsSurpriseMapper;
    private final ObjectProvider<StockCandleMapper> candleMapper;
    private final ObjectProvider<StockSignalLatestMapper> signalLatestMapper;
    private final ObjectProvider<StockDataQualityMapper> dataQualityMapper;
    private final ObjectProvider<SecFinancialStandardMapper> secFinancialStandardMapper;
    private final ObjectProvider<Institution13fMapper> institution13fMapper;
    private final ObjectProvider<StockSymbolMapper> symbolMapper;
    private final ObjectProvider<ApiCallLogMapper> apiCallLogMapper;
    private StockSourceFinancialService sourceFinancialService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSourceFinancialService(StockSourceFinancialService service) { this.sourceFinancialService=service; }

    public StockCacheService(ObjectProvider<CompanyProfileMapper> profileMapper,
            ObjectProvider<StockQuoteMapper> quoteMapper,
            ObjectProvider<StockMetricMapper> metricMapper,
            ObjectProvider<CompanyNewsMapper> newsMapper,
            ObjectProvider<StockRecommendationTrendMapper> recommendationMapper,
            ObjectProvider<StockEpsSurpriseMapper> epsSurpriseMapper,
            ObjectProvider<StockCandleMapper> candleMapper,
            ObjectProvider<StockSignalLatestMapper> signalLatestMapper,
            ObjectProvider<StockDataQualityMapper> dataQualityMapper,
            ObjectProvider<SecFinancialStandardMapper> secFinancialStandardMapper,
            ObjectProvider<Institution13fMapper> institution13fMapper,
            ObjectProvider<StockSymbolMapper> symbolMapper,
            ObjectProvider<ApiCallLogMapper> apiCallLogMapper) {
        this.profileMapper = profileMapper;
        this.quoteMapper = quoteMapper;
        this.metricMapper = metricMapper;
        this.newsMapper = newsMapper;
        this.recommendationMapper = recommendationMapper;
        this.epsSurpriseMapper = epsSurpriseMapper;
        this.candleMapper = candleMapper;
        this.signalLatestMapper = signalLatestMapper;
        this.dataQualityMapper = dataQualityMapper;
        this.secFinancialStandardMapper = secFinancialStandardMapper;
        this.institution13fMapper = institution13fMapper;
        this.symbolMapper = symbolMapper;
        this.apiCallLogMapper = apiCallLogMapper;
    }

    public boolean databaseEnabled() {
        return profileMapper.getIfAvailable() != null;
    }

    public StockQuoteCache findQuote(String symbol) {
        StockQuoteMapper mapper = quoteMapper.getIfAvailable();
        return mapper == null ? null : mapper.findBySymbol(symbol);
    }

    public void saveQuote(StockQuoteCache quote) {
        StockQuoteMapper mapper = quoteMapper.getIfAvailable();
        if (mapper != null) {
            mapper.upsert(quote);
        }
    }

    public CompanyProfile findProfile(String symbol) {
        CompanyProfileMapper mapper = profileMapper.getIfAvailable();
        return mapper == null ? null : mapper.findBySymbol(symbol);
    }

    public List<StockPeerComparison> findPeersByIndustry(String industry, int limit) {
        CompanyProfileMapper mapper = profileMapper.getIfAvailable();
        return mapper == null || industry == null ? List.of() : mapper.findPeersByIndustry(industry, limit);
    }

    public void saveProfile(CompanyProfile profile) {
        CompanyProfileMapper mapper = profileMapper.getIfAvailable();
        if (mapper != null) {
            mapper.upsert(profile);
        }
        StockSymbolMapper stockSymbolMapper = symbolMapper.getIfAvailable();
        if (stockSymbolMapper != null) {
            stockSymbolMapper.upsertSymbol(profile.getSymbol(), profile.getName(), profile.getExchange(),
                    profile.getCurrency(), "PROFILE");
        }
    }

    public StockMetricSnapshot findLatestMetric(String symbol) {
        StockMetricMapper mapper = metricMapper.getIfAvailable();
        return mapper == null ? null : mapper.findLatestBySymbol(symbol);
    }

    public void saveMetric(StockMetricSnapshot metric) {
        StockMetricMapper mapper = metricMapper.getIfAvailable();
        if (mapper != null) {
            mapper.upsert(metric);
        }
    }

    public LocalDateTime findLatestNewsFetchedAt(String symbol) {
        CompanyNewsMapper mapper = newsMapper.getIfAvailable();
        return mapper == null ? null : mapper.findLatestFetchedAt(symbol);
    }

    public List<CompanyNews> findRecentNews(String symbol, int limit) {
        CompanyNewsMapper mapper = newsMapper.getIfAvailable();
        return mapper == null ? List.of() : mapper.findRecentBySymbol(symbol, limit);
    }

    public void saveNews(List<CompanyNews> news) {
        CompanyNewsMapper mapper = newsMapper.getIfAvailable();
        if (mapper != null && !news.isEmpty()) {
            mapper.upsertBatch(news);
        }
    }

    public LocalDateTime findLatestRecommendationFetchedAt(String symbol) {
        StockRecommendationTrendMapper mapper = recommendationMapper.getIfAvailable();
        return mapper == null ? null : mapper.findLatestFetchedAt(symbol);
    }

    public List<StockRecommendationTrend> findRecentRecommendations(String symbol, int limit) {
        StockRecommendationTrendMapper mapper = recommendationMapper.getIfAvailable();
        return mapper == null ? List.of() : mapper.findRecentBySymbol(symbol, limit);
    }

    public void saveRecommendations(List<StockRecommendationTrend> recommendations) {
        StockRecommendationTrendMapper mapper = recommendationMapper.getIfAvailable();
        if (mapper != null && !recommendations.isEmpty()) {
            mapper.upsertBatch(recommendations);
        }
    }

    public LocalDateTime findLatestEpsSurpriseFetchedAt(String symbol) {
        StockEpsSurpriseMapper mapper = epsSurpriseMapper.getIfAvailable();
        return mapper == null ? null : mapper.findLatestFetchedAt(symbol);
    }

    public List<StockEpsSurprise> findRecentEpsSurprises(String symbol, int limit) {
        StockEpsSurpriseMapper mapper = epsSurpriseMapper.getIfAvailable();
        return mapper == null ? List.of() : mapper.findRecentBySymbol(symbol, limit);
    }

    public void saveEpsSurprises(List<StockEpsSurprise> surprises) {
        StockEpsSurpriseMapper mapper = epsSurpriseMapper.getIfAvailable();
        if (mapper != null && !surprises.isEmpty()) {
            mapper.upsertBatch(surprises);
        }
    }

    public LocalDateTime findLatestCandleUpdatedAt(String symbol) {
        StockCandleMapper mapper = candleMapper.getIfAvailable();
        return mapper == null ? null : mapper.findLatestUpdatedAt(symbol);
    }

    public List<StockCandleDaily> findRecentCandles(String symbol, int limit) {
        StockCandleMapper mapper = candleMapper.getIfAvailable();
        return mapper == null ? List.of() : mapper.findRecentBySymbol(symbol, limit);
    }

    @Transactional
    public CandleSaveResult saveCandles(List<StockCandleDaily> candles) {
        StockCandleMapper mapper = candleMapper.getIfAvailable();
        if (candles == null || candles.isEmpty() || mapper == null) {
            return new CandleSaveResult(candles == null ? 0 : candles.size(), 0, 0,
                    candles == null ? 0 : candles.size(), null, "NO_PERSISTENCE");
        }
        List<StockCandleDaily> sourceRows = candles.stream().filter(Objects::nonNull)
                .filter(c -> c.getSymbol() != null && !c.getSymbol().isBlank() && c.getTradeDate() != null
                        && c.getOpenPrice() != null && c.getHighPrice() != null && c.getLowPrice() != null
                        && c.getClosePrice() != null).toList();
        if (!sourceRows.isEmpty()) { mapper.upsertSourceBatch(sourceRows); }
        List<StockCandleDaily> valid = sourceRows.stream().filter(StockCacheService::isCanonicalCandleSource)
                .filter(StockCacheService::hasValidCandlePrices).toList();
        int accepted = 0;
        LocalDate latest = null;
        boolean quarantined = false;
        for (var entry : valid.stream().collect(Collectors.groupingBy(StockCandleDaily::getSymbol)).entrySet()) {
            mapper.lockCanonicalSymbol(entry.getKey());
            List<StockCandleDaily> existing = mapper.findRecentBySymbol(entry.getKey(), Integer.MAX_VALUE);
            String source = existing.isEmpty() ? entry.getValue().stream()
                    .map(StockCandleDaily::getSource).filter("yahoo-chart"::equalsIgnoreCase).findFirst()
                    .orElse(entry.getValue().getFirst().getSource()) : existing.getFirst().getSource();
            List<StockCandleDaily> selected = entry.getValue().stream()
                    .filter(c -> Objects.equals(source, c.getSource())).toList();
            Map<LocalDate, StockCandleDaily> byDate = new LinkedHashMap<>();
            selected.forEach(c -> byDate.put(c.getTradeDate(), c));
            boolean compatible = !selected.isEmpty() && existing.stream().allMatch(old ->
                    Objects.equals(source, old.getSource()) && (!byDate.containsKey(old.getTradeDate())
                            || samePrices(old, byDate.get(old.getTradeDate()))));
            // Appends must overlap the previous endpoint. An unverified rebase/source switch stays source-only.
            if (!existing.isEmpty() && selected.stream().anyMatch(c -> c.getTradeDate().isAfter(existing.getFirst().getTradeDate()))) {
                compatible &= byDate.containsKey(existing.getFirst().getTradeDate());
            }
            if (compatible) {
                List<StockCandleDaily> canonical = List.copyOf(byDate.values());
                mapper.upsertBatch(canonical);
                accepted += canonical.size();
                LocalDate max = canonical.stream().map(StockCandleDaily::getTradeDate).max(LocalDate::compareTo).orElse(null);
                if (latest == null || max != null && max.isAfter(latest)) { latest = max; }
            } else {
                quarantined = true;
            }
        }
        return new CandleSaveResult(candles.size(), sourceRows.size(), accepted, candles.size() - accepted,
                latest, quarantined ? "QUARANTINED_BASIS_CHANGE" : accepted > 0 ? "PERSISTED" : "NO_VALID_CANONICAL_ROWS");
    }

    public record CandleSaveResult(int receivedRows, int sourceRows, int canonicalRows, int rejectedRows,
            LocalDate latestMarketDate, String status) { }

    private static boolean samePrices(StockCandleDaily left, StockCandleDaily right) {
        return equalPrice(left.getOpenPrice(), right.getOpenPrice()) && equalPrice(left.getHighPrice(), right.getHighPrice())
                && equalPrice(left.getLowPrice(), right.getLowPrice()) && equalPrice(left.getClosePrice(), right.getClosePrice());
    }

    private static boolean equalPrice(java.math.BigDecimal left, java.math.BigDecimal right) {
        // Both source/canonical SQL columns are DECIMAL(24,6); compare the actual persisted values.
        return left != null && right != null && left.setScale(6, java.math.RoundingMode.HALF_UP)
                .compareTo(right.setScale(6, java.math.RoundingMode.HALF_UP)) == 0;
    }

    private static boolean isCanonicalCandleSource(StockCandleDaily candle) {
        if (candle == null || candle.getSource() == null) {
            return false;
        }
        return "yahoo-chart".equalsIgnoreCase(candle.getSource())
                || "stooq".equalsIgnoreCase(candle.getSource());
    }

    private static boolean hasValidCandlePrices(StockCandleDaily candle) {
        var open = candle.getOpenPrice();
        var high = candle.getHighPrice();
        var low = candle.getLowPrice();
        var close = candle.getClosePrice();
        return open != null && high != null && low != null && close != null
                && open.signum() > 0 && high.signum() > 0 && low.signum() > 0 && close.signum() > 0
                && high.compareTo(open.max(low).max(close)) >= 0
                && low.compareTo(open.min(high).min(close)) <= 0
                && (candle.getVolume() == null || candle.getVolume() >= 0);
    }

    public StockSignalLatest findLatestSignal(String symbol) {
        StockSignalLatestMapper mapper = signalLatestMapper.getIfAvailable();
        return mapper == null ? null : mapper.findBySymbol(symbol);
    }

    public StockDataQualityLatest findLatestDataQuality(String symbol) {
        StockDataQualityMapper mapper = dataQualityMapper.getIfAvailable();
        return mapper == null ? null : mapper.findBySymbol(symbol);
    }

    public SecFinancialStandard findLatestSecAnnualFinancial(String symbol) {
        SecFinancialStandardMapper mapper = secFinancialStandardMapper.getIfAvailable();
        return mapper == null ? null : mapper.findLatestAnnual(symbol);
    }

    public SecFinancialStandard findPreviousSecAnnualFinancial(String symbol, java.time.LocalDate endDate) {
        SecFinancialStandardMapper mapper = secFinancialStandardMapper.getIfAvailable();
        return mapper == null || endDate == null ? null : mapper.findPreviousAnnual(symbol, endDate);
    }

    public SecFinancialStandard findLatestSecQuarterFinancial(String symbol) {
        SecFinancialStandardMapper mapper = secFinancialStandardMapper.getIfAvailable();
        var legacy=mapper == null ? null : mapper.findLatestQuarter(symbol);
        return sourceFinancialService==null?legacy:sourceFinancialService.resolveQuarter(symbol,
            LocalDate.now(java.time.ZoneId.of("America/New_York")),null,legacy,false);
    }

    public SecFinancialStandard findPreviousSecQuarterFinancial(String symbol, java.time.LocalDate endDate) {
        if(endDate==null)return null;
        SecFinancialStandardMapper mapper = secFinancialStandardMapper.getIfAvailable();
        var legacy=mapper == null ? null : mapper.findPreviousQuarter(symbol, endDate);
        return sourceFinancialService==null?legacy:sourceFinancialService.resolveQuarter(symbol,
            LocalDate.now(java.time.ZoneId.of("America/New_York")),endDate,legacy,false);
    }

    public StockInstitutionFlow findLatestInstitutionFlow(String symbol) {
        Institution13fMapper mapper = institution13fMapper.getIfAvailable();
        return mapper == null ? null : mapper.findLatestFlow(symbol);
    }

    public List<StockInstitutionFlow> findRecentInstitutionFlows(String symbol, int limit) {
        Institution13fMapper mapper = institution13fMapper.getIfAvailable();
        return mapper == null ? List.of() : mapper.findRecentFlows(symbol, limit);
    }

    public void saveLatestSignal(StockSignalLatest signal) {
        StockSignalLatestMapper mapper = signalLatestMapper.getIfAvailable();
        if (mapper != null) {
            mapper.upsert(signal);
        }
    }

    public void saveLatestDataQuality(StockDataQualityLatest quality) {
        StockDataQualityMapper mapper = dataQualityMapper.getIfAvailable();
        if (mapper != null) {
            mapper.upsert(quality);
        }
    }

    public void logApiCall(String provider, String endpoint, String symbol, FinnhubResponse response) {
        ApiCallLogMapper mapper = apiCallLogMapper.getIfAvailable();
        if (mapper != null) {
            mapper.insert(new ApiCallLog(provider, endpoint, symbol,
                    response.statusCode() == 0 ? null : response.statusCode(), response.errorMessage()));
        }
    }

    public LocalDateTime findLatestSuccessfulCallAt(String endpoint, String symbol) {
        ApiCallLogMapper mapper = apiCallLogMapper.getIfAvailable();
        return mapper == null ? null : mapper.findLatestSuccessfulCallAt(endpoint, symbol);
    }
}
