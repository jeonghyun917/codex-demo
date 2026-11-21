package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class StockQuoteCache {

    private LocalDateTime priceAsOf;
    private String priceSource;
    private String priceKind;
    private String priceBasis;

    public LocalDateTime getPriceAsOf() { return priceAsOf; }
    public void setPriceAsOf(LocalDateTime value) { priceAsOf = value; }

    public String getPriceSource() { return priceSource; }
    public void setPriceSource(String value) { priceSource = value; }

    public String getPriceKind() { return priceKind; }
    public void setPriceKind(String value) { priceKind = value; }

    public String getPriceBasis() { return priceBasis; }
    public void setPriceBasis(String value) { priceBasis = value; }


    private String symbol;
    private BigDecimal currentPrice;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal previousClose;
    private LocalDateTime quoteTime;
    private String rawJson;
    private LocalDateTime fetchedAt;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
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

    public BigDecimal getPreviousClose() {
        return previousClose;
    }

    public void setPreviousClose(BigDecimal previousClose) {
        this.previousClose = previousClose;
    }

    public LocalDateTime getQuoteTime() {
        return quoteTime;
    }

    public void setQuoteTime(LocalDateTime quoteTime) {
        this.quoteTime = quoteTime;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(LocalDateTime fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
