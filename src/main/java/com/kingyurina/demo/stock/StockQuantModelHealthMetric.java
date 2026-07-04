package com.kingyurina.demo.stock;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class StockQuantModelHealthMetric {

    private String layer;
    private String metricKey;
    private Long rowCount;
    private Long symbolCount;
    private Long dateCount;
    private LocalDate latestDate;
    private LocalDateTime latestCalculatedAt;
    private String note;

    public String getLayer() {
        return layer;
    }

    public void setLayer(String layer) {
        this.layer = layer;
    }

    public String getMetricKey() {
        return metricKey;
    }

    public void setMetricKey(String metricKey) {
        this.metricKey = metricKey;
    }

    public Long getRowCount() {
        return rowCount;
    }

    public void setRowCount(Long rowCount) {
        this.rowCount = rowCount;
    }

    public Long getSymbolCount() {
        return symbolCount;
    }

    public void setSymbolCount(Long symbolCount) {
        this.symbolCount = symbolCount;
    }

    public Long getDateCount() {
        return dateCount;
    }

    public void setDateCount(Long dateCount) {
        this.dateCount = dateCount;
    }

    public LocalDate getLatestDate() {
        return latestDate;
    }

    public void setLatestDate(LocalDate latestDate) {
        this.latestDate = latestDate;
    }

    public LocalDateTime getLatestCalculatedAt() {
        return latestCalculatedAt;
    }

    public void setLatestCalculatedAt(LocalDateTime latestCalculatedAt) {
        this.latestCalculatedAt = latestCalculatedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
