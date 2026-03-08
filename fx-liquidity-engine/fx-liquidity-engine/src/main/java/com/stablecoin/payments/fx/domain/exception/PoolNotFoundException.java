package com.stablecoin.payments.fx.domain.exception;

import java.util.UUID;

public class PoolNotFoundException extends RuntimeException {

    private PoolNotFoundException(String message) {
        super(message);
    }

    public static PoolNotFoundException withId(UUID poolId) {
        return new PoolNotFoundException("Liquidity pool not found: " + poolId);
    }

    public static PoolNotFoundException forCorridor(String fromCurrency, String toCurrency) {
        return new PoolNotFoundException(
                "No liquidity pool for corridor %s:%s".formatted(fromCurrency, toCurrency));
    }
}
