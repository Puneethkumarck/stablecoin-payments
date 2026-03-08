package com.stablecoin.payments.fx.domain.exception;

public class CorridorNotSupportedException extends RuntimeException {

    private CorridorNotSupportedException(String message) {
        super(message);
    }

    public static CorridorNotSupportedException forCurrencies(String fromCurrency, String toCurrency) {
        return new CorridorNotSupportedException(
                "Corridor not supported: %s:%s".formatted(fromCurrency, toCurrency));
    }
}
