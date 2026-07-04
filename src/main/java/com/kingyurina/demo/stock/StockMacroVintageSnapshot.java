package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;

public class StockMacroVintageSnapshot {

    private String indexCode;
    private String seriesCode;
    private LocalDate observationDate;
    private LocalDate realtimeStart;
    private LocalDate realtimeEnd;
    private BigDecimal value;
    private String source;

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

    public LocalDate getObservationDate() {
        return observationDate;
    }

    public void setObservationDate(LocalDate observationDate) {
        this.observationDate = observationDate;
    }

    public LocalDate getRealtimeStart() {
        return realtimeStart;
    }

    public void setRealtimeStart(LocalDate realtimeStart) {
        this.realtimeStart = realtimeStart;
    }

    public LocalDate getRealtimeEnd() {
        return realtimeEnd;
    }

    public void setRealtimeEnd(LocalDate realtimeEnd) {
        this.realtimeEnd = realtimeEnd;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
