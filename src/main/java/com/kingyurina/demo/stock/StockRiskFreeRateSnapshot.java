package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class StockRiskFreeRateSnapshot {

    private String indexCode;
    private String seriesCode;
    private LocalDate rateDate;
    private BigDecimal annualRatePct;
    private String source;
    private LocalDateTime calculatedAt;

    public String getIndexCode() {
        return indexCode;
    }

    public void setIndexCode(String indexCode) {
        this.indexCode = indexCode;
    }

    public String getSeriesCode() {
        return seriesCode;
    }

    public void setSeriesCode(String seriesCode) {
        this.seriesCode = seriesCode;
    }

    public LocalDate getRateDate() {
        return rateDate;
    }

    public void setRateDate(LocalDate rateDate) {
        this.rateDate = rateDate;
    }

    public BigDecimal getAnnualRatePct() {
        return annualRatePct;
    }

    public void setAnnualRatePct(BigDecimal annualRatePct) {
        this.annualRatePct = annualRatePct;
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
