package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class StockMacroRegimeSnapshot {

    private String indexCode;
    private LocalDate snapshotDate;
    private String regimeLabel;
    private Integer macroScore;
    private Integer trendScore;
    private Integer volatilityScore;
    private Integer breadthScore;
    private Integer liquidityScore;
    private Integer dollarScore;
    private Integer rateScore;
    private BigDecimal benchmarkReturn20dPct;
    private BigDecimal benchmarkReturn60dPct;
    private BigDecimal realizedVolatility20dPct;
    private BigDecimal realizedVolatility60dPct;
    private BigDecimal breadthAdvancerPct;
    private Integer benchmarkCoverageCount;
    private BigDecimal vixLevel;
    private BigDecimal vixChange20dPct;
    private BigDecimal dollarProxyReturn20dPct;
    private BigDecimal rateProxyReturn20dPct;
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

    public String getRegimeLabel() {
        return regimeLabel;
    }

    public void setRegimeLabel(String regimeLabel) {
        this.regimeLabel = regimeLabel;
    }

    public Integer getMacroScore() {
        return macroScore;
    }

    public void setMacroScore(Integer macroScore) {
        this.macroScore = macroScore;
    }

    public Integer getTrendScore() {
        return trendScore;
    }

    public void setTrendScore(Integer trendScore) {
        this.trendScore = trendScore;
    }

    public Integer getVolatilityScore() {
        return volatilityScore;
    }

    public void setVolatilityScore(Integer volatilityScore) {
        this.volatilityScore = volatilityScore;
    }

    public Integer getBreadthScore() {
        return breadthScore;
    }

    public void setBreadthScore(Integer breadthScore) {
        this.breadthScore = breadthScore;
    }

    public Integer getLiquidityScore() {
        return liquidityScore;
    }

    public void setLiquidityScore(Integer liquidityScore) {
        this.liquidityScore = liquidityScore;
    }

    public Integer getDollarScore() {
        return dollarScore;
    }

    public void setDollarScore(Integer dollarScore) {
        this.dollarScore = dollarScore;
    }

    public Integer getRateScore() {
        return rateScore;
    }

    public void setRateScore(Integer rateScore) {
        this.rateScore = rateScore;
    }

    public BigDecimal getBenchmarkReturn20dPct() {
        return benchmarkReturn20dPct;
    }

    public void setBenchmarkReturn20dPct(BigDecimal benchmarkReturn20dPct) {
        this.benchmarkReturn20dPct = benchmarkReturn20dPct;
    }

    public BigDecimal getBenchmarkReturn60dPct() {
        return benchmarkReturn60dPct;
    }

    public void setBenchmarkReturn60dPct(BigDecimal benchmarkReturn60dPct) {
        this.benchmarkReturn60dPct = benchmarkReturn60dPct;
    }

    public BigDecimal getRealizedVolatility20dPct() {
        return realizedVolatility20dPct;
    }

    public void setRealizedVolatility20dPct(BigDecimal realizedVolatility20dPct) {
        this.realizedVolatility20dPct = realizedVolatility20dPct;
    }

    public BigDecimal getRealizedVolatility60dPct() {
        return realizedVolatility60dPct;
    }

    public void setRealizedVolatility60dPct(BigDecimal realizedVolatility60dPct) {
        this.realizedVolatility60dPct = realizedVolatility60dPct;
    }

    public BigDecimal getBreadthAdvancerPct() {
        return breadthAdvancerPct;
    }

    public void setBreadthAdvancerPct(BigDecimal breadthAdvancerPct) {
        this.breadthAdvancerPct = breadthAdvancerPct;
    }

    public Integer getBenchmarkCoverageCount() {
        return benchmarkCoverageCount;
    }

    public void setBenchmarkCoverageCount(Integer benchmarkCoverageCount) {
        this.benchmarkCoverageCount = benchmarkCoverageCount;
    }

    public BigDecimal getVixLevel() {
        return vixLevel;
    }

    public void setVixLevel(BigDecimal vixLevel) {
        this.vixLevel = vixLevel;
    }

    public BigDecimal getVixChange20dPct() {
        return vixChange20dPct;
    }

    public void setVixChange20dPct(BigDecimal vixChange20dPct) {
        this.vixChange20dPct = vixChange20dPct;
    }

    public BigDecimal getDollarProxyReturn20dPct() {
        return dollarProxyReturn20dPct;
    }

    public void setDollarProxyReturn20dPct(BigDecimal dollarProxyReturn20dPct) {
        this.dollarProxyReturn20dPct = dollarProxyReturn20dPct;
    }

    public BigDecimal getRateProxyReturn20dPct() {
        return rateProxyReturn20dPct;
    }

    public void setRateProxyReturn20dPct(BigDecimal rateProxyReturn20dPct) {
        this.rateProxyReturn20dPct = rateProxyReturn20dPct;
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
