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
}
