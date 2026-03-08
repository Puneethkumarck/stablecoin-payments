package com.stablecoin.payments.fx.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FxQuoteResponse(
        UUID quoteId,
        String fromCurrency,
        String toCurrency,
        BigDecimal sourceAmount,
        BigDecimal targetAmount,
        BigDecimal rate,
        BigDecimal inverseRate,
        int feeBps,
        BigDecimal feeAmount,
        String provider,
        Instant createdAt,
        Instant expiresAt
) {}
