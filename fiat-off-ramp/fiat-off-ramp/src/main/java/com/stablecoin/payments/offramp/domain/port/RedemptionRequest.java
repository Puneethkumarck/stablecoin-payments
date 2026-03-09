package com.stablecoin.payments.offramp.domain.port;

import java.math.BigDecimal;
import java.util.UUID;

public record RedemptionRequest(
        UUID payoutId,
        String stablecoin,
        BigDecimal amount
) {}
