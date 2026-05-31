package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class StockEpsSurprise {

    private String symbol;
    private LocalDate periodDate;
    private Integer fiscalYear;
    private Integer fiscalQuarter;
    private BigDecimal actual;
    private BigDecimal estimate;
    private BigDecimal surprise;
    private BigDecimal surprisePercent;
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

    public Integer getFiscalYear() {
        return fiscalYear;
    }

    public void setFiscalYear(Integer fiscalYear) {
        this.fiscalYear = fiscalYear;
    }

    public Integer getFiscalQuarter() {
        return fiscalQuarter;
    }

    public void setFiscalQuarter(Integer fiscalQuarter) {
        this.fiscalQuarter = fiscalQuarter;
    }

    public BigDecimal getActual() {
        return actual;
    }

    public void setActual(BigDecimal actual) {
        this.actual = actual;
    }

    public BigDecimal getEstimate() {
        return estimate;
    }

    public void setEstimate(BigDecimal estimate) {
        this.estimate = estimate;
    }

    public BigDecimal getSurprise() {
        return surprise;
    }

    public void setSurprise(BigDecimal surprise) {
        this.surprise = surprise;
    }

    public BigDecimal getSurprisePercent() {
        return surprisePercent;
    }

    public void setSurprisePercent(BigDecimal surprisePercent) {
        this.surprisePercent = surprisePercent;
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
