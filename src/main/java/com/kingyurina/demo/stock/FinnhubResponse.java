package com.kingyurina.demo.stock;

public record FinnhubResponse(int statusCode, String body, String errorMessage) {

    public boolean success() {
        return statusCode >= 200 && statusCode < 300 && errorMessage == null;
    }
}
