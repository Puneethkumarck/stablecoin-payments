package com.stablecoin.payments.fx.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public record RateSnapshot(
        String fromCurrency,
        String toCurrency,
        BigDecimal rate,
        BigDecimal bid,
        BigDecimal ask,
        String provider,
        RateSourceType sourceType,
        Instant recordedAt
) {

    public static RateSnapshot fromCorridorRate(CorridorRate corridorRate, RateSourceType sourceType) {
        return new RateSnapshot(
                corridorRate.fromCurrency(),
                corridorRate.toCurrency(),
                corridorRate.rate(),
                null,
                null,
                corridorRate.provider(),
                sourceType,
                Instant.now()
        );
    }
}
