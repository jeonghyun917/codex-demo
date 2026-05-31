package com.kingyurina.demo.stock;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class StockRecommendationTrend {

    private String symbol;
    private LocalDate periodDate;
    private Integer strongBuy;
    private Integer buy;
    private Integer hold;
    private Integer sell;
    private Integer strongSell;
    private String rawJson;
    private LocalDateTime fetchedAt;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public LocalDate getPeriodDate() {
        return periodDate;
    }

    public void setPeriodDate(LocalDate periodDate) {
        this.periodDate = periodDate;
    }

    public Integer getStrongBuy() {
        return strongBuy;
    }

    public void setStrongBuy(Integer strongBuy) {
        this.strongBuy = strongBuy;
    }

    public Integer getBuy() {
        return buy;
    }

    public void setBuy(Integer buy) {
        this.buy = buy;
    }

    public Integer getHold() {
        return hold;
    }

    public void setHold(Integer hold) {
        this.hold = hold;
    }

    public Integer getSell() {
        return sell;
    }

    public void setSell(Integer sell) {
        this.sell = sell;
    }

    public Integer getStrongSell() {
        return strongSell;
    }

    public void setStrongSell(Integer strongSell) {
        this.strongSell = strongSell;
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
