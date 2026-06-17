package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class StockBacktestResult {

    private Long snapshotId;
    private String symbol;
    private String name;
    private LocalDate signalDate;
    private Integer horizonDays;
    private LocalDate entryTradeDate;
    private BigDecimal entryClose;
    private LocalDate exitTradeDate;
    private BigDecimal exitClose;
    private BigDecimal forwardReturnPct;
    private Integer integratedScore;
    private Integer valuationScore;
    private Integer qualityScore;
    private Integer growthScore;
    private Integer stabilityScore;
    private Integer earningsScore;
    private Integer analystScore;
    private Integer newsScore;
    private Integer momentumScore;
    private Integer riskScore;
    private Integer institutionScore;
    private Integer dataQualityScore;
    private BigDecimal marketCap;
    private String sector;
    private LocalDateTime calculatedAt;

    public Long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId) {
        this.snapshotId = snapshotId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getSignalDate() {
        return signalDate;
    }

    public void setSignalDate(LocalDate signalDate) {
        this.signalDate = signalDate;
    }

    public Integer getHorizonDays() {
        return horizonDays;
    }

    public void setHorizonDays(Integer horizonDays) {
        this.horizonDays = horizonDays;
    }

    public LocalDate getEntryTradeDate() {
        return entryTradeDate;
    }

    public void setEntryTradeDate(LocalDate entryTradeDate) {
        this.entryTradeDate = entryTradeDate;
    }

    public BigDecimal getEntryClose() {
        return entryClose;
    }

    public void setEntryClose(BigDecimal entryClose) {
        this.entryClose = entryClose;
    }

    public LocalDate getExitTradeDate() {
        return exitTradeDate;
    }

    public void setExitTradeDate(LocalDate exitTradeDate) {
        this.exitTradeDate = exitTradeDate;
    }

    public BigDecimal getExitClose() {
        return exitClose;
    }

    public void setExitClose(BigDecimal exitClose) {
        this.exitClose = exitClose;
    }

    public BigDecimal getForwardReturnPct() {
        return forwardReturnPct;
    }

    public void setForwardReturnPct(BigDecimal forwardReturnPct) {
        this.forwardReturnPct = forwardReturnPct;
    }

    public Integer getIntegratedScore() {
        return integratedScore;
    }

    public void setIntegratedScore(Integer integratedScore) {
        this.integratedScore = integratedScore;
    }

    public Integer getValuationScore() {
        return valuationScore;
    }

    public void setValuationScore(Integer valuationScore) {
        this.valuationScore = valuationScore;
    }

    public Integer getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(Integer qualityScore) {
        this.qualityScore = qualityScore;
    }

    public Integer getGrowthScore() {
        return growthScore;
    }

    public void setGrowthScore(Integer growthScore) {
        this.growthScore = growthScore;
    }

    public Integer getStabilityScore() {
        return stabilityScore;
    }

    public void setStabilityScore(Integer stabilityScore) {
        this.stabilityScore = stabilityScore;
    }

    public Integer getEarningsScore() {
        return earningsScore;
    }

    public void setEarningsScore(Integer earningsScore) {
        this.earningsScore = earningsScore;
    }

    public Integer getAnalystScore() {
        return analystScore;
    }

    public void setAnalystScore(Integer analystScore) {
        this.analystScore = analystScore;
    }

    public Integer getNewsScore() {
        return newsScore;
    }

    public void setNewsScore(Integer newsScore) {
        this.newsScore = newsScore;
    }

    public Integer getMomentumScore() {
        return momentumScore;
    }

    public void setMomentumScore(Integer momentumScore) {
        this.momentumScore = momentumScore;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Integer riskScore) {
        this.riskScore = riskScore;
    }

    public Integer getInstitutionScore() {
        return institutionScore;
    }

    public void setInstitutionScore(Integer institutionScore) {
        this.institutionScore = institutionScore;
    }

    public Integer getDataQualityScore() {
        return dataQualityScore;
    }

    public void setDataQualityScore(Integer dataQualityScore) {
        this.dataQualityScore = dataQualityScore;
    }

    public BigDecimal getMarketCap() {
        return marketCap;
    }

    public void setMarketCap(BigDecimal marketCap) {
        this.marketCap = marketCap;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(LocalDateTime calculatedAt) {
        this.calculatedAt = calculatedAt;
    }
}
