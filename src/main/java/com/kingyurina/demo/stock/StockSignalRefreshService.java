package com.kingyurina.demo.stock;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class StockSignalRefreshService {

    private static final Logger log = LoggerFactory.getLogger(StockSignalRefreshService.class);
    private static final List<String> QUANT_INDEX_CODES = List.of("SP500", "NASDAQ100", "DOW30");

    private final ObjectProvider<IndexConstituentMapper> indexConstituentMapper;
    private final ObjectProvider<StockSignalSnapshotMapper> stockSignalSnapshotMapper;
    private final StockSignalService stockSignalService;
    private final StockCacheService stockCacheService;
    private final StockBacktestService stockBacktestService;
    private final StockPortfolioBacktestService stockPortfolioBacktestService;
    private final StockMacroFeatureService stockMacroFeatureService;
    private final StockExpectedReturnV8Service stockExpectedReturnV8Service;
    private final StockExpectedReturnV9Service stockExpectedReturnV9Service;
    private final StockDashboardViewSnapshotService stockDashboardViewSnapshotService;

    public StockSignalRefreshService(ObjectProvider<IndexConstituentMapper> indexConstituentMapper,
            ObjectProvider<StockSignalSnapshotMapper> stockSignalSnapshotMapper,
            StockSignalService stockSignalService, StockCacheService stockCacheService,
            StockBacktestService stockBacktestService, StockPortfolioBacktestService stockPortfolioBacktestService,
            StockMacroFeatureService stockMacroFeatureService, StockExpectedReturnV8Service stockExpectedReturnV8Service,
            StockExpectedReturnV9Service stockExpectedReturnV9Service,
            StockDashboardViewSnapshotService stockDashboardViewSnapshotService) {
        this.indexConstituentMapper = indexConstituentMapper;
        this.stockSignalSnapshotMapper = stockSignalSnapshotMapper;
        this.stockSignalService = stockSignalService;
        this.stockCacheService = stockCacheService;
        this.stockBacktestService = stockBacktestService;
        this.stockPortfolioBacktestService = stockPortfolioBacktestService;
        this.stockMacroFeatureService = stockMacroFeatureService;
        this.stockExpectedReturnV8Service = stockExpectedReturnV8Service;
        this.stockExpectedReturnV9Service = stockExpectedReturnV9Service;
        this.stockDashboardViewSnapshotService = stockDashboardViewSnapshotService;
    }

    public RefreshResult recalculateIndexLatest(String indexCode) {
        IndexConstituentMapper mapper = indexConstituentMapper.getIfAvailable();
        if (mapper == null) {
            return new RefreshResult(0, 0, 0);
        }
        return recalculateSymbols(mapper.findCurrentSymbols(indexCode));
    }

    public RefreshResult recalculateSymbol(String symbol) {
        return recalculateSymbols(List.of(symbol));
    }

    public RefreshResult recalculateSymbols(List<String> symbols) {
        int requested = 0;
        int success = 0;
        int fail = 0;
        List<StockSignalLatest> calculatedSignals = new ArrayList<>();
        for (String symbol : symbols) {
            if (symbol == null || symbol.isBlank()) {
                continue;
            }
            requested++;
            try {
                calculatedSignals.add(stockSignalService.buildLatest(symbol));
            } catch (RuntimeException ex) {
                fail++;
                log.warn("Failed to recalculate stock signal for {}", symbol, ex);
            }
        }
        applyQuantRankScores(calculatedSignals);
        for (StockSignalLatest signal : calculatedSignals) {
            try {
                stockCacheService.saveLatestSignal(signal);
                saveSnapshot(signal);
                success++;
            } catch (RuntimeException ex) {
                fail++;
                log.warn("Failed to save stock signal for {}", signal.getSymbol(), ex);
            }
        }
        try {
            stockBacktestService.refreshCompletedResults(5_000);
            stockPortfolioBacktestService.refreshRiskSnapshots("SP500", 100_000);
            stockPortfolioBacktestService.refreshLatestRiskSnapshots("SP500", 100_000);
            for (String indexCode : QUANT_INDEX_CODES) {
                stockPortfolioBacktestService.refreshLatestCovarianceSnapshot(indexCode, 120, 126);
                stockPortfolioBacktestService.refreshLatestExpectedReturnSnapshots(indexCode, 100_000, 300);
                stockPortfolioBacktestService.refreshLatestExpectedReturnV3Snapshots(indexCode, 100_000, 300);
                stockPortfolioBacktestService.refreshLatestExpectedReturnV4Snapshots(indexCode, 100_000, 300);
                stockPortfolioBacktestService.refreshLatestExpectedReturnV5Snapshots(indexCode, 100_000, 300);
                stockPortfolioBacktestService.refreshLatestExpectedReturnV6Snapshots(indexCode, 100_000, 300);
                stockPortfolioBacktestService.refreshLatestExpectedReturnV7Snapshots(indexCode, 100_000, 300);
                stockMacroFeatureService.refreshLatestMacroFeature(indexCode);
                stockExpectedReturnV8Service.refreshLatest(indexCode);
                stockExpectedReturnV9Service.refreshLatest(indexCode);
            }
            stockBacktestService.refreshBacktestViewSnapshot("SP500");
            stockPortfolioBacktestService.refreshPortfolioViewSnapshot("SP500");
            for (String dashboardIndexCode : QUANT_INDEX_CODES) {
                stockDashboardViewSnapshotService.refreshDashboardViewSnapshot(dashboardIndexCode);
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to refresh stock signal backtest, risk/covariance snapshot, live expected return, or materialized views.", ex);
        }
        return new RefreshResult(requested, success, fail);
    }

    private void saveSnapshot(StockSignalLatest signal) {
        StockSignalSnapshotMapper mapper = stockSignalSnapshotMapper.getIfAvailable();
        if (mapper == null) {
            return;
        }
        StockSignalSnapshot snapshot = new StockSignalSnapshot();
        snapshot.setSymbol(signal.getSymbol());
        snapshot.setSignalDate(signal.getCalculatedAt() == null ? LocalDate.now() : signal.getCalculatedAt().toLocalDate());
        snapshot.setSnapshotMode("LIVE");
        snapshot.setCalculatedAt(signal.getCalculatedAt());
        snapshot.setSignalVersion(signal.getSignalVersion());
        snapshot.setIntegratedScore(signal.getIntegratedScore());
        snapshot.setIntegratedLabel(signal.getIntegratedLabel());
        snapshot.setTone(signal.getTone());
        snapshot.setConfidence(signal.getConfidence());
        snapshot.setSummary(signal.getSummary());
        snapshot.setValuationScore(signal.getValuationScore());
        snapshot.setValuationLabel(signal.getValuationLabel());
        snapshot.setQualityScore(signal.getQualityScore());
        snapshot.setQualityLabel(signal.getQualityLabel());
        snapshot.setGrowthScore(signal.getGrowthScore());
        snapshot.setGrowthLabel(signal.getGrowthLabel());
        snapshot.setStabilityScore(signal.getStabilityScore());
        snapshot.setStabilityLabel(signal.getStabilityLabel());
        snapshot.setEarningsScore(signal.getEarningsScore());
        snapshot.setEarningsLabel(signal.getEarningsLabel());
        snapshot.setAnalystScore(signal.getAnalystScore());
        snapshot.setAnalystLabel(signal.getAnalystLabel());
        snapshot.setNewsScore(signal.getNewsScore());
        snapshot.setNewsLabel(signal.getNewsLabel());
        snapshot.setMomentumScore(signal.getMomentumScore());
        snapshot.setMomentumLabel(signal.getMomentumLabel());
        snapshot.setRiskScore(signal.getRiskScore());
        snapshot.setRiskLabel(signal.getRiskLabel());
        snapshot.setInstitutionScore(signal.getInstitutionScore());
        snapshot.setInstitutionLabel(signal.getInstitutionLabel());
        snapshot.setDataQualityScore(signal.getDataQualityScore());
        snapshot.setDataQualityExcludedMetricCount(signal.getDataQualityExcludedMetricCount());
        snapshot.setDataQualityIssueCount(signal.getDataQualityIssueCount());
        snapshot.setReasonsJson(signal.getReasonsJson());
        snapshot.setCardsJson(signal.getCardsJson());
        snapshot.setSourceFreshnessJson(signal.getSourceFreshnessJson());
        snapshot.setRawJson(signal.getRawJson());
        mapper.upsert(snapshot);
    }

    private void applyQuantRankScores(List<StockSignalLatest> signals) {
        if (signals.size() < 20) {
            return;
        }
        Map<String, Integer> valuationRanks = percentileRanks(signals, StockSignalLatest::getValuationScore);
        Map<String, Integer> qualityRanks = percentileRanks(signals, StockSignalLatest::getQualityScore);
        Map<String, Integer> growthRanks = percentileRanks(signals, StockSignalLatest::getGrowthScore);
        Map<String, Integer> stabilityRanks = percentileRanks(signals, StockSignalLatest::getStabilityScore);
        Map<String, Integer> earningsRanks = percentileRanks(signals, StockSignalLatest::getEarningsScore);
        Map<String, Integer> analystRanks = percentileRanks(signals, StockSignalLatest::getAnalystScore);
        Map<String, Integer> newsRanks = percentileRanks(signals, StockSignalLatest::getNewsScore);
        Map<String, Integer> momentumRanks = percentileRanks(signals, StockSignalLatest::getMomentumScore);
        Map<String, Integer> riskRanks = percentileRanks(signals, StockSignalLatest::getRiskScore);
        Map<String, Integer> institutionRanks = percentileRanks(signals, StockSignalLatest::getInstitutionScore);

        for (StockSignalLatest signal : signals) {
            String symbol = signal.getSymbol();
            int factorRankScore = Math.round((
                    rank(qualityRanks, symbol) * 17
                            + rank(valuationRanks, symbol) * 14
                            + rank(growthRanks, symbol) * 14
                            + rank(earningsRanks, symbol) * 13
                            + rank(stabilityRanks, symbol) * 9
                            + rank(momentumRanks, symbol) * 8
                            + rank(riskRanks, symbol) * 8
                            + rank(analystRanks, symbol) * 7
                            + rank(institutionRanks, symbol) * 6
                            + rank(newsRanks, symbol) * 4)
                    / 100.0f);
            int rawScore = valueOr(signal.getIntegratedScore(), 50);
            int score = Math.round(rawScore * 0.35f + factorRankScore * 0.65f);
            score = applyGuardrails(score, signal);
            signal.setIntegratedScore(score);
            signal.setIntegratedLabel(integratedLabel(score));
            signal.setTone(tone(score));
            signal.setSummary(signal.getIntegratedLabel()
                    + "입니다. 10개 핵심 Signal과 동일 수집 대상 내 factor ranking을 함께 반영한 Quant Signal v2 점수입니다. 확정 예측은 아닙니다.");
        }
    }

    private static Map<String, Integer> percentileRanks(List<StockSignalLatest> signals, ScoreReader reader) {
        List<StockSignalLatest> ranked = signals.stream()
                .filter(signal -> signal.getSymbol() != null && reader.score(signal) != null)
                .sorted(Comparator
                        .comparing((StockSignalLatest signal) -> reader.score(signal))
                        .thenComparing(StockSignalLatest::getSymbol))
                .toList();
        Map<String, Integer> ranks = new HashMap<>();
        if (ranked.isEmpty()) {
            return ranks;
        }
        if (ranked.size() == 1) {
            ranks.put(ranked.getFirst().getSymbol(), 50);
            return ranks;
        }
        for (int index = 0; index < ranked.size(); index++) {
            int percentile = Math.round(index * 100.0f / (ranked.size() - 1));
            ranks.put(ranked.get(index).getSymbol(), percentile);
        }
        return ranks;
    }

    private static int rank(Map<String, Integer> ranks, String symbol) {
        return valueOr(ranks.get(symbol), 50);
    }

    private static int applyGuardrails(int score, StockSignalLatest signal) {
        if (lte(signal.getQualityScore(), 35)) {
            score = Math.min(score, 58);
        }
        if (lte(signal.getEarningsScore(), 35) && lte(signal.getMomentumScore(), 45)) {
            score = Math.min(score, 60);
        }
        if (lte(signal.getValuationScore(), 30) && !gte(signal.getQualityScore(), 70)) {
            score = Math.min(score, 62);
        }
        if (lte(signal.getStabilityScore(), 30)) {
            score = Math.min(score, 60);
        }
        if (lte(signal.getRiskScore(), 30)) {
            score = Math.min(score, 64);
        }
        if (lte(signal.getAnalystScore(), 30)) {
            score = Math.min(score, 68);
        }
        if (lte(signal.getInstitutionScore(), 25)) {
            score = Math.min(score, 72);
        }
        if (signal.getDataQualityScore() != null && signal.getDataQualityScore() < 35) {
            score = Math.min(score, 55);
        } else if (signal.getDataQualityScore() != null && signal.getDataQualityScore() < 50) {
            score = Math.min(score, 60);
        }
        if (signal.getDataQualityExcludedMetricCount() != null && signal.getDataQualityExcludedMetricCount() >= 4) {
            score = Math.min(pullTowardNeutral(score, 8), 62);
        } else if (signal.getDataQualityExcludedMetricCount() != null
                && signal.getDataQualityExcludedMetricCount() >= 2) {
            score = pullTowardNeutral(score, 4);
        }
        return Math.max(0, Math.min(100, score));
    }

    private static String integratedLabel(int score) {
        if (score >= 70) {
            return "상향 우세";
        }
        if (score >= 55) {
            return "중립 우세";
        }
        if (score >= 40) {
            return "주의";
        }
        return "하향 리스크";
    }

    private static String tone(int score) {
        if (score >= 67) {
            return "positive";
        }
        if (score >= 52) {
            return "neutral";
        }
        if (score >= 40) {
            return "caution";
        }
        return "negative";
    }

    private static int valueOr(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static int pullTowardNeutral(int score, int amount) {
        if (score > 50) {
            return Math.max(50, score - amount);
        }
        if (score < 50) {
            return Math.min(50, score + amount);
        }
        return score;
    }

    private static boolean gte(Integer value, int threshold) {
        return value != null && value >= threshold;
    }

    private static boolean lte(Integer value, int threshold) {
        return value != null && value <= threshold;
    }

    private interface ScoreReader {
        Integer score(StockSignalLatest signal);
    }

    public record RefreshResult(int requested, int success, int fail) {
    }
}
