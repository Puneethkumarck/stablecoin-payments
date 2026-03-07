package com.stablecoin.payments.fx.fixtures;

import com.stablecoin.payments.fx.domain.model.LiquidityPool;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class LiquidityPoolFixtures {

    private LiquidityPoolFixtures() {}

    public static LiquidityPool aUsdEurPool() {
        return new LiquidityPool(
                UUID.randomUUID(), "USD", "EUR",
                new BigDecimal("1000000.00000000"), BigDecimal.ZERO,
                new BigDecimal("100000.00000000"), new BigDecimal("5000000.00000000"),
                Instant.now()
        );
    }
}
