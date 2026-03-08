package com.stablecoin.payments.fx.api.response;

import java.math.BigDecimal;
import java.time.Instant;

public record CorridorResponse(
        String fromCurrency,
        String toCurrency,
        BigDecimal indicativeRate,
        int feeBps,
        int spreadBps,
        String provider,
        Instant rateUpdatedAt
) {}
