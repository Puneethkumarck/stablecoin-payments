package com.stablecoin.payments.offramp.fixtures;

import com.stablecoin.payments.offramp.domain.model.AccountType;
import com.stablecoin.payments.offramp.domain.model.BankAccount;
import com.stablecoin.payments.offramp.domain.model.MobileMoneyAccount;
import com.stablecoin.payments.offramp.domain.model.MobileMoneyProvider;
import com.stablecoin.payments.offramp.domain.model.Money;
import com.stablecoin.payments.offramp.domain.model.PartnerIdentifier;
import com.stablecoin.payments.offramp.domain.model.PaymentRail;
import com.stablecoin.payments.offramp.domain.model.PayoutOrder;
import com.stablecoin.payments.offramp.domain.model.PayoutType;
import com.stablecoin.payments.offramp.domain.model.StablecoinTicker;

import java.math.BigDecimal;
import java.util.UUID;

public final class PayoutOrderFixtures {

    private PayoutOrderFixtures() {}

    // -- Constants --------------------------------------------------------

    public static final UUID PAYMENT_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    public static final UUID CORRELATION_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    public static final UUID TRANSFER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    public static final UUID RECIPIENT_ID = UUID.fromString("d4e5f6a7-b8c9-0123-defa-234567890123");
    public static final String RECIPIENT_ACCOUNT_HASH = "sha256_recipient_abc123";
    public static final String TARGET_CURRENCY = "EUR";
    public static final BigDecimal REDEEMED_AMOUNT = new BigDecimal("1000.00");
    public static final BigDecimal APPLIED_FX_RATE = new BigDecimal("0.92");
    public static final BigDecimal EXPECTED_FIAT_AMOUNT = new BigDecimal("920.00");
    public static final String PARTNER_REFERENCE = "modulr_ref_12345";
    public static final String FAILURE_REASON = "Partner timeout exceeded";

    // -- Value Object Factories -------------------------------------------

    public static StablecoinTicker aStablecoinTicker() {
        return StablecoinTicker.of("USDC");
    }

    public static BankAccount aBankAccount() {
        return new BankAccount("DE89370400440532013000", "DEUTDEFF", AccountType.IBAN, "DE");
    }

    public static MobileMoneyAccount aMobileMoneyAccount() {
        return new MobileMoneyAccount(MobileMoneyProvider.M_PESA, "+254712345678", "KE");
    }

    public static PartnerIdentifier aPartnerIdentifier() {
        return new PartnerIdentifier("modulr_001", "Modulr");
    }

    public static Money aMoney() {
        return new Money(new BigDecimal("1000.00"), "USD");
    }

    // -- PayoutOrder State Factories --------------------------------------

    public static PayoutOrder aPendingOrder() {
        return PayoutOrder.create(
                PAYMENT_ID, CORRELATION_ID, TRANSFER_ID,
                PayoutType.FIAT, aStablecoinTicker(),
                REDEEMED_AMOUNT, TARGET_CURRENCY,
                APPLIED_FX_RATE, RECIPIENT_ID,
                RECIPIENT_ACCOUNT_HASH,
                aBankAccount(), null,
                PaymentRail.SEPA, aPartnerIdentifier()
        );
    }

    public static PayoutOrder aPendingHoldOrder() {
        return PayoutOrder.create(
                PAYMENT_ID, CORRELATION_ID, TRANSFER_ID,
                PayoutType.HOLD_STABLECOIN, aStablecoinTicker(),
                REDEEMED_AMOUNT, TARGET_CURRENCY,
                APPLIED_FX_RATE, RECIPIENT_ID,
                RECIPIENT_ACCOUNT_HASH,
                aBankAccount(), null,
                PaymentRail.SEPA, aPartnerIdentifier()
        );
    }

    public static PayoutOrder aPendingMobileMoneyOrder() {
        return PayoutOrder.create(
                PAYMENT_ID, CORRELATION_ID, TRANSFER_ID,
                PayoutType.FIAT, aStablecoinTicker(),
                REDEEMED_AMOUNT, TARGET_CURRENCY,
                APPLIED_FX_RATE, RECIPIENT_ID,
                RECIPIENT_ACCOUNT_HASH,
                null, aMobileMoneyAccount(),
                PaymentRail.M_PESA, aPartnerIdentifier()
        );
    }

    public static PayoutOrder aRedeemingOrder() {
        return aPendingOrder().startRedemption();
    }

    public static PayoutOrder aRedeemedOrder() {
        return aRedeemingOrder().completeRedemption(EXPECTED_FIAT_AMOUNT);
    }

    public static PayoutOrder aRedemptionFailedOrder() {
        return aRedeemingOrder().failRedemption(FAILURE_REASON);
    }

    public static PayoutOrder aPayoutInitiatedOrder() {
        return aRedeemedOrder().initiatePayout(PARTNER_REFERENCE);
    }

    public static PayoutOrder aPayoutProcessingOrder() {
        return aPayoutInitiatedOrder().markPayoutProcessing();
    }

    public static PayoutOrder aCompletedOrder() {
        return aPayoutProcessingOrder().completePayout(PARTNER_REFERENCE, java.time.Instant.now());
    }

    public static PayoutOrder aPayoutFailedOrder() {
        return aPayoutInitiatedOrder().failPayout(FAILURE_REASON);
    }

    public static PayoutOrder aManualReviewOrder() {
        return aPayoutFailedOrder().escalateToManualReview();
    }

    public static PayoutOrder aStablecoinHeldOrder() {
        return aPendingHoldOrder().holdStablecoin();
    }

    public static PayoutOrder aCompletedHoldOrder() {
        return aStablecoinHeldOrder().completeHold();
    }
}
