package com.stablecoin.payments.fx.fixtures;

import com.stablecoin.payments.fx.domain.model.FxRateLock;
import com.stablecoin.payments.fx.domain.model.FxRateLockStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class FxRateLockFixtures {

    public static final UUID PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    public static final UUID CORRELATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    public static final String SOURCE_COUNTRY = "US";
    public static final String TARGET_COUNTRY = "DE";

    private FxRateLockFixtures() {}

    public static FxRateLock anActiveLock(UUID quoteId) {
        return new FxRateLock(
                UUID.randomUUID(), quoteId, PAYMENT_ID, CORRELATION_ID,
                "USD", "EUR", new BigDecimal("10000.00000000"), new BigDecimal("9200.00000000"),
                new BigDecimal("0.9200000000"), 30, new BigDecimal("30.00000000"),
                SOURCE_COUNTRY, TARGET_COUNTRY, FxRateLockStatus.ACTIVE,
                Instant.now(), Instant.now().plusSeconds(30), null
        );
    }

    public static FxRateLock anActiveLock(UUID quoteId, UUID paymentId) {
        return new FxRateLock(
                UUID.randomUUID(), quoteId, paymentId, CORRELATION_ID,
                "USD", "EUR", new BigDecimal("10000.00000000"), new BigDecimal("9200.00000000"),
                new BigDecimal("0.9200000000"), 30, new BigDecimal("30.00000000"),
                SOURCE_COUNTRY, TARGET_COUNTRY, FxRateLockStatus.ACTIVE,
                Instant.now(), Instant.now().plusSeconds(30), null
        );
    }
}
