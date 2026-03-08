package com.stablecoin.payments.onramp.fixtures;

import com.stablecoin.payments.onramp.domain.model.Money;
import com.stablecoin.payments.onramp.domain.model.Refund;

import java.math.BigDecimal;
import java.util.UUID;

import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.PAYMENT_ID;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.PSP_REFUND_REFERENCE;

public final class RefundFixtures {

    private RefundFixtures() {}

    // -- Constants --------------------------------------------------------

    public static final UUID COLLECTION_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    public static final String REFUND_REASON = "Customer requested refund";
    public static final String FAILURE_REASON = "PSP rejected refund request";

    // -- Refund State Factories -------------------------------------------

    public static Money aRefundAmount() {
        return new Money(new BigDecimal("1000.00"), "USD");
    }

    public static Refund aPendingRefund() {
        return Refund.initiate(
                COLLECTION_ID,
                PAYMENT_ID,
                aRefundAmount(),
                REFUND_REASON
        );
    }

    public static Refund aProcessingRefund() {
        return aPendingRefund().startProcessing();
    }

    public static Refund aCompletedRefund() {
        return aProcessingRefund().complete(PSP_REFUND_REFERENCE);
    }

    public static Refund aFailedRefund() {
        return aProcessingRefund().fail(FAILURE_REASON);
    }
}
