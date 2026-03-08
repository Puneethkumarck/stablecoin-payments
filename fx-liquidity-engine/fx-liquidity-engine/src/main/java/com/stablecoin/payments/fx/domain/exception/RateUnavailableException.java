package com.stablecoin.payments.fx.domain.exception;

public class RateUnavailableException extends RuntimeException {

    private RateUnavailableException(String message) {
        super(message);
    }

    public static RateUnavailableException forCorridor(String fromCurrency, String toCurrency) {
        return new RateUnavailableException(
                "No rate available for corridor %s:%s".formatted(fromCurrency, toCurrency));
    }
}
