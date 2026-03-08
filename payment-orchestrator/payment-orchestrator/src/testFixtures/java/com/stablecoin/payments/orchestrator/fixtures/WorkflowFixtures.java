package com.stablecoin.payments.orchestrator.fixtures;

import com.stablecoin.payments.orchestrator.domain.workflow.dto.PaymentRequest;

import java.math.BigDecimal;
import java.util.UUID;

import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.IDEMPOTENCY_KEY;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.RECIPIENT_ID;
import static com.stablecoin.payments.orchestrator.fixtures.PaymentFixtures.SENDER_ID;

/**
 * Test fixture factory methods for Temporal workflow and activity DTOs.
 */
public final class WorkflowFixtures {

    public static final UUID PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID QUOTE_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    public static final UUID LOCK_ID = UUID.fromString("00000000-0000-0000-0000-000000000077");
    public static final UUID CHECK_ID = UUID.fromString("00000000-0000-0000-0000-000000000088");

    private WorkflowFixtures() {}

    public static PaymentRequest aPaymentRequest() {
        return new PaymentRequest(
                PAYMENT_ID,
                IDEMPOTENCY_KEY,
                UUID.fromString("00000000-0000-0000-0000-000000000010"),
                SENDER_ID,
                RECIPIENT_ID,
                new BigDecimal("1000.00"),
                "USD",
                "EUR",
                "US",
                "DE"
        );
    }
}
