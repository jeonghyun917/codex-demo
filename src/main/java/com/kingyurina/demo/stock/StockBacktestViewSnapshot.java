package com.kingyurina.demo.stock;

import java.time.LocalDateTime;

public class StockBacktestViewSnapshot {

    private String indexCode;
    private String viewVersion;
    private LocalDateTime generatedAt;
    private String source;
    private String payloadJson;

    public String getIndexCode() {
        return indexCode;
    }

    public void setIndexCode(String indexCode) {
        this.indexCode = indexCode;
    }

    public String getViewVersion() {
        return viewVersion;
    }

    public void setViewVersion(String viewVersion) {
        this.viewVersion = viewVersion;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }
}
