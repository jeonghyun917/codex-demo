package com.kingyurina.demo.stock;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

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

    public void saveCandles(List<StockCandleDaily> candles) {
        StockCandleMapper mapper = candleMapper.getIfAvailable();
        if (mapper != null && !candles.isEmpty()) {
            mapper.upsertBatch(candles);
        }
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
        return mapper == null ? null : mapper.findLatestQuarter(symbol);
    }

    public SecFinancialStandard findPreviousSecQuarterFinancial(String symbol, java.time.LocalDate endDate) {
        SecFinancialStandardMapper mapper = secFinancialStandardMapper.getIfAvailable();
        return mapper == null || endDate == null ? null : mapper.findPreviousQuarter(symbol, endDate);
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
