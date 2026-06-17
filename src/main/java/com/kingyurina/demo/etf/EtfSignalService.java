package com.kingyurina.demo.etf;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class EtfSignalService {

    public EtfSignal build(EtfMarketRow row, List<EtfCandleDaily> candles) {
        Integer cost = costScore(row);
        Integer liquidity = liquidityScore(row);
        Integer scale = scaleScore(row);
        Integer diversification = diversificationScore(row);
        Integer momentum = momentumScore(row, candles);
        Integer volatility = volatilityScore(candles);

        List<Integer> scores = new ArrayList<>();
        addScore(scores, cost);
        addScore(scores, liquidity);
        addScore(scores, scale);
        addScore(scores, diversification);
        addScore(scores, momentum);
        addScore(scores, volatility);
        int integrated = scores.isEmpty() ? 50
                : Math.round((float) scores.stream().mapToInt(Integer::intValue).sum() / scores.size());
        String tone = tone(integrated);
        List<EtfSignal.Card> cards = new ArrayList<>();
        cards.add(card("Cost", cost, label(cost, "저비용", "비용 보통", "비용 부담"),
                detailExpense(row.getExpenseRatio())));
        cards.add(card("Liquidity", liquidity, label(liquidity, "유동성 강함", "유동성 보통", "거래량 약함"),
                detailVolume(row.getVolume())));
        cards.add(card("Scale", scale, label(scale, "규모 우수", "규모 보통", "규모 확인 필요"),
                detailAum(row.getAumMillion())));
        cards.add(card("Diversification", diversification,
                label(diversification, "분산 우수", "분산 보통", "집중도 확인 필요"),
                detailHoldings(row.getHoldingsCount())));
        cards.add(card("Momentum", momentum, label(momentum, "추세 강함", "추세 보통", "추세 약함"),
                detailMomentum(row, candles)));
        cards.add(card("Volatility", volatility, label(volatility, "변동성 안정", "변동성 보통", "변동성 높음"),
                detailVolatility(candles)));

        List<String> reasons = cards.stream()
                .filter(card -> card.score() <= 45 || card.score() >= 70)
                .map(card -> card.title() + ": " + card.detail())
                .limit(4)
                .toList();

        return new EtfSignal(
                integrated,
                integratedLabel(integrated),
                tone,
                confidence(scores.size()),
                "ETF 전용 Signal입니다. 비용, 유동성, 규모, 분산, 모멘텀, 변동성을 합산합니다.",
                cards,
                reasons);
    }

    private static EtfSignal.Card card(String title, Integer score, String label, String detail) {
        int resolved = score == null ? 50 : score;
        return new EtfSignal.Card(title, label, resolved, tone(resolved), detail);
    }

    private static void addScore(List<Integer> scores, Integer score) {
        if (score != null) {
            scores.add(score);
        }
    }

    private static Integer costScore(EtfMarketRow row) {
        BigDecimal expense = row.getExpenseRatio();
        if (expense == null) {
            return null;
        }
        int score = 78;
        if (lte(expense, "0.05")) {
            score = 95;
        } else if (lte(expense, "0.10")) {
            score = 88;
        } else if (lte(expense, "0.20")) {
            score = 76;
        } else if (lte(expense, "0.50")) {
            score = 58;
        } else {
            score = 38;
        }
        return score;
    }

    private static Integer liquidityScore(EtfMarketRow row) {
        Long volume = row.getVolume();
        if (volume == null) {
            return null;
        }
        if (volume >= 10_000_000L) {
            return 92;
        }
        if (volume >= 3_000_000L) {
            return 82;
        }
        if (volume >= 500_000L) {
            return 68;
        }
        if (volume >= 100_000L) {
            return 52;
        }
        return 36;
    }

    private static Integer scaleScore(EtfMarketRow row) {
        BigDecimal aum = row.getAumMillion();
        if (aum == null) {
            return null;
        }
        if (gte(aum, "100000")) {
            return 94;
        }
        if (gte(aum, "30000")) {
            return 84;
        }
        if (gte(aum, "5000")) {
            return 70;
        }
        if (gte(aum, "1000")) {
            return 55;
        }
        return 38;
    }

    private static Integer diversificationScore(EtfMarketRow row) {
        Integer count = row.getHoldingsCount();
        if (count == null) {
            return null;
        }
        if (count >= 500) {
            return 90;
        }
        if (count >= 100) {
            return 76;
        }
        if (count >= 50) {
            return 62;
        }
        if (count >= 20) {
            return 50;
        }
        return 35;
    }

    private static Integer momentumScore(EtfMarketRow row, List<EtfCandleDaily> candles) {
        BigDecimal change = changePercent(row);
        int score = 50;
        boolean hasData = false;
        if (change != null) {
            hasData = true;
            score += bounded(change.multiply(BigDecimal.valueOf(4)), -18, 18);
        }
        BigDecimal close = latestClose(candles);
        BigDecimal sma20 = averageClose(candles, 20);
        BigDecimal sma60 = averageClose(candles, 60);
        if (close != null && sma20 != null) {
            hasData = true;
            score += close.compareTo(sma20) >= 0 ? 10 : -10;
        }
        if (close != null && sma60 != null) {
            hasData = true;
            score += close.compareTo(sma60) >= 0 ? 8 : -8;
        }
        return hasData ? clamp(score) : null;
    }

    private static Integer volatilityScore(List<EtfCandleDaily> candles) {
        if (candles == null || candles.size() < 20) {
            return null;
        }
        List<BigDecimal> returns = new ArrayList<>();
        List<EtfCandleDaily> chronological = candles.reversed();
        for (int index = 1; index < chronological.size(); index++) {
            BigDecimal previous = chronological.get(index - 1).getClosePrice();
            BigDecimal current = chronological.get(index).getClosePrice();
            if (previous == null || current == null || previous.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            returns.add(current.subtract(previous).multiply(BigDecimal.valueOf(100))
                    .divide(previous, 6, RoundingMode.HALF_UP));
        }
        if (returns.size() < 10) {
            return null;
        }
        double mean = returns.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double variance = returns.stream()
                .mapToDouble(value -> Math.pow(value.doubleValue() - mean, 2))
                .average()
                .orElse(0);
        double dailyVol = Math.sqrt(variance);
        if (dailyVol <= 0.8) {
            return 86;
        }
        if (dailyVol <= 1.4) {
            return 72;
        }
        if (dailyVol <= 2.2) {
            return 58;
        }
        if (dailyVol <= 3.5) {
            return 44;
        }
        return 30;
    }

    private static String detailExpense(BigDecimal value) {
        return value == null ? "수수료 데이터 없음" : "총보수 " + strip(value) + "%";
    }

    private static String detailVolume(Long value) {
        return value == null ? "거래량 데이터 없음" : "최근 거래량 " + String.format("%,d", value);
    }

    private static String detailAum(BigDecimal value) {
        return value == null ? "AUM 데이터 없음" : "AUM " + strip(value) + " million USD";
    }

    private static String detailHoldings(Integer value) {
        return value == null ? "보유 종목 수 데이터 없음" : "보유 종목 " + value + "개";
    }

    private static String detailMomentum(EtfMarketRow row, List<EtfCandleDaily> candles) {
        BigDecimal change = changePercent(row);
        BigDecimal close = latestClose(candles);
        BigDecimal sma20 = averageClose(candles, 20);
        if (change == null && close == null) {
            return "가격/일봉 데이터 없음";
        }
        if (close != null && sma20 != null) {
            String changeText = change == null ? "전일 대비 데이터 없음" : "전일 대비 " + strip(change) + "%";
            return changeText + ", SMA20 대비 "
                    + strip(close.subtract(sma20).multiply(BigDecimal.valueOf(100)).divide(sma20, 2, RoundingMode.HALF_UP))
                    + "%";
        }
        return change == null ? "전일 대비 데이터 없음" : "전일 대비 " + strip(change) + "%";
    }

    private static String detailVolatility(List<EtfCandleDaily> candles) {
        return candles == null || candles.size() < 20 ? "20일 이상 일봉 필요" : "최근 일봉 " + candles.size() + "개 기준";
    }

    private static BigDecimal changePercent(EtfMarketRow row) {
        if (row.getCurrentPrice() == null || row.getPreviousClose() == null
                || row.getPreviousClose().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return row.getCurrentPrice().subtract(row.getPreviousClose()).multiply(BigDecimal.valueOf(100))
                .divide(row.getPreviousClose(), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal latestClose(List<EtfCandleDaily> candles) {
        return candles == null || candles.isEmpty() ? null : candles.get(0).getClosePrice();
    }

    private static BigDecimal averageClose(List<EtfCandleDaily> candles, int limit) {
        if (candles == null || candles.size() < limit) {
            return null;
        }
        List<BigDecimal> closes = candles.stream()
                .limit(limit)
                .map(EtfCandleDaily::getClosePrice)
                .filter(value -> value != null)
                .toList();
        if (closes.size() < limit) {
            return null;
        }
        return closes.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(closes.size()), 6, RoundingMode.HALF_UP);
    }

    private static String integratedLabel(int score) {
        if (score >= 76) {
            return "우수";
        }
        if (score >= 62) {
            return "양호";
        }
        if (score >= 46) {
            return "중립";
        }
        return "주의";
    }

    private static String label(Integer score, String high, String middle, String low) {
        if (score == null) {
            return "데이터 부족";
        }
        if (score >= 70) {
            return high;
        }
        if (score >= 46) {
            return middle;
        }
        return low;
    }

    private static String confidence(int size) {
        if (size >= 5) {
            return "신뢰도 보통";
        }
        if (size >= 3) {
            return "신뢰도 낮음";
        }
        return "신뢰도 매우 낮음";
    }

    private static String tone(int score) {
        if (score >= 70) {
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

    private static boolean gte(BigDecimal value, String threshold) {
        return value != null && value.compareTo(new BigDecimal(threshold)) >= 0;
    }

    private static boolean lte(BigDecimal value, String threshold) {
        return value != null && value.compareTo(new BigDecimal(threshold)) <= 0;
    }

    private static int bounded(BigDecimal value, int min, int max) {
        return Math.max(min, Math.min(max, value.setScale(0, RoundingMode.HALF_UP).intValue()));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static String strip(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
