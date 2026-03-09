package com.stablecoin.payments.offramp.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StablecoinRedeemedEvent(
        UUID redemptionId,
        UUID payoutId,
        UUID paymentId,
        UUID correlationId,
        String stablecoin,
        BigDecimal redeemedAmount,
        BigDecimal fiatReceived,
        String fiatCurrency,
        Instant redeemedAt
) {}
