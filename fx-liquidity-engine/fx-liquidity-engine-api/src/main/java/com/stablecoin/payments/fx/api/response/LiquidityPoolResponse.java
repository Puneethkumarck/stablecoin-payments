package com.stablecoin.payments.fx.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LiquidityPoolResponse(
        UUID poolId,
        String fromCurrency,
        String toCurrency,
        BigDecimal availableBalance,
        BigDecimal reservedBalance,
        BigDecimal minimumThreshold,
        BigDecimal maximumCapacity,
        BigDecimal utilizationPct,
        String status,
        Instant updatedAt
) {}
