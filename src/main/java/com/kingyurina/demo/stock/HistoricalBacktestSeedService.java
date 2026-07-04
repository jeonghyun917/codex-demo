package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

@Service
public class HistoricalBacktestSeedService {

    static final String SNAPSHOT_MODE = "HISTORICAL_RECONSTRUCTED";
    static final String SIGNAL_VERSION = "quant-v2-hist";

    private static final int CANDLE_LOOKBACK = 260;
    private static final int MIN_CANDLES = 80;

    private final ObjectProvider<HistoricalBacktestSeedMapper> seedMapper;
    private final ObjectProvider<IndexConstituentMapper> indexConstituentMapper;
    private final ObjectProvider<StockSignalSnapshotMapper> snapshotMapper;
    private final StockBacktestService stockBacktestService;
    private final ObjectMapper objectMapper;

    public HistoricalBacktestSeedService(ObjectProvider<HistoricalBacktestSeedMapper> seedMapper,
            ObjectProvider<IndexConstituentMapper> indexConstituentMapper,
            ObjectProvider<StockSignalSnapshotMapper> snapshotMapper,
            StockBacktestService stockBacktestService,
            ObjectMapper objectMapper) {
        this.seedMapper = seedMapper;
        this.indexConstituentMapper = indexConstituentMapper;
        this.snapshotMapper = snapshotMapper;
        this.stockBacktestService = stockBacktestService;
        this.objectMapper = objectMapper;
    }

    public HistoricalBacktestSeedResult seedMonthly(String indexCode, int years, int symbolLimit, int dateLimit) {
        HistoricalBacktestSeedMapper mapper = seedMapper.getIfAvailable();
        IndexConstituentMapper constituentMapper = indexConstituentMapper.getIfAvailable();
        StockSignalSnapshotMapper signalSnapshotMapper = snapshotMapper.getIfAvailable();
        if (mapper == null || constituentMapper == null || signalSnapshotMapper == null) {
            return new HistoricalBacktestSeedResult(0, 0, 0, 0, 0);
        }

        List<LocalDate> seedDates = seedDates(mapper, years, dateLimit);
        int snapshots = 0;
        int skipped = 0;
        LinkedHashSet<String> symbolUniverse = new LinkedHashSet<>();

        for (LocalDate seedDate : seedDates) {
            List<String> symbols = constituentMapper.findMemberSymbolsOnDate(indexCode, seedDate);
            if (symbols == null || symbols.isEmpty()) {
                symbols = constituentMapper.findCurrentSymbols(indexCode);
            }
            if (symbolLimit > 0 && symbols.size() > symbolLimit) {
                symbols = symbols.subList(0, symbolLimit);
            }
            symbolUniverse.addAll(symbols);
            List<StockSignalSnapshot> dailySignals = new ArrayList<>();
            for (String symbol : symbols) {
                StockSignalSnapshot snapshot = buildSnapshot(mapper, symbol, seedDate);
                if (snapshot == null) {
                    skipped++;
                    continue;
                }
                dailySignals.add(snapshot);
            }

            applyHistoricalRankScores(dailySignals);
            for (StockSignalSnapshot snapshot : dailySignals) {
                signalSnapshotMapper.upsert(snapshot);
                snapshots++;
            }
        }

        int results = stockBacktestService.refreshCompletedResults(Math.max(50_000, snapshots * 3));
        return new HistoricalBacktestSeedResult(seedDates.size(), symbolUniverse.size(), snapshots, skipped, results);
    }

    private List<LocalDate> seedDates(HistoricalBacktestSeedMapper mapper, int years, int dateLimit) {
        LocalDate latestCandleDate = mapper.findLatestCandleDate();
        if (latestCandleDate == null) {
            return List.of();
        }

        LocalDate endDate = latestCandleDate.minusDays(90);
        LocalDate startDate = endDate.minusYears(Math.max(1, years));
        LocalDate cursor = startDate.withDayOfMonth(1);
        LinkedHashSet<LocalDate> dates = new LinkedHashSet<>();
        while (!cursor.isAfter(endDate)) {
            LocalDate monthEnd = cursor.withDayOfMonth(cursor.lengthOfMonth());
            LocalDate target = monthEnd.isAfter(endDate) ? endDate : monthEnd;
            LocalDate tradeDate = mapper.findTradeDateOnOrBefore(target);
            if (tradeDate != null && !tradeDate.isBefore(startDate) && !tradeDate.isAfter(endDate)) {
                dates.add(tradeDate);
            }
            cursor = cursor.plusMonths(1);
        }
        List<LocalDate> result = new ArrayList<>(dates);
        result.sort(Comparator.naturalOrder());
        if (dateLimit > 0 && result.size() > dateLimit) {
            return result.subList(result.size() - dateLimit, result.size());
        }
        return result;
    }

