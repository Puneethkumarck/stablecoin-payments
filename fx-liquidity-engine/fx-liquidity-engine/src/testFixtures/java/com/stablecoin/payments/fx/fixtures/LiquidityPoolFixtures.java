package com.stablecoin.payments.fx.fixtures;

import com.stablecoin.payments.fx.domain.model.LiquidityPool;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class LiquidityPoolFixtures {

    public static final UUID POOL_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");

    private LiquidityPoolFixtures() {}

    public static LiquidityPool aUsdEurPool() {
        return new LiquidityPool(
                UUID.randomUUID(), "USD", "EUR",
                new BigDecimal("1000000.00000000"), BigDecimal.ZERO,
                new BigDecimal("100000.00000000"), new BigDecimal("5000000.00000000"),
                Instant.now()
        );
    }

    public static LiquidityPool aPoolWithId(UUID poolId) {
        return new LiquidityPool(
                poolId, "USD", "EUR",
                new BigDecimal("1000000.00000000"), BigDecimal.ZERO,
                new BigDecimal("100000.00000000"), new BigDecimal("5000000.00000000"),
                Instant.now()
        );
    }

    public static LiquidityPool aPoolWithLowBalance() {
        return new LiquidityPool(
                UUID.randomUUID(), "USD", "EUR",
                new BigDecimal("100.00000000"), BigDecimal.ZERO,
                new BigDecimal("100000.00000000"), new BigDecimal("5000000.00000000"),
                Instant.now()
        );
    }

    public static LiquidityPool aGbpEurPool() {
        return new LiquidityPool(
                UUID.randomUUID(), "GBP", "EUR",
                new BigDecimal("500000.00000000"), BigDecimal.ZERO,
                new BigDecimal("50000.00000000"), new BigDecimal("2000000.00000000"),
                Instant.now()
        );
    }
}
