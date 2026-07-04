package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class StockRiskSnapshot {

    private String symbol;
    private LocalDate signalDate;
    private BigDecimal beta;
    private BigDecimal volatilityPct;
    private BigDecimal avgDollarVolume;
    private Integer observations;
    private String source;
    private LocalDateTime calculatedAt;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public LocalDate getSignalDate() {
        return signalDate;
    }

    public void setSignalDate(LocalDate signalDate) {
        this.signalDate = signalDate;
    }

    public BigDecimal getBeta() {
        return beta;
    }

    public void setBeta(BigDecimal beta) {
        this.beta = beta;
    }

    public BigDecimal getVolatilityPct() {
        return volatilityPct;
    }

    public void setVolatilityPct(BigDecimal volatilityPct) {
        this.volatilityPct = volatilityPct;
    }

    public BigDecimal getAvgDollarVolume() {
        return avgDollarVolume;
    }

    public void setAvgDollarVolume(BigDecimal avgDollarVolume) {
        this.avgDollarVolume = avgDollarVolume;
    }

    public Integer getObservations() {
        return observations;
    }

    public void setObservations(Integer observations) {
        this.observations = observations;
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
