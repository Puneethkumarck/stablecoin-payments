package com.stablecoin.payments.fx.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FxRateLockResponse(
        UUID lockId,
        UUID quoteId,
        UUID paymentId,
        String fromCurrency,
        String toCurrency,
        BigDecimal sourceAmount,
        BigDecimal targetAmount,
        BigDecimal lockedRate,
        int feeBps,
        BigDecimal feeAmount,
        Instant lockedAt,
        Instant expiresAt
) {}
