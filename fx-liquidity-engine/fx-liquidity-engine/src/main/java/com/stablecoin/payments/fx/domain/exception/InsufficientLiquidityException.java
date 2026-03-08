package com.stablecoin.payments.fx.domain.exception;

import java.math.BigDecimal;

public class InsufficientLiquidityException extends RuntimeException {

    private InsufficientLiquidityException(String message) {
        super(message);
    }

    public static InsufficientLiquidityException forCorridor(String fromCurrency, String toCurrency,
                                                              BigDecimal requested, BigDecimal available) {
        return new InsufficientLiquidityException(
                "Insufficient liquidity for %s:%s — requested=%s, available=%s"
                        .formatted(fromCurrency, toCurrency, requested, available));
    }
}
