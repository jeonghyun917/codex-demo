package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class StockExpectedReturnCalibration {

    private String indexCode;
    private String modelVersion;
    private Integer horizonDays;
    private Integer probabilityBucket;
    private Integer sampleCount;
    private BigDecimal averagePredictedUpsidePct;
    private BigDecimal actualUpsideRatePct;
    private BigDecimal calibrationErrorPct;
    private BigDecimal brierScore;
    private String source;
    private LocalDateTime calculatedAt;

    public String getIndexCode() {
        return indexCode;
    }

    public void setIndexCode(String indexCode) {
        this.indexCode = indexCode;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public Integer getHorizonDays() {
        return horizonDays;
    }

    public void setHorizonDays(Integer horizonDays) {
        this.horizonDays = horizonDays;
    }

    public Integer getProbabilityBucket() {
        return probabilityBucket;
    }

    public void setProbabilityBucket(Integer probabilityBucket) {
        this.probabilityBucket = probabilityBucket;
    }

    public Integer getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(Integer sampleCount) {
        this.sampleCount = sampleCount;
    }

    public BigDecimal getAveragePredictedUpsidePct() {
        return averagePredictedUpsidePct;
    }

    public void setAveragePredictedUpsidePct(BigDecimal averagePredictedUpsidePct) {
        this.averagePredictedUpsidePct = averagePredictedUpsidePct;
    }

    public BigDecimal getActualUpsideRatePct() {
        return actualUpsideRatePct;
    }

    public void setActualUpsideRatePct(BigDecimal actualUpsideRatePct) {
        this.actualUpsideRatePct = actualUpsideRatePct;
    }

    public BigDecimal getCalibrationErrorPct() {
        return calibrationErrorPct;
    }

    public void setCalibrationErrorPct(BigDecimal calibrationErrorPct) {
        this.calibrationErrorPct = calibrationErrorPct;
    }

    public BigDecimal getBrierScore() {
        return brierScore;
    }

    public void setBrierScore(BigDecimal brierScore) {
        this.brierScore = brierScore;
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
