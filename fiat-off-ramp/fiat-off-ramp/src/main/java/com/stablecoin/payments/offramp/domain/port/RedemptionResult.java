package com.stablecoin.payments.offramp.domain.port;

import java.math.BigDecimal;
import java.time.Instant;

public record RedemptionResult(
        String partnerReference,
        BigDecimal fiatReceived,
        String fiatCurrency,
        Instant redeemedAt
) {}
