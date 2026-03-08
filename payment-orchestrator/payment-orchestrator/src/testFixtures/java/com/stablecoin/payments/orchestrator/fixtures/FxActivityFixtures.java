package com.stablecoin.payments.orchestrator.fixtures;

import com.stablecoin.payments.fx.api.response.FxQuoteResponse;
import com.stablecoin.payments.fx.api.response.FxRateLockResponse;
import com.stablecoin.payments.orchestrator.domain.workflow.activity.FxLockRequest;

import java.math.BigDecimal;
import java.time.Instant;

import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.IDEMPOTENCY_KEY;
import static com.stablecoin.payments.orchestrator.fixtures.WorkflowFixtures.LOCK_ID;
import static com.stablecoin.payments.orchestrator.fixtures.WorkflowFixtures.PAYMENT_ID;
import static com.stablecoin.payments.orchestrator.fixtures.WorkflowFixtures.QUOTE_ID;

/**
 * Test fixture factory methods for FX activity DTOs.
 */
public final class FxActivityFixtures {

    private FxActivityFixtures() {}

    public static FxLockRequest aFxLockRequest() {
        return new FxLockRequest(
                IDEMPOTENCY_KEY, PAYMENT_ID,
                "USD", "EUR", new BigDecimal("1000.00"),
                "US", "DE");
    }

    public static FxQuoteResponse aQuoteResponse() {
        return new FxQuoteResponse(
                QUOTE_ID, "USD", "EUR",
                new BigDecimal("1000.00"), new BigDecimal("920.00"),
                new BigDecimal("0.92"), new BigDecimal("1.0869"),
                50, new BigDecimal("5.00"), "refinitiv",
                Instant.now(), Instant.now().plusSeconds(300));
    }

    public static FxRateLockResponse aLockResponse() {
        return new FxRateLockResponse(
                LOCK_ID, QUOTE_ID, PAYMENT_ID,
                "USD", "EUR",
                new BigDecimal("1000.00"), new BigDecimal("920.00"),
                new BigDecimal("0.92"),
                50, new BigDecimal("5.00"),
                Instant.now(), Instant.now().plusSeconds(300));
    }
}
