package com.kingyurina.demo.stock;

import java.math.BigDecimal;

public class StockPeerComparison {

    private String symbol;
    private String name;
    private String finnhubIndustry;
    private BigDecimal marketCap;
    private BigDecimal currentPrice;
    private BigDecimal peNormalizedAnnual;
    private BigDecimal pbAnnual;
    private BigDecimal roeTtm;

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

    public String getFinnhubIndustry() {
        return finnhubIndustry;
    }

    public void setFinnhubIndustry(String finnhubIndustry) {
        this.finnhubIndustry = finnhubIndustry;
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
}