    private StockSignalSnapshot buildSnapshot(HistoricalBacktestSeedMapper mapper, String symbol, LocalDate signalDate) {
        List<StockCandleDaily> candles = mapper.findCandlesOnOrBefore(symbol, signalDate, CANDLE_LOOKBACK);
        if (candles.size() < MIN_CANDLES || !hasValidClose(candles.getFirst())) {
            return null;
        }
        Collections.reverse(candles);

        SecFinancialStandard annual = mapper.findLatestAnnualAsOf(symbol, signalDate);
        SecFinancialStandard previousAnnual = annual == null ? null
                : mapper.findPreviousAnnualAsOf(symbol, annual.getEndDate(), signalDate);
        SecFinancialStandard quarter = mapper.findLatestQuarterAsOf(symbol, signalDate);
        SecFinancialStandard previousQuarter = quarter == null ? null
                : mapper.findPreviousQuarterAsOf(symbol, quarter.getEndDate(), signalDate);
        StockRecommendationTrend recommendation = mapper.findRecommendationAsOf(symbol, signalDate);
        StockInstitutionFlow institutionFlow = mapper.findInstitutionFlowAsOf(symbol, signalDate.minusDays(45));
        HistoricalNewsStats news = mapper.findNewsStatsAsOf(symbol, signalDate.minusDays(30).atStartOfDay(),
                signalDate.plusDays(1).atStartOfDay());

        BigDecimal close = latestClose(candles);
        SignalScore valuation = valuationScore(close, annual);
        SignalScore quality = qualityScore(annual);
        SignalScore growth = growthScore(annual, previousAnnual);
        SignalScore stability = stabilityScore(annual);
        SignalScore earnings = earningsScore(quarter, previousQuarter);
        SignalScore analyst = analystScore(recommendation);
        SignalScore newsScore = newsScore(news);
        SignalScore momentum = momentumScore(candles);
        SignalScore risk = riskScore(candles);
        SignalScore institution = institutionScore(institutionFlow);

        int dataQualityScore = dataQualityScore(candles, annual, quarter, recommendation, institutionFlow, news);
        int rawScore = weightedScore(List.of(valuation, quality, growth, stability, earnings, analyst, newsScore,
                momentum, risk, institution));

        StockSignalSnapshot snapshot = new StockSignalSnapshot();
        snapshot.setSymbol(symbol);
        snapshot.setSignalDate(signalDate);
        snapshot.setSnapshotMode(SNAPSHOT_MODE);
        snapshot.setCalculatedAt(LocalDateTime.now());
        snapshot.setSignalVersion(SIGNAL_VERSION);
        snapshot.setIntegratedScore(rawScore);
        snapshot.setIntegratedLabel(integratedLabel(rawScore));
        snapshot.setTone(tone(rawScore));
        snapshot.setConfidence(confidence(dataQualityScore));
        snapshot.setSummary(integratedLabel(rawScore)
                + "입니다. 10개 Signal을 과거 시점 기준으로 재구성한 historical backtest seed입니다.");
        snapshot.setValuationScore(valuation.score());
        snapshot.setValuationLabel(valuation.label());
        snapshot.setQualityScore(quality.score());
        snapshot.setQualityLabel(quality.label());
        snapshot.setGrowthScore(growth.score());
        snapshot.setGrowthLabel(growth.label());
        snapshot.setStabilityScore(stability.score());
        snapshot.setStabilityLabel(stability.label());
        snapshot.setEarningsScore(earnings.score());
        snapshot.setEarningsLabel(earnings.label());
        snapshot.setAnalystScore(analyst.score());
        snapshot.setAnalystLabel(analyst.label());
        snapshot.setNewsScore(newsScore.score());
        snapshot.setNewsLabel(newsScore.label());
        snapshot.setMomentumScore(momentum.score());
        snapshot.setMomentumLabel(momentum.label());
        snapshot.setRiskScore(risk.score());
        snapshot.setRiskLabel(risk.label());
        snapshot.setInstitutionScore(institution.score());
        snapshot.setInstitutionLabel(institution.label());
        snapshot.setDataQualityScore(dataQualityScore);
        snapshot.setDataQualityExcludedMetricCount(excludedMetricCount(annual, quarter, recommendation,
                institutionFlow, news));
        snapshot.setDataQualityIssueCount(dataQualityScore < 60 ? 1 : 0);
        snapshot.setReasonsJson(writeJson(reasons(valuation, quality, growth, stability, earnings, analyst, newsScore,
                momentum, risk, institution)));
        snapshot.setCardsJson(writeJson(cards(valuation, quality, growth, stability, earnings, analyst, newsScore,
                momentum, risk, institution)));
        snapshot.setSourceFreshnessJson(writeJson(sourceFreshness(signalDate, candles, annual, quarter,
                recommendation, institutionFlow, news)));
        snapshot.setRawJson(writeJson(raw(symbol, signalDate, close, annual, previousAnnual, quarter,
                previousQuarter, recommendation, institutionFlow, news, dataQualityScore)));
        return snapshot;
    }

