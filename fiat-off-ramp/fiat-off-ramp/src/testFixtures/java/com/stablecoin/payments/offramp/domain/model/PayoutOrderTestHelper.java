package com.stablecoin.payments.offramp.domain.model;

import java.time.Instant;

/**
 * Test-only helper that accesses the package-private {@code toBuilder()} on {@link PayoutOrder}.
 * Lives in the same package to reach the builder.
 */
public final class PayoutOrderTestHelper {

    private PayoutOrderTestHelper() {}

    /**
     * Returns a copy of the given order with a different {@code updatedAt} timestamp.
     * Useful for simulating stuck payouts in monitor tests.
     */
    public static PayoutOrder withUpdatedAt(PayoutOrder order, Instant updatedAt) {
        return order.toBuilder().updatedAt(updatedAt).build();
    }
}
