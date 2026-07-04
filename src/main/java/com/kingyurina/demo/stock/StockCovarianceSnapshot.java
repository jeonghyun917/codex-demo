package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class StockCovarianceSnapshot {

    private String indexCode;
    private LocalDate snapshotDate;
    private String symbolA;
    private String symbolB;
    private BigDecimal correlation;
    private BigDecimal covariance;
    private BigDecimal volatilityAPct;
    private BigDecimal volatilityBPct;
    private Integer observations;
    private Integer lookbackDays;
    private String source;
    private LocalDateTime calculatedAt;

    public String getIndexCode() {
        return indexCode;
    }

    public void setIndexCode(String indexCode) {
        this.indexCode = indexCode;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public String getSymbolA() {
        return symbolA;
    }

    public void setSymbolA(String symbolA) {
        this.symbolA = symbolA;
    }

    public String getSymbolB() {
        return symbolB;
    }

    public void setSymbolB(String symbolB) {
        this.symbolB = symbolB;
    }

    public BigDecimal getCorrelation() {
        return correlation;
    }

    public void setCorrelation(BigDecimal correlation) {
        this.correlation = correlation;
    }

    public BigDecimal getCovariance() {
        return covariance;
    }

    public void setCovariance(BigDecimal covariance) {
        this.covariance = covariance;
    }

    public BigDecimal getVolatilityAPct() {
        return volatilityAPct;
    }

    public void setVolatilityAPct(BigDecimal volatilityAPct) {
        this.volatilityAPct = volatilityAPct;
    }

    public BigDecimal getVolatilityBPct() {
        return volatilityBPct;
    }

    public void setVolatilityBPct(BigDecimal volatilityBPct) {
        this.volatilityBPct = volatilityBPct;
    }

    public Integer getObservations() {
        return observations;
    }

    public void setObservations(Integer observations) {
        this.observations = observations;
    }

    public Integer getLookbackDays() {
        return lookbackDays;
    }

    public void setLookbackDays(Integer lookbackDays) {
        this.lookbackDays = lookbackDays;
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
