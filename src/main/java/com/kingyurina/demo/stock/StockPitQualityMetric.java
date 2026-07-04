package com.kingyurina.demo.stock;

public class StockPitQualityMetric {

    private String layer;
    private Long totalRows;
    private Long officialRows;
    private Long secRows;
    private Long wikipediaRows;
    private Long proxyRows;
    private Double score;

    public String getLayer() {
        return layer;
    }

    public void setLayer(String layer) {
        this.layer = layer;
    }

    public Long getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Long totalRows) {
        this.totalRows = totalRows;
    }

    public Long getOfficialRows() {
        return officialRows;
    }

    public void setOfficialRows(Long officialRows) {
        this.officialRows = officialRows;
    }

    public Long getSecRows() {
        return secRows;
    }

    public void setSecRows(Long secRows) {
        this.secRows = secRows;
    }

    public Long getWikipediaRows() {
        return wikipediaRows;
    }

    public void setWikipediaRows(Long wikipediaRows) {
        this.wikipediaRows = wikipediaRows;
    }

    public Long getProxyRows() {
        return proxyRows;
    }

    public void setProxyRows(Long proxyRows) {
        this.proxyRows = proxyRows;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }
}
