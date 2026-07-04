package com.kingyurina.demo.stock;

import java.math.BigDecimal;
import java.time.LocalDate;

public class StockSharesOutstandingSnapshot {

    private String indexCode;
    private String symbol;
    private LocalDate snapshotDate;
    private BigDecimal sharesOutstanding;
    private String source;

    public String getIndexCode() {
        return indexCode;
    }

    public void setIndexCode(String indexCode) {
        this.indexCode = indexCode;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public BigDecimal getSharesOutstanding() {
        return sharesOutstanding;
    }

    public void setSharesOutstanding(BigDecimal sharesOutstanding) {
        this.sharesOutstanding = sharesOutstanding;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
