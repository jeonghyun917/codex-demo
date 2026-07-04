package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class StockBenchmarkReturn {

    private String indexCode;
    private LocalDate tradeDate;
    private BigDecimal benchmarkLevel;
    private BigDecimal returnPct;
    private BigDecimal totalMarketCapUsd;
    private Integer constituentCount;
    private Integer coverageCount;
    private String source;
    private LocalDateTime calculatedAt;

    public String getIndexCode() {
        return indexCode;
    }

    public void setIndexCode(String indexCode) {
        this.indexCode = indexCode;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    public BigDecimal getBenchmarkLevel() {
        return benchmarkLevel;
    }

    public void setBenchmarkLevel(BigDecimal benchmarkLevel) {
        this.benchmarkLevel = benchmarkLevel;
    }

    public BigDecimal getReturnPct() {
        return returnPct;
    }

    public void setReturnPct(BigDecimal returnPct) {
        this.returnPct = returnPct;
    }

    public BigDecimal getTotalMarketCapUsd() {
        return totalMarketCapUsd;
    }

    public void setTotalMarketCapUsd(BigDecimal totalMarketCapUsd) {
        this.totalMarketCapUsd = totalMarketCapUsd;
    }

    public Integer getConstituentCount() {
        return constituentCount;
    }

    public void setConstituentCount(Integer constituentCount) {
        this.constituentCount = constituentCount;
    }

    public Integer getCoverageCount() {
        return coverageCount;
    }

    public void setCoverageCount(Integer coverageCount) {
        this.coverageCount = coverageCount;
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
