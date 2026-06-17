package com.kingyurina.demo.stock;

import java.time.LocalDate;

public class StockBacktestCoverage {

    private String symbol;
    private String name;
    private String sector;
    private Integer candleCount;
    private LocalDate firstTradeDate;
    private LocalDate lastTradeDate;

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

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public Integer getCandleCount() {
        return candleCount;
    }

    public void setCandleCount(Integer candleCount) {
        this.candleCount = candleCount;
    }

    public LocalDate getFirstTradeDate() {
        return firstTradeDate;
    }

    public void setFirstTradeDate(LocalDate firstTradeDate) {
        this.firstTradeDate = firstTradeDate;
    }

    public LocalDate getLastTradeDate() {
        return lastTradeDate;
    }

    public void setLastTradeDate(LocalDate lastTradeDate) {
        this.lastTradeDate = lastTradeDate;
    }
}
