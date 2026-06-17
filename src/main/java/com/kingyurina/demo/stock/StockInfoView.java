package com.kingyurina.demo.stock;

import java.util.List;

public record StockInfoView(
        List<InfoRow> majorRows,
        List<MetricCard> investmentCards,
        List<MetricCard> institutionCards,
        List<MetricCard> financeCards,
        List<SeriesPoint> profitability,
        List<SeriesPoint> stability,
        List<SeriesPoint> earningsRevenue,
        List<SeriesPoint> earningsEps,
        List<MetricRow> incomeStatementRows,
        List<MetricCard> dividendCards,
        List<SeriesPoint> dividendHistory,
        List<PeerRow> peers,
        String industryName,
        String marketCapLabel,
        String enterpriseValueLabel,
        String shareOutstandingLabel,
        String fiscalSourceLabel,
        List<MetricCard> dataQualityCards,
        List<String> dataQualityIssues) {

    public record InfoRow(String label, String value) {
    }

    public record MetricCard(String label, String value, String note) {
    }

    public record SeriesPoint(String label, String primary, String secondary, int primaryHeight, int secondaryHeight) {
    }

    public record MetricRow(String label, List<String> values) {
    }

    public record PeerRow(String rank, String symbol, String name, String per, String marketCap, String price,
            String pbr, String roe, boolean selected) {
    }

    public record StockSignalView(String integratedLabel, String tone, String score, String confidence,
            String summary, List<SignalCard> cards, List<String> reasons) {
    }

    public record SignalCard(String title, String label, String tone, String score, String detail) {
    }
}
