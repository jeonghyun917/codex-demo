package com.kingyurina.demo.stock;

import java.time.LocalDateTime;

public class StockDataQualityLatest {

    private String symbol;
    private LocalDateTime calculatedAt;
    private Integer qualityScore;
    private String qualityLabel;
    private String tone;
    private Integer coverageScore;
    private Integer freshnessScore;
    private Integer outlierScore;
    private Integer consistencyScore;
    private Integer issueCount;
    private Integer excludedMetricCount;
    private String staleSourcesJson;
    private String excludedFieldsJson;
    private String issuesJson;
    private String rawJson;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(LocalDateTime calculatedAt) {
        this.calculatedAt = calculatedAt;
    }

    public Integer getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(Integer qualityScore) {
        this.qualityScore = qualityScore;
    }

    public String getQualityLabel() {
        return qualityLabel;
    }

    public void setQualityLabel(String qualityLabel) {
        this.qualityLabel = qualityLabel;
    }

    public String getTone() {
        return tone;
    }

    public void setTone(String tone) {
        this.tone = tone;
    }

    public Integer getCoverageScore() {
        return coverageScore;
    }

    public void setCoverageScore(Integer coverageScore) {
        this.coverageScore = coverageScore;
    }

    public Integer getFreshnessScore() {
        return freshnessScore;
    }

    public void setFreshnessScore(Integer freshnessScore) {
        this.freshnessScore = freshnessScore;
    }

    public Integer getOutlierScore() {
        return outlierScore;
    }

    public void setOutlierScore(Integer outlierScore) {
        this.outlierScore = outlierScore;
    }

    public Integer getConsistencyScore() {
        return consistencyScore;
    }

    public void setConsistencyScore(Integer consistencyScore) {
        this.consistencyScore = consistencyScore;
    }

    public Integer getIssueCount() {
        return issueCount;
    }

    public void setIssueCount(Integer issueCount) {
        this.issueCount = issueCount;
    }

    public Integer getExcludedMetricCount() {
        return excludedMetricCount;
    }

    public void setExcludedMetricCount(Integer excludedMetricCount) {
        this.excludedMetricCount = excludedMetricCount;
    }

    public String getStaleSourcesJson() {
        return staleSourcesJson;
    }

    public void setStaleSourcesJson(String staleSourcesJson) {
        this.staleSourcesJson = staleSourcesJson;
    }

    public String getExcludedFieldsJson() {
        return excludedFieldsJson;
    }

    public void setExcludedFieldsJson(String excludedFieldsJson) {
        this.excludedFieldsJson = excludedFieldsJson;
    }

    public String getIssuesJson() {
        return issuesJson;
    }

    public void setIssuesJson(String issuesJson) {
        this.issuesJson = issuesJson;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }
}
