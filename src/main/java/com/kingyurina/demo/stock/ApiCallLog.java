package com.kingyurina.demo.stock;

public class ApiCallLog {

    private String provider;
    private String endpoint;
    private String symbol;
    private Integer statusCode;
    private String errorMessage;

    public ApiCallLog(String provider, String endpoint, String symbol, Integer statusCode, String errorMessage) {
        this.provider = provider;
        this.endpoint = endpoint;
        this.symbol = symbol;
        this.statusCode = statusCode;
        this.errorMessage = errorMessage;
    }

    public String getProvider() {
        return provider;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getSymbol() {
        return symbol;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