    private static SignalScore valuationScore(BigDecimal close, SecFinancialStandard annual) {
        BigDecimal eps = annual == null ? null : annual.getEpsDiluted();
        if (close == null || eps == null || eps.compareTo(BigDecimal.ZERO) <= 0) {
            return new SignalScore("Valuation", 50, "중립", "과거 시점 EPS가 없어 Valuation을 중립 처리했습니다.");
        }
        BigDecimal pe = close.divide(eps, 4, RoundingMode.HALF_UP);
        int score;
        if (pe.compareTo(BigDecimal.valueOf(12)) < 0) {
            score = 78;
        } else if (pe.compareTo(BigDecimal.valueOf(22)) < 0) {
            score = 68;
        } else if (pe.compareTo(BigDecimal.valueOf(35)) < 0) {
            score = 55;
        } else if (pe.compareTo(BigDecimal.valueOf(55)) < 0) {
            score = 40;
        } else {
            score = 28;
        }
        return new SignalScore("Valuation", score, score >= 60 ? "밸류 부담 낮음" : score >= 45 ? "밸류 중립" : "고평가 부담",
                "as-of PE " + pe.setScale(2, RoundingMode.HALF_UP) + "배");
    }

    private static SignalScore qualityScore(SecFinancialStandard annual) {
        if (annual == null || annual.getRevenue() == null || annual.getNetIncome() == null) {
            return new SignalScore("Quality", 50, "중립", "SEC annual 재무가 부족해 Quality를 중립 처리했습니다.");
        }
        BigDecimal netMargin = ratioPct(annual.getNetIncome(), annual.getRevenue());
        BigDecimal operatingMargin = ratioPct(annual.getOperatingIncome(), annual.getRevenue());
        BigDecimal roe = ratioPct(annual.getNetIncome(), annual.getEquity());
        int score = 50;
        score += marginPoints(netMargin, 8, 20);
        score += marginPoints(operatingMargin, 8, 16);
        score += marginPoints(roe, 12, 18);
        score = clamp(score);
        return new SignalScore("Quality", score, score >= 68 ? "수익성 강함" : score >= 48 ? "수익성 보통" : "마진 약함",
                "net margin " + percentText(netMargin) + ", op margin " + percentText(operatingMargin)
                        + ", ROE " + percentText(roe));
    }

    private static SignalScore growthScore(SecFinancialStandard annual, SecFinancialStandard previousAnnual) {
        BigDecimal revenueGrowth = growthPct(value(annual, SecFinancialStandard::getRevenue),
                value(previousAnnual, SecFinancialStandard::getRevenue));
        BigDecimal incomeGrowth = growthPct(value(annual, SecFinancialStandard::getNetIncome),
                value(previousAnnual, SecFinancialStandard::getNetIncome));
        if (revenueGrowth == null && incomeGrowth == null) {
            return new SignalScore("Growth", 50, "중립", "비교 가능한 전년 재무가 부족합니다.");
        }
        int score = 50
                + growthPoints(revenueGrowth, 2, 18)
                + growthPoints(incomeGrowth, 3, 18);
        score = clamp(score);
        return new SignalScore("Growth", score, score >= 66 ? "성장 우세" : score >= 45 ? "성장 중립" : "성장 둔화",
                "revenue YoY " + percentText(revenueGrowth) + ", net income YoY " + percentText(incomeGrowth));
    }

