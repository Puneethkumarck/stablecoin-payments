package com.stablecoin.payments.orchestrator.fixtures;

import com.stablecoin.payments.orchestrator.domain.model.ChainId;
import com.stablecoin.payments.orchestrator.domain.model.Corridor;
import com.stablecoin.payments.orchestrator.domain.model.FxRate;
import com.stablecoin.payments.orchestrator.domain.model.Money;
import com.stablecoin.payments.orchestrator.domain.model.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Test fixture factory methods for {@link Payment} and related value objects.
 * <p>
 * Each method creates a Payment at the named state by walking through the state machine
 * from INITIATED. This guarantees that every fixture is reachable via valid transitions.
 */
public final class PaymentFixtures {

    public static final Money SOURCE_AMOUNT = new Money(new BigDecimal("1000.00"), "USD");
    public static final Corridor US_TO_DE = new Corridor("US", "DE");
    public static final String IDEMPOTENCY_KEY = "idem-key-123";
    public static final UUID CORRELATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID SENDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID RECIPIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    public static final String SOURCE_CURRENCY = "USD";
    public static final String TARGET_CURRENCY = "EUR";
    public static final ChainId BASE_CHAIN = new ChainId("base");
    public static final String TX_HASH = "0xabc123def456";

    private PaymentFixtures() {}

    public static FxRate aValidFxRate() {
        var now = Instant.now();
        return new FxRate(
                UUID.fromString("00000000-0000-0000-0000-000000000099"),
                "USD",
                "EUR",
                new BigDecimal("0.92"),
                now,
                now.plusSeconds(600),
                "ecb"
        );
    }

    public static Payment anInitiatedPayment() {
        return Payment.initiate(
                IDEMPOTENCY_KEY,
                CORRELATION_ID,
                SENDER_ID,
                RECIPIENT_ID,
                SOURCE_AMOUNT,
                SOURCE_CURRENCY,
                TARGET_CURRENCY,
                US_TO_DE
        );
    }

    public static Payment aComplianceCheckPayment() {
        return anInitiatedPayment().startComplianceCheck();
    }

    public static Payment anFxLockedPayment() {
        return aComplianceCheckPayment().lockFxRate(aValidFxRate());
    }

    public static Payment aFiatCollectionPendingPayment() {
        return anFxLockedPayment().startFiatCollection();
    }

    public static Payment aFiatCollectedPayment() {
        return aFiatCollectionPendingPayment().confirmFiatCollected();
    }

    public static Payment anOnChainSubmittedPayment() {
        return aFiatCollectedPayment().submitOnChain(BASE_CHAIN);
    }

    public static Payment anOnChainConfirmedPayment() {
        return anOnChainSubmittedPayment().confirmOnChain(TX_HASH);
    }

    public static Payment anOffRampInitiatedPayment() {
        return anOnChainConfirmedPayment().initiateOffRamp();
    }

    public static Payment aSettledPayment() {
        return anOffRampInitiatedPayment().settle();
    }

    public static Payment aCompletedPayment() {
        return aSettledPayment().complete();
    }

    public static Payment aFailedPayment() {
        return anInitiatedPayment().fail("Test failure");
    }

    public static Payment aCompensatingFiatRefundPayment() {
        return aFiatCollectedPayment().startCompensation("Refund needed");
    }

    public static Payment aCompensatingStablecoinReturnPayment() {
        return anOnChainSubmittedPayment().startCompensation("Stablecoin return needed");
    }
}
