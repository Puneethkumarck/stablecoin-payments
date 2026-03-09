package com.stablecoin.payments.onramp.fixtures;

import com.stablecoin.payments.onramp.api.CollectionRequest;
import com.stablecoin.payments.onramp.domain.model.AccountType;
import com.stablecoin.payments.onramp.domain.model.BankAccount;
import com.stablecoin.payments.onramp.domain.model.CollectionOrder;
import com.stablecoin.payments.onramp.domain.model.Money;
import com.stablecoin.payments.onramp.domain.model.PaymentRail;
import com.stablecoin.payments.onramp.domain.model.PaymentRailType;
import com.stablecoin.payments.onramp.domain.model.PspIdentifier;

import com.stablecoin.payments.onramp.api.CollectionRequest;

import java.math.BigDecimal;
import java.util.UUID;

public final class CollectionOrderFixtures {

    private CollectionOrderFixtures() {}

    // -- Constants --------------------------------------------------------

    public static final UUID PAYMENT_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    public static final UUID CORRELATION_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    public static final String PSP_REFERENCE = "psp_ref_stripe_12345";
    public static final String PSP_REFUND_REFERENCE = "psp_refund_ref_67890";
    public static final String FAILURE_REASON = "Payment timeout exceeded";
    public static final String ERROR_CODE = "TIMEOUT_001";

    // -- Value Object Factories -------------------------------------------

    public static Money aMoney() {
        return new Money(new BigDecimal("1000.00"), "USD");
    }

    public static Money aCollectedMoney() {
        return new Money(new BigDecimal("1000.00"), "USD");
    }

    public static Money aRefundMoney() {
        return new Money(new BigDecimal("1000.00"), "USD");
    }

    public static BankAccount aBankAccount() {
        return new BankAccount(
                "sha256_abc123def456",
                "DEUTDEFF",
                AccountType.IBAN,
                "DE"
        );
    }

    public static PaymentRail aPaymentRail() {
        return new PaymentRail(PaymentRailType.SEPA, "DE", "EUR");
    }

    public static PspIdentifier aPspIdentifier() {
        return new PspIdentifier("stripe_001", "Stripe");
    }

    // -- CollectionOrder State Factories ----------------------------------

    public static CollectionOrder aPendingOrder() {
        return CollectionOrder.initiate(
                PAYMENT_ID,
                CORRELATION_ID,
                aMoney(),
                aPaymentRail(),
                aPspIdentifier(),
                aBankAccount()
        );
    }

    public static CollectionOrder aPaymentInitiatedOrder() {
        return aPendingOrder().initiatePayment();
    }

    public static CollectionOrder anAwaitingConfirmationOrder() {
        return aPaymentInitiatedOrder().awaitConfirmation(PSP_REFERENCE);
    }

    public static CollectionOrder aCollectedOrder() {
        return anAwaitingConfirmationOrder().confirmCollection(aCollectedMoney());
    }

    public static CollectionOrder aCollectionFailedOrder() {
        return aPendingOrder().failCollection(FAILURE_REASON, ERROR_CODE);
    }

    public static CollectionOrder anAmountMismatchOrder() {
        return anAwaitingConfirmationOrder().detectAmountMismatch();
    }

    public static CollectionOrder aManualReviewOrder() {
        return anAmountMismatchOrder().escalateToManualReview();
    }

    public static CollectionOrder aRefundInitiatedOrder() {
        return aCollectedOrder().initiateRefund();
    }

    public static CollectionOrder aRefundProcessingOrder() {
        return aRefundInitiatedOrder().startRefundProcessing();
    }

    public static CollectionOrder aRefundedOrder() {
        return aRefundProcessingOrder().completeRefund();
    }

    // -- API Request Factories --------------------------------------------

    public static CollectionRequest aCollectionRequest() {
        return new CollectionRequest(
                PAYMENT_ID,
                CORRELATION_ID,
                new BigDecimal("1000.00"),
                "USD",
                "SEPA",
                "DE",
                "EUR",
                "stripe_001",
                "Stripe",
                "sha256_abc123def456",
                "DEUTDEFF",
                "IBAN",
                "DE"
        );
    }
}