    private static SignalScore stabilityScore(SecFinancialStandard annual) {
        if (annual == null || annual.getAssets() == null) {
            return new SignalScore("Stability", 50, "중립", "자산/부채 재무가 부족합니다.");
        }
        BigDecimal liabilitiesToAssets = ratioPct(annual.getLiabilities(), annual.getAssets());
        BigDecimal equityToAssets = ratioPct(annual.getEquity(), annual.getAssets());
        BigDecimal ocfToIncome = ratioPct(annual.getOperatingCashFlow(), annual.getNetIncome());
        int score = 50;
        if (liabilitiesToAssets != null) {
            score += liabilitiesToAssets.compareTo(BigDecimal.valueOf(45)) <= 0 ? 16
                    : liabilitiesToAssets.compareTo(BigDecimal.valueOf(70)) <= 0 ? 4 : -16;
        }
        if (equityToAssets != null) {
            score += equityToAssets.compareTo(BigDecimal.valueOf(30)) >= 0 ? 12 : -8;
        }
        if (ocfToIncome != null) {
            score += ocfToIncome.compareTo(BigDecimal.valueOf(80)) >= 0 ? 12 : -6;
        }
        score = clamp(score);
        return new SignalScore("Stability", score, score >= 65 ? "재무 안정" : score >= 45 ? "안정성 보통" : "재무 부담",
                "liabilities/assets " + percentText(liabilitiesToAssets)
                        + ", equity/assets " + percentText(equityToAssets)
                        + ", OCF/net income " + percentText(ocfToIncome));
    }

    private static SignalScore earningsScore(SecFinancialStandard quarter, SecFinancialStandard previousQuarter) {
        BigDecimal revenueGrowth = growthPct(value(quarter, SecFinancialStandard::getRevenue),
                value(previousQuarter, SecFinancialStandard::getRevenue));
        BigDecimal incomeGrowth = growthPct(value(quarter, SecFinancialStandard::getNetIncome),
                value(previousQuarter, SecFinancialStandard::getNetIncome));
        if (quarter == null || (revenueGrowth == null && incomeGrowth == null)) {
            return new SignalScore("Earnings", 50, "중립", "과거 분기 실적 비교 데이터가 부족합니다.");
        }
        int score = clamp(50 + growthPoints(revenueGrowth, 3, 16) + growthPoints(incomeGrowth, 4, 18));
        return new SignalScore("Earnings", score, score >= 65 ? "실적 개선" : score >= 45 ? "실적 중립" : "실적 둔화",
                "quarter revenue change " + percentText(revenueGrowth)
                        + ", net income change " + percentText(incomeGrowth));
    }

    private static SignalScore analystScore(StockRecommendationTrend recommendation) {
        if (recommendation == null) {
            return new SignalScore("Analyst", 50, "중립", "과거 analyst trend가 없어 중립 처리했습니다.");
        }
        int buy = safe(recommendation.getStrongBuy()) + safe(recommendation.getBuy());
        int hold = safe(recommendation.getHold());
        int sell = safe(recommendation.getSell()) + safe(recommendation.getStrongSell());
        int total = buy + hold + sell;
        if (total == 0) {
            return new SignalScore("Analyst", 50, "중립", "analyst 표본이 없습니다.");
        }
        int score = clamp(50 + Math.round((buy - sell) * 50.0f / total));
        return new SignalScore("Analyst", score, score >= 65 ? "매수 우세" : score >= 45 ? "중립 우세" : "매도 우세",
                "buy " + buy + ", hold " + hold + ", sell " + sell);
    }

