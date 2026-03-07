package com.stablecoin.payments.fx.fixtures;

import com.stablecoin.payments.fx.domain.model.FxRateLock;
import com.stablecoin.payments.fx.domain.model.FxRateLockStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class FxRateLockFixtures {

    private FxRateLockFixtures() {}

    public static FxRateLock anActiveLock(UUID quoteId) {
        return new FxRateLock(
                UUID.randomUUID(), quoteId, UUID.randomUUID(), UUID.randomUUID(),
                "USD", "EUR", new BigDecimal("10000.00000000"), new BigDecimal("9200.00000000"),
                new BigDecimal("0.9200000000"), 30, new BigDecimal("30.00000000"),
                "US", "DE", FxRateLockStatus.ACTIVE, Instant.now(), Instant.now().plusSeconds(30), null
        );
    }
}
