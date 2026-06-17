package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class StockMarketRow {

    private String symbol;
    private String name;
    private String logo;
    private String sector;
    private String industry;
    private BigDecimal marketCap;
    private BigDecimal institutionSharesChangePct;
    private Integer institutionHolderCount;
    private java.time.LocalDate institutionReportQuarter;
    private BigDecimal currentPrice;
    private BigDecimal previousClose;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal peNormalizedAnnual;
    private BigDecimal pbAnnual;
    private BigDecimal roeTtm;
    private Integer analystBullish;
    private Integer analystNeutral;
    private Integer analystBearish;
    private BigDecimal latestSurprisePercent;
    private Integer recentNewsCount;
    private Integer signalScore;
    private String signalConfidence;
    private String signalTone;
    private LocalDateTime signalCalculatedAt;
    private Integer dataQualityScore;
    private String dataQualityLabel;
    private String dataQualityTone;
    private String dataQualityExcludedFieldsJson;
    private Integer valuationScore;
    private Integer qualityScore;
    private Integer growthScore;
    private Integer stabilityScore;
    private Integer earningsScore;
    private Integer analystScore;
    private Integer newsScore;
    private Integer momentumScore;
    private Integer riskScore;

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

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public BigDecimal getMarketCap() {
        return marketCap;
    }

    public void setMarketCap(BigDecimal marketCap) {
        this.marketCap = marketCap;
    }

    public BigDecimal getInstitutionSharesChangePct() {
        return institutionSharesChangePct;
    }

    public void setInstitutionSharesChangePct(BigDecimal institutionSharesChangePct) {
        this.institutionSharesChangePct = institutionSharesChangePct;
    }

    public Integer getInstitutionHolderCount() {
        return institutionHolderCount;
    }

    public void setInstitutionHolderCount(Integer institutionHolderCount) {
        this.institutionHolderCount = institutionHolderCount;
    }

    public java.time.LocalDate getInstitutionReportQuarter() {
        return institutionReportQuarter;
    }

    public void setInstitutionReportQuarter(java.time.LocalDate institutionReportQuarter) {
        this.institutionReportQuarter = institutionReportQuarter;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getPreviousClose() {
        return previousClose;
    }

    public void setPreviousClose(BigDecimal previousClose) {
        this.previousClose = previousClose;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    public BigDecimal getHighPrice() {
        return highPrice;
    }

    public void setHighPrice(BigDecimal highPrice) {
        this.highPrice = highPrice;
    }

    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    public void setLowPrice(BigDecimal lowPrice) {
        this.lowPrice = lowPrice;
    }

    public BigDecimal getPeNormalizedAnnual() {
        return peNormalizedAnnual;
    }

    public void setPeNormalizedAnnual(BigDecimal peNormalizedAnnual) {
        this.peNormalizedAnnual = peNormalizedAnnual;
    }

    public BigDecimal getPbAnnual() {
        return pbAnnual;
    }

    public void setPbAnnual(BigDecimal pbAnnual) {
        this.pbAnnual = pbAnnual;
    }

    public BigDecimal getRoeTtm() {
        return roeTtm;
    }

    public void setRoeTtm(BigDecimal roeTtm) {
        this.roeTtm = roeTtm;
    }

    public Integer getAnalystBullish() {
        return analystBullish;
    }

    public void setAnalystBullish(Integer analystBullish) {
        this.analystBullish = analystBullish;
    }

    public Integer getAnalystNeutral() {
        return analystNeutral;
    }

    public void setAnalystNeutral(Integer analystNeutral) {
        this.analystNeutral = analystNeutral;
    }

    public Integer getAnalystBearish() {
        return analystBearish;
    }

    public void setAnalystBearish(Integer analystBearish) {
        this.analystBearish = analystBearish;
    }

    public BigDecimal getLatestSurprisePercent() {
        return latestSurprisePercent;
    }

    public void setLatestSurprisePercent(BigDecimal latestSurprisePercent) {
        this.latestSurprisePercent = latestSurprisePercent;
    }

    public Integer getRecentNewsCount() {
        return recentNewsCount;
    }

    public void setRecentNewsCount(Integer recentNewsCount) {
        this.recentNewsCount = recentNewsCount;
    }

    public Integer getSignalScore() {
        return signalScore;
    }

    public void setSignalScore(Integer signalScore) {
        this.signalScore = signalScore;
    }

    public String getSignalConfidence() {
        return signalConfidence;
    }

    public void setSignalConfidence(String signalConfidence) {
        this.signalConfidence = signalConfidence;
    }

    public String getSignalTone() {
        return signalTone;
    }

    public void setSignalTone(String signalTone) {
        this.signalTone = signalTone;
    }

    public LocalDateTime getSignalCalculatedAt() {
        return signalCalculatedAt;
    }

    public void setSignalCalculatedAt(LocalDateTime signalCalculatedAt) {
        this.signalCalculatedAt = signalCalculatedAt;
    }

    public Integer getDataQualityScore() {
        return dataQualityScore;
    }

    public void setDataQualityScore(Integer dataQualityScore) {
        this.dataQualityScore = dataQualityScore;
    }

    public String getDataQualityLabel() {
        return dataQualityLabel;
    }

    public void setDataQualityLabel(String dataQualityLabel) {
        this.dataQualityLabel = dataQualityLabel;
    }

    public String getDataQualityTone() {
        return dataQualityTone;
    }

    public void setDataQualityTone(String dataQualityTone) {
        this.dataQualityTone = dataQualityTone;
    }

    public String getDataQualityExcludedFieldsJson() {
        return dataQualityExcludedFieldsJson;
    }

    public void setDataQualityExcludedFieldsJson(String dataQualityExcludedFieldsJson) {
        this.dataQualityExcludedFieldsJson = dataQualityExcludedFieldsJson;
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
}