    private static SignalScore institutionScore(StockInstitutionFlow flow) {
        if (!hasInstitutionFlow(flow)) {
            return new SignalScore("Institution", 50, "중립",
                    "과거 시점에서 사용할 수 있는 13F 기관수급 데이터가 없어 중립 처리했습니다.");
        }
        BigDecimal sharesChange = flow.getSharesChangePct();
        BigDecimal valueChange = flow.getValueChangePct();
        int score = 50;
        if (sharesChange != null) {
            score += cappedInt(sharesChange.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP), -18, 18);
        }
        if (valueChange != null) {
            score += cappedInt(valueChange.divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP), -10, 10);
        }
        if (flow.getHolderCount() != null) {
            if (flow.getHolderCount() >= 10) {
                score += 5;
            } else if (flow.getHolderCount() <= 2) {
                score -= 4;
            }
        }
        score = clamp(score);
        String label = score >= 65 ? "기관 보유 증가" : score >= 48 ? "기관 보유 중립" : "기관 보유 감소";
        String detail = "13F " + (flow.getReportQuarter() == null ? "-" : flow.getReportQuarter())
                + ", shares QoQ " + percentText(sharesChange)
                + ", value QoQ " + percentText(valueChange)
                + ", holders " + (flow.getHolderCount() == null ? "-" : flow.getHolderCount());
        return new SignalScore("Institution", score, label, detail);
    }

    private static SignalScore newsScore(HistoricalNewsStats news) {
        int total = news == null || news.getTotalCount() == null ? 0 : news.getTotalCount();
        if (total == 0) {
            return new SignalScore("News", 50, "중립", "과거 30일 뉴스가 없습니다.");
        }
        int positive = safe(news.getPositiveCount());
        int risk = safe(news.getRiskCount());
        int score = clamp(50 + positive * 5 - risk * 7 + Math.min(8, total));
        return new SignalScore("News", score, score >= 63 ? "긍정 뉴스 우세" : score >= 43 ? "뉴스 중립" : "리스크 뉴스 우세",
                "news " + total + ", positive " + positive + ", risk " + risk);
    }

    private static SignalScore momentumScore(List<StockCandleDaily> candles) {
        BigDecimal return20 = returnPct(candles, 20);
        BigDecimal return60 = returnPct(candles, 60);
        BigDecimal close = latestClose(candles);
        BigDecimal sma20 = averageClose(candles, 20);
        BigDecimal sma60 = averageClose(candles, 60);
        BigDecimal position = rangePosition(candles, 252);
        int score = 50
                + cappedPoints(return20, BigDecimal.valueOf(1.2), 18)
                + cappedPoints(return60, BigDecimal.valueOf(0.6), 20);
        if (close != null && sma20 != null) {
            score += close.compareTo(sma20) >= 0 ? 6 : -6;
        }
        if (close != null && sma60 != null) {
            score += close.compareTo(sma60) >= 0 ? 6 : -6;
        }
        if (position != null) {
            score += position.compareTo(BigDecimal.valueOf(75)) >= 0 ? 8
                    : position.compareTo(BigDecimal.valueOf(25)) <= 0 ? -8 : 0;
        }
        score = clamp(score);
        return new SignalScore("Momentum", score, score >= 65 ? "상승 모멘텀" : score >= 45 ? "모멘텀 중립" : "단기 약세",
                "20D " + percentText(return20) + ", 60D " + percentText(return60)
                        + ", 52W position " + percentText(position));
    }

    private static SignalScore riskScore(List<StockCandleDaily> candles) {
        BigDecimal volatility20 = realizedVolatility(candles, 20);
        BigDecimal drawdown60 = drawdown(candles, 60);
        int score = 65;
        if (volatility20 != null) {
            score -= volatility20.compareTo(BigDecimal.valueOf(45)) >= 0 ? 18
                    : volatility20.compareTo(BigDecimal.valueOf(30)) >= 0 ? 8 : 0;
        }
        if (drawdown60 != null) {
            score += drawdown60.compareTo(BigDecimal.valueOf(-8)) >= 0 ? 10
                    : drawdown60.compareTo(BigDecimal.valueOf(-20)) >= 0 ? -4 : -16;
        }
        score = clamp(score);
        return new SignalScore("Risk", score, score >= 65 ? "리스크 양호" : score >= 45 ? "리스크 보통" : "하락 리스크",
                "20D volatility " + percentText(volatility20) + ", 60D drawdown " + percentText(drawdown60));
    }

    private static int weightedScore(List<SignalScore> scores) {
        Map<String, Integer> weights = Map.of(
                "Quality", 17,
                "Valuation", 14,
                "Growth", 14,
                "Earnings", 13,
                "Stability", 9,
                "Momentum", 8,
                "Risk", 8,
                "Analyst", 7,
                "Institution", 6,
                "News", 4);
        int weighted = 0;
        int total = 0;
        for (SignalScore score : scores) {
            int weight = weights.getOrDefault(score.title(), 0);
            weighted += score.score() * weight;
            total += weight;
        }
        return total == 0 ? 50 : clamp(Math.round(weighted / (float) total));
    }

    private static void applyHistoricalRankScores(List<StockSignalSnapshot> snapshots) {
        if (snapshots.size() < 20) {
            return;
        }
        Map<String, Integer> valuationRanks = percentileRanks(snapshots, StockSignalSnapshot::getValuationScore);
        Map<String, Integer> qualityRanks = percentileRanks(snapshots, StockSignalSnapshot::getQualityScore);
        Map<String, Integer> growthRanks = percentileRanks(snapshots, StockSignalSnapshot::getGrowthScore);
        Map<String, Integer> stabilityRanks = percentileRanks(snapshots, StockSignalSnapshot::getStabilityScore);
        Map<String, Integer> earningsRanks = percentileRanks(snapshots, StockSignalSnapshot::getEarningsScore);
        Map<String, Integer> analystRanks = percentileRanks(snapshots, StockSignalSnapshot::getAnalystScore);
        Map<String, Integer> newsRanks = percentileRanks(snapshots, StockSignalSnapshot::getNewsScore);
        Map<String, Integer> momentumRanks = percentileRanks(snapshots, StockSignalSnapshot::getMomentumScore);
        Map<String, Integer> riskRanks = percentileRanks(snapshots, StockSignalSnapshot::getRiskScore);
        Map<String, Integer> institutionRanks = percentileRanks(snapshots, StockSignalSnapshot::getInstitutionScore);

        for (StockSignalSnapshot snapshot : snapshots) {
            String symbol = snapshot.getSymbol();
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
            int rawScore = snapshot.getIntegratedScore() == null ? 50 : snapshot.getIntegratedScore();
            int score = Math.round(rawScore * 0.35f + factorRankScore * 0.65f);
            score = applyGuardrails(score, snapshot);
            snapshot.setIntegratedScore(score);
            snapshot.setIntegratedLabel(integratedLabel(score));
            snapshot.setTone(tone(score));
            snapshot.setSummary(integratedLabel(score)
                    + "입니다. 10개 Signal을 과거 시점 기준으로 재구성한 historical backtest seed입니다.");
        }
    }

    private static Map<String, Integer> percentileRanks(List<StockSignalSnapshot> snapshots,
            Function<StockSignalSnapshot, Integer> reader) {
        List<StockSignalSnapshot> ranked = snapshots.stream()
                .filter(snapshot -> snapshot.getSymbol() != null && reader.apply(snapshot) != null)
                .sorted(Comparator
                        .comparing((StockSignalSnapshot snapshot) -> reader.apply(snapshot))
                        .thenComparing(StockSignalSnapshot::getSymbol))
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
            ranks.put(ranked.get(index).getSymbol(), Math.round(index * 100.0f / (ranked.size() - 1)));
        }
        return ranks;
    }

    private static int rank(Map<String, Integer> ranks, String symbol) {
        return ranks.getOrDefault(symbol, 50);
    }

    private static int applyGuardrails(int score, StockSignalSnapshot snapshot) {
        if (lte(snapshot.getQualityScore(), 35)) {
            score = Math.min(score, 58);
        }
        if (lte(snapshot.getValuationScore(), 30) && !gte(snapshot.getQualityScore(), 70)) {
            score = Math.min(score, 62);
        }
        if (lte(snapshot.getStabilityScore(), 30)) {
            score = Math.min(score, 60);
        }
        if (lte(snapshot.getRiskScore(), 30)) {
            score = Math.min(score, 64);
        }
        if (lte(snapshot.getInstitutionScore(), 25)) {
            score = Math.min(score, 72);
        }
        if (snapshot.getDataQualityScore() != null && snapshot.getDataQualityScore() < 45) {
            score = Math.min(score, 58);
        }
        return clamp(score);
    }

    private static int dataQualityScore(List<StockCandleDaily> candles, SecFinancialStandard annual,
            SecFinancialStandard quarter, StockRecommendationTrend recommendation, StockInstitutionFlow institutionFlow,
            HistoricalNewsStats news) {
        int score = 35;
        if (candles.size() >= 252) {
            score += 25;
        } else if (candles.size() >= 120) {
            score += 16;
        } else if (candles.size() >= 80) {
            score += 10;
        }
        if (annual != null) {
            score += 22;
        }
        if (quarter != null) {
            score += 12;
        }
        if (recommendation != null) {
            score += 4;
        }
        if (hasInstitutionFlow(institutionFlow)) {
            score += 4;
        }
        if (news != null && safe(news.getTotalCount()) > 0) {
            score += 2;
        }
        return clamp(score);
    }

    private static int excludedMetricCount(SecFinancialStandard annual, SecFinancialStandard quarter,
            StockRecommendationTrend recommendation, StockInstitutionFlow institutionFlow, HistoricalNewsStats news) {
        int count = 0;
        if (annual == null) {
            count += 4;
        }
        if (quarter == null) {
            count += 1;
        }
        if (recommendation == null) {
            count += 1;
        }
        if (!hasInstitutionFlow(institutionFlow)) {
            count += 1;
        }
        if (news == null || safe(news.getTotalCount()) == 0) {
            count += 1;
        }
        return count;
    }

    private static BigDecimal latestClose(List<StockCandleDaily> candles) {
        return candles.isEmpty() ? null : candles.getLast().getClosePrice();
    }

    private static BigDecimal returnPct(List<StockCandleDaily> candles, int lookback) {
        if (candles.size() <= lookback || !hasValidClose(candles.getLast())) {
            return null;
        }
        BigDecimal current = candles.getLast().getClosePrice();
        BigDecimal previous = candles.get(candles.size() - 1 - lookback).getClosePrice();
        if (previous == null || previous.signum() <= 0) {
            return null;
        }
        return current.subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous, 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal averageClose(List<StockCandleDaily> candles, int length) {
        if (candles.size() < length) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int index = candles.size() - length; index < candles.size(); index++) {
            if (!hasValidClose(candles.get(index))) {
                return null;
            }
            sum = sum.add(candles.get(index).getClosePrice());
        }
        return sum.divide(BigDecimal.valueOf(length), 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal rangePosition(List<StockCandleDaily> candles, int length) {
        int start = Math.max(0, candles.size() - length);
        BigDecimal low = null;
        BigDecimal high = null;
        for (int index = start; index < candles.size(); index++) {
            BigDecimal close = candles.get(index).getClosePrice();
            if (close == null) {
                continue;
            }
            low = low == null || close.compareTo(low) < 0 ? close : low;
            high = high == null || close.compareTo(high) > 0 ? close : high;
        }
        BigDecimal current = latestClose(candles);
        if (current == null || low == null || high == null || high.compareTo(low) == 0) {
            return null;
        }
        return current.subtract(low).multiply(BigDecimal.valueOf(100))
                .divide(high.subtract(low), 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal realizedVolatility(List<StockCandleDaily> candles, int length) {
        if (candles.size() <= length) {
            return null;
        }
        List<Double> returns = new ArrayList<>();
        for (int index = candles.size() - length; index < candles.size(); index++) {
            BigDecimal previous = candles.get(index - 1).getClosePrice();
            BigDecimal current = candles.get(index).getClosePrice();
            if (previous == null || previous.signum() <= 0 || current == null) {
                continue;
            }
            returns.add(current.subtract(previous).divide(previous, 8, RoundingMode.HALF_UP).doubleValue());
        }
        if (returns.size() < 5) {
            return null;
        }
        double average = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = returns.stream()
                .mapToDouble(value -> Math.pow(value - average, 2))
                .sum() / returns.size();
        double annualized = Math.sqrt(variance) * Math.sqrt(252) * 100;
        return BigDecimal.valueOf(annualized).setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal drawdown(List<StockCandleDaily> candles, int length) {
        if (candles.isEmpty()) {
            return null;
        }
        int start = Math.max(0, candles.size() - length);
        BigDecimal high = null;
        for (int index = start; index < candles.size(); index++) {
            BigDecimal close = candles.get(index).getClosePrice();
            if (close != null) {
                high = high == null || close.compareTo(high) > 0 ? close : high;
            }
        }
        BigDecimal current = latestClose(candles);
        if (high == null || current == null || high.signum() <= 0) {
            return null;
        }
        return current.subtract(high).multiply(BigDecimal.valueOf(100))
                .divide(high, 6, RoundingMode.HALF_UP);
    }

    private static boolean hasValidClose(StockCandleDaily candle) {
        return candle != null && candle.getClosePrice() != null && candle.getClosePrice().signum() > 0;
    }

    private static BigDecimal ratioPct(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal growthPct(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous.abs(), 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal value(SecFinancialStandard row, Function<SecFinancialStandard, BigDecimal> reader) {
        return row == null ? null : reader.apply(row);
    }

    private static int marginPoints(BigDecimal value, int divisor, int cap) {
        if (value == null) {
            return 0;
        }
        return Math.max(-cap, Math.min(cap, value.divide(BigDecimal.valueOf(divisor), 0, RoundingMode.HALF_UP).intValue()));
    }

    private static int growthPoints(BigDecimal value, int divisor, int cap) {
        if (value == null) {
            return 0;
        }
        return Math.max(-cap, Math.min(cap, value.divide(BigDecimal.valueOf(divisor), 0, RoundingMode.HALF_UP).intValue()));
    }

    private static int cappedPoints(BigDecimal value, BigDecimal multiplier, int cap) {
        if (value == null) {
            return 0;
        }
        int points = value.multiply(multiplier).setScale(0, RoundingMode.HALF_UP).intValue();
        return Math.max(-cap, Math.min(cap, points));
    }

    private static int cappedInt(BigDecimal value, int min, int max) {
        if (value == null) {
            return 0;
        }
        return Math.max(min, Math.min(max, value.setScale(0, RoundingMode.HALF_UP).intValue()));
    }

    private static boolean hasInstitutionFlow(StockInstitutionFlow flow) {
        return flow != null
                && (flow.getSharesChangePct() != null || flow.getValueChangePct() != null
                        || flow.getHolderCount() != null);
    }

    private static int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private static boolean gte(Integer value, int threshold) {
        return value != null && value >= threshold;
    }

    private static boolean lte(Integer value, int threshold) {
        return value != null && value <= threshold;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
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

    private static String confidence(int dataQualityScore) {
        if (dataQualityScore >= 80) {
            return "신뢰도 높음";
        }
        if (dataQualityScore >= 60) {
            return "신뢰도 보통";
        }
        return "신뢰도 낮음";
    }

    private static String percentText(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP) + "%";
    }

    private static List<String> reasons(SignalScore... scores) {
        List<String> reasons = new ArrayList<>();
        for (SignalScore score : scores) {
            reasons.add(score.title() + ": " + score.detail());
        }
        return reasons;
    }

    private static List<Map<String, Object>> cards(SignalScore... scores) {
        List<Map<String, Object>> cards = new ArrayList<>();
        for (SignalScore score : scores) {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("title", score.title());
            card.put("label", score.label());
            card.put("tone", tone(score.score()));
            card.put("score", score.score() + "점");
            card.put("detail", score.detail());
            cards.add(card);
        }
        return cards;
    }

    private static Map<String, Object> sourceFreshness(LocalDate signalDate, List<StockCandleDaily> candles,
            SecFinancialStandard annual, SecFinancialStandard quarter, StockRecommendationTrend recommendation,
            StockInstitutionFlow institutionFlow, HistoricalNewsStats news) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("snapshotMode", SNAPSHOT_MODE);
        values.put("signalDate", signalDate);
        values.put("latestCandleDate", candles.isEmpty() ? null : candles.getLast().getTradeDate());
        values.put("annualFiledAt", annual == null ? null : annual.getFiledAt());
        values.put("annualEndDate", annual == null ? null : annual.getEndDate());
        values.put("quarterFiledAt", quarter == null ? null : quarter.getFiledAt());
        values.put("quarterEndDate", quarter == null ? null : quarter.getEndDate());
        values.put("recommendationPeriodDate", recommendation == null ? null : recommendation.getPeriodDate());
        values.put("institution13fQuarter", institutionFlow == null ? null : institutionFlow.getReportQuarter());
        values.put("institution13fAvailabilityLagDays", 45);
        values.put("newsWindowDays", 30);
        values.put("newsCount", news == null ? 0 : safe(news.getTotalCount()));
        return values;
    }

    private static Map<String, Object> raw(String symbol, LocalDate signalDate, BigDecimal close,
            SecFinancialStandard annual, SecFinancialStandard previousAnnual, SecFinancialStandard quarter,
            SecFinancialStandard previousQuarter, StockRecommendationTrend recommendation,
            StockInstitutionFlow institutionFlow, HistoricalNewsStats news, int dataQualityScore) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("symbol", symbol);
        values.put("signalDate", signalDate);
        values.put("snapshotMode", SNAPSHOT_MODE);
        values.put("signalVersion", SIGNAL_VERSION);
        values.put("close", close);
        values.put("annualAccession", annual == null ? null : annual.getAccessionNumber());
        values.put("previousAnnualAccession", previousAnnual == null ? null : previousAnnual.getAccessionNumber());
        values.put("quarterAccession", quarter == null ? null : quarter.getAccessionNumber());
        values.put("previousQuarterAccession", previousQuarter == null ? null : previousQuarter.getAccessionNumber());
        values.put("recommendationPeriodDate", recommendation == null ? null : recommendation.getPeriodDate());
        values.put("institution13fQuarter", institutionFlow == null ? null : institutionFlow.getReportQuarter());
        values.put("institutionSharesChangePct", institutionFlow == null ? null : institutionFlow.getSharesChangePct());
        values.put("institutionValueChangePct", institutionFlow == null ? null : institutionFlow.getValueChangePct());
        values.put("newsCount", news == null ? 0 : safe(news.getTotalCount()));
        values.put("dataQualityScore", dataQualityScore);
        values.put("leakagePolicy",
                "Uses candles <= signal_date, SEC rows with filed_at <= signal_date, and 13F report_quarter + 45D <= signal_date.");
        values.put("knownLimitations", List.of(
                "Current index membership is used; historical index membership is not yet point-in-time.",
                "Finnhub latest metric snapshots are excluded from historical reconstructed snapshots.",
                "Historical shares outstanding is not available, so valuation uses close/EPS only.",
                "13F institution flow uses collected quarterly data with a conservative 45-day availability lag."));
        return values;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private record SignalScore(String title, int score, String label, String detail) {
    }
}
