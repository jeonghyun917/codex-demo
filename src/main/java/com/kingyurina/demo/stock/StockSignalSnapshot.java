package com.kingyurina.demo.stock;

import java.time.LocalDate;

public class StockSignalSnapshot extends StockSignalLatest {

    private Long id;
    private LocalDate signalDate;
    private String snapshotMode;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getSignalDate() {
        return signalDate;
    }

    public void setSignalDate(LocalDate signalDate) {
        this.signalDate = signalDate;
    }

    public String getSnapshotMode() {
        return snapshotMode;
    }

    public void setSnapshotMode(String snapshotMode) {
        this.snapshotMode = snapshotMode;
    }
}
