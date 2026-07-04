package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class StockExpectedReturnFactorContribution {

    private String indexCode;
    private LocalDate signalDate;
    private String symbol;
    private Integer horizonDays;
    private String modelVersion;
    private String factor;
    private BigDecimal exposureScore;
    private BigDecimal coefficient;
    private BigDecimal contributionPct;
    private Integer sampleCount;
    private String source;
    private LocalDateTime calculatedAt;

    public String getIndexCode() {
        return indexCode;
    }

    public void setIndexCode(String indexCode) {
        this.indexCode = indexCode;
    }

    public LocalDate getSignalDate() {
        return signalDate;
    }

    public void setSignalDate(LocalDate signalDate) {
        this.signalDate = signalDate;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Integer getHorizonDays() {
        return horizonDays;
    }

    public void setHorizonDays(Integer horizonDays) {
        this.horizonDays = horizonDays;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getFactor() {
        return factor;
    }

    public void setFactor(String factor) {
        this.factor = factor;
    }

    public BigDecimal getExposureScore() {
        return exposureScore;
    }

    public void setExposureScore(BigDecimal exposureScore) {
        this.exposureScore = exposureScore;
    }

    public BigDecimal getCoefficient() {
        return coefficient;
    }

    public void setCoefficient(BigDecimal coefficient) {
        this.coefficient = coefficient;
    }

    public BigDecimal getContributionPct() {
        return contributionPct;
    }

    public void setContributionPct(BigDecimal contributionPct) {
        this.contributionPct = contributionPct;
    }

    public Integer getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(Integer sampleCount) {
        this.sampleCount = sampleCount;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(LocalDateTime calculatedAt) {
        this.calculatedAt = calculatedAt;
    }
}
