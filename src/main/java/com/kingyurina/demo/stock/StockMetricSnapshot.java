package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class StockMetricSnapshot {

    private String symbol;
    private LocalDate metricDate;
    private BigDecimal peNormalizedAnnual;
    private BigDecimal pbAnnual;
    private BigDecimal roeTtm;
    private BigDecimal epsTtm;
    private BigDecimal week52High;
    private BigDecimal week52Low;
    private String rawJson;
    private LocalDateTime fetchedAt;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public LocalDate getMetricDate() {
        return metricDate;
    }

    public void setMetricDate(LocalDate metricDate) {
        this.metricDate = metricDate;
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

    public BigDecimal getEpsTtm() {
        return epsTtm;
    }

    public void setEpsTtm(BigDecimal epsTtm) {
        this.epsTtm = epsTtm;
    }

    public BigDecimal getWeek52High() {
        return week52High;
    }

    public void setWeek52High(BigDecimal week52High) {
        this.week52High = week52High;
    }

    public BigDecimal getWeek52Low() {
        return week52Low;
    }

    public void setWeek52Low(BigDecimal week52Low) {
        this.week52Low = week52Low;
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
