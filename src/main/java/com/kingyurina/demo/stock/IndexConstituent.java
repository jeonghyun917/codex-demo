package com.kingyurina.demo.stock;

public class IndexConstituent {

    private String indexCode;
    private String symbol;
    private String name;
    private String exchange;
    private String sector;
    private boolean currentMember;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public boolean isCurrentMember() {
        return currentMember;
    }

    public void setCurrentMember(boolean currentMember) {
        this.currentMember = currentMember;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
