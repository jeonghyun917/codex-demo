package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class StockFactorExposureSnapshot {

    private String indexCode;
    private LocalDate signalDate;
    private String symbol;
    private String factor;
    private Integer rawScore;
    private BigDecimal exposureScore;
    private String sector;
    private BigDecimal marketCap;
    private Integer dataQualityScore;
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

    public String getFactor() {
        return factor;
    }

    public void setFactor(String factor) {
        this.factor = factor;
    }

    public Integer getRawScore() {
        return rawScore;
    }

    public void setRawScore(Integer rawScore) {
        this.rawScore = rawScore;
    }

    public BigDecimal getExposureScore() {
        return exposureScore;
    }

    public void setExposureScore(BigDecimal exposureScore) {
        this.exposureScore = exposureScore;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public BigDecimal getMarketCap() {
        return marketCap;
    }

    public void setMarketCap(BigDecimal marketCap) {
        this.marketCap = marketCap;
    }

    public Integer getDataQualityScore() {
        return dataQualityScore;
    }

    public void setDataQualityScore(Integer dataQualityScore) {
        this.dataQualityScore = dataQualityScore;
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
