package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class StockExpectedReturnSnapshot {

    private String indexCode;
    private LocalDate signalDate;
    private String symbol;
    private Integer horizonDays;
    private BigDecimal expectedReturnPct;
    private BigDecimal expectedExcessReturnPct;
    private BigDecimal returnP10Pct;
    private BigDecimal returnP50Pct;
    private BigDecimal returnP90Pct;
    private BigDecimal excessP10Pct;
    private BigDecimal excessP50Pct;
    private BigDecimal excessP90Pct;
    private BigDecimal upsideProbabilityPct;
    private BigDecimal calibratedUpsideProbabilityPct;
    private BigDecimal downsideProbabilityPct;
    private BigDecimal drawdownRiskPct;
    private Integer confidence;
    private Integer sampleCount;
    private Integer sectorSampleCount;
    private Integer scoreBucket;
    private Integer calibrationBucket;
    private BigDecimal calibrationErrorPct;
    private String modelVersion;
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

    public BigDecimal getExpectedReturnPct() {
        return expectedReturnPct;
    }

    public void setExpectedReturnPct(BigDecimal expectedReturnPct) {
        this.expectedReturnPct = expectedReturnPct;
    }

    public BigDecimal getExpectedExcessReturnPct() {
        return expectedExcessReturnPct;
    }

    public void setExpectedExcessReturnPct(BigDecimal expectedExcessReturnPct) {
        this.expectedExcessReturnPct = expectedExcessReturnPct;
    }

    public BigDecimal getReturnP10Pct() {
        return returnP10Pct;
    }

    public void setReturnP10Pct(BigDecimal returnP10Pct) {
        this.returnP10Pct = returnP10Pct;
    }

    public BigDecimal getReturnP50Pct() {
        return returnP50Pct;
    }

    public void setReturnP50Pct(BigDecimal returnP50Pct) {
        this.returnP50Pct = returnP50Pct;
    }

    public BigDecimal getReturnP90Pct() {
        return returnP90Pct;
    }

    public void setReturnP90Pct(BigDecimal returnP90Pct) {
        this.returnP90Pct = returnP90Pct;
    }

    public BigDecimal getExcessP10Pct() {
        return excessP10Pct;
    }

    public void setExcessP10Pct(BigDecimal excessP10Pct) {
        this.excessP10Pct = excessP10Pct;
    }

    public BigDecimal getExcessP50Pct() {
        return excessP50Pct;
    }

    public void setExcessP50Pct(BigDecimal excessP50Pct) {
        this.excessP50Pct = excessP50Pct;
    }

    public BigDecimal getExcessP90Pct() {
        return excessP90Pct;
    }

    public void setExcessP90Pct(BigDecimal excessP90Pct) {
        this.excessP90Pct = excessP90Pct;
    }

    public BigDecimal getUpsideProbabilityPct() {
        return upsideProbabilityPct;
    }

    public void setUpsideProbabilityPct(BigDecimal upsideProbabilityPct) {
        this.upsideProbabilityPct = upsideProbabilityPct;
    }

    public BigDecimal getCalibratedUpsideProbabilityPct() {
        return calibratedUpsideProbabilityPct;
    }

    public void setCalibratedUpsideProbabilityPct(BigDecimal calibratedUpsideProbabilityPct) {
        this.calibratedUpsideProbabilityPct = calibratedUpsideProbabilityPct;
    }

    public BigDecimal getDownsideProbabilityPct() {
        return downsideProbabilityPct;
    }

    public void setDownsideProbabilityPct(BigDecimal downsideProbabilityPct) {
        this.downsideProbabilityPct = downsideProbabilityPct;
    }

    public BigDecimal getDrawdownRiskPct() {
        return drawdownRiskPct;
    }

    public void setDrawdownRiskPct(BigDecimal drawdownRiskPct) {
        this.drawdownRiskPct = drawdownRiskPct;
    }

    public Integer getConfidence() {
        return confidence;
    }

    public void setConfidence(Integer confidence) {
        this.confidence = confidence;
    }

    public Integer getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(Integer sampleCount) {
        this.sampleCount = sampleCount;
    }

    public Integer getSectorSampleCount() {
        return sectorSampleCount;
    }

    public void setSectorSampleCount(Integer sectorSampleCount) {
        this.sectorSampleCount = sectorSampleCount;
    }

    public Integer getScoreBucket() {
        return scoreBucket;
    }

    public void setScoreBucket(Integer scoreBucket) {
        this.scoreBucket = scoreBucket;
    }

    public Integer getCalibrationBucket() {
        return calibrationBucket;
    }

    public void setCalibrationBucket(Integer calibrationBucket) {
        this.calibrationBucket = calibrationBucket;
    }

    public BigDecimal getCalibrationErrorPct() {
        return calibrationErrorPct;
    }

    public void setCalibrationErrorPct(BigDecimal calibrationErrorPct) {
        this.calibrationErrorPct = calibrationErrorPct;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
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
