package com.kingyurina.demo.stock;

public record TossInvestResponse(int statusCode, String body, String errorMessage) {

    public boolean success() {
        return statusCode >= 200 && statusCode < 300 && body != null && !body.isBlank();
    }
}
