package com.stablecoin.payments.onramp.fixtures;

import com.stablecoin.payments.onramp.domain.model.CollectionOrder;
import com.stablecoin.payments.onramp.domain.model.Money;
import com.stablecoin.payments.onramp.domain.model.ReconciliationRecord;

import java.math.BigDecimal;
import java.util.UUID;

import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aCollectedOrder;

public final class ReconciliationFixtures {

    private ReconciliationFixtures() {}

    // -- Constants --------------------------------------------------------

    public static final UUID RECONCILIATION_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    public static final UUID COLLECTION_ID = UUID.fromString("d4e5f6a7-b8c9-0123-defa-234567890123");
    public static final String PSP_NAME = "Stripe";
    public static final String PSP_REF = "psp_ref_stripe_12345";

    // -- ReconciliationRecord Factories -----------------------------------

    public static ReconciliationRecord aMatchedReconciliation() {
        return ReconciliationRecord.reconcile(
                COLLECTION_ID,
                PSP_NAME,
                PSP_REF,
                new BigDecimal("1000.00"),
                new BigDecimal("1000.00"),
                "USD"
        );
    }

    public static ReconciliationRecord aDiscrepancyReconciliation() {
        return ReconciliationRecord.reconcile(
                COLLECTION_ID,
                PSP_NAME,
                PSP_REF,
                new BigDecimal("1000.00"),
                new BigDecimal("950.00"),
                "USD"
        );
    }

    public static ReconciliationRecord anUnmatchedReconciliation() {
        return ReconciliationRecord.reconcile(
                COLLECTION_ID,
                PSP_NAME,
                PSP_REF,
                new BigDecimal("1000.00"),
                null,
                "USD"
        );
    }

    // -- CollectionOrder for Reconciliation Tests -------------------------

    public static CollectionOrder aCollectedOrderForReconciliation() {
        return aCollectedOrder();
    }

    public static CollectionOrder aCollectedOrderWithDifferentAmount() {
        var pending = CollectionOrder.initiate(
                CollectionOrderFixtures.PAYMENT_ID,
                CollectionOrderFixtures.CORRELATION_ID,
                CollectionOrderFixtures.aMoney(),
                CollectionOrderFixtures.aPaymentRail(),
                CollectionOrderFixtures.aPspIdentifier(),
                CollectionOrderFixtures.aBankAccount()
        );
        var initiated = pending.initiatePayment();
        var awaiting = initiated.awaitConfirmation(CollectionOrderFixtures.PSP_REFERENCE);
        return awaiting.confirmCollection(new Money(new BigDecimal("950.00"), "USD"));
    }

    public static CollectionOrder aCollectedOrderWithinTolerance() {
        var pending = CollectionOrder.initiate(
                CollectionOrderFixtures.PAYMENT_ID,
                CollectionOrderFixtures.CORRELATION_ID,
                CollectionOrderFixtures.aMoney(),
                CollectionOrderFixtures.aPaymentRail(),
                CollectionOrderFixtures.aPspIdentifier(),
                CollectionOrderFixtures.aBankAccount()
        );
        var initiated = pending.initiatePayment();
        var awaiting = initiated.awaitConfirmation(CollectionOrderFixtures.PSP_REFERENCE);
        return awaiting.confirmCollection(new Money(new BigDecimal("999.995"), "USD"));
    }

    // -- CollectionOrder for Expiry Tests ---------------------------------

    public static CollectionOrder anExpiredAwaitingConfirmationOrder() {
        var pending = CollectionOrder.initiate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                CollectionOrderFixtures.aMoney(),
                CollectionOrderFixtures.aPaymentRail(),
                CollectionOrderFixtures.aPspIdentifier(),
                CollectionOrderFixtures.aBankAccount()
        );
        var initiated = pending.initiatePayment();
        return initiated.awaitConfirmation("psp_ref_expired_001");
    }
}
