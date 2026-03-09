package com.stablecoin.payments.offramp.domain.model;

import com.stablecoin.payments.offramp.domain.statemachine.StateMachineException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.offramp.domain.model.PayoutStatus.COMPLETED;
import static com.stablecoin.payments.offramp.domain.model.PayoutStatus.MANUAL_REVIEW;
import static com.stablecoin.payments.offramp.domain.model.PayoutStatus.PAYOUT_FAILED;
import static com.stablecoin.payments.offramp.domain.model.PayoutStatus.PAYOUT_INITIATED;
import static com.stablecoin.payments.offramp.domain.model.PayoutStatus.PAYOUT_PROCESSING;
import static com.stablecoin.payments.offramp.domain.model.PayoutStatus.PENDING;
import static com.stablecoin.payments.offramp.domain.model.PayoutStatus.REDEEMED;
import static com.stablecoin.payments.offramp.domain.model.PayoutStatus.REDEEMING;
import static com.stablecoin.payments.offramp.domain.model.PayoutStatus.REDEMPTION_FAILED;
import static com.stablecoin.payments.offramp.domain.model.PayoutStatus.STABLECOIN_HELD;
import static com.stablecoin.payments.offramp.domain.model.PayoutTrigger.COMPLETE_HOLD;
import static com.stablecoin.payments.offramp.domain.model.PayoutTrigger.COMPLETE_PAYOUT;
import static com.stablecoin.payments.offramp.domain.model.PayoutTrigger.COMPLETE_REDEMPTION;
import static com.stablecoin.payments.offramp.domain.model.PayoutTrigger.ESCALATE_MANUAL_REVIEW;
import static com.stablecoin.payments.offramp.domain.model.PayoutTrigger.FAIL_PAYOUT;
import static com.stablecoin.payments.offramp.domain.model.PayoutTrigger.FAIL_REDEMPTION;
import static com.stablecoin.payments.offramp.domain.model.PayoutTrigger.HOLD_STABLECOIN;
import static com.stablecoin.payments.offramp.domain.model.PayoutTrigger.INITIATE_PAYOUT;
import static com.stablecoin.payments.offramp.domain.model.PayoutTrigger.MARK_PAYOUT_PROCESSING;
import static com.stablecoin.payments.offramp.domain.model.PayoutTrigger.START_REDEMPTION;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.APPLIED_FX_RATE;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.CORRELATION_ID;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.EXPECTED_FIAT_AMOUNT;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.FAILURE_REASON;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.PARTNER_REFERENCE;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.PAYMENT_ID;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.RECIPIENT_ACCOUNT_HASH;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.RECIPIENT_ID;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.REDEEMED_AMOUNT;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.TARGET_CURRENCY;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.TRANSFER_ID;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aBankAccount;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aCompletedHoldOrder;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aCompletedOrder;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aManualReviewOrder;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aPartnerIdentifier;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aPayoutFailedOrder;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aPayoutInitiatedOrder;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aPayoutProcessingOrder;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aPendingHoldOrder;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aPendingOrder;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aRedeemedOrder;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aRedeemingOrder;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aRedemptionFailedOrder;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aStablecoinHeldOrder;
import static com.stablecoin.payments.offramp.fixtures.PayoutOrderFixtures.aStablecoinTicker;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayoutOrderTest {

    // =====================================================================
    // Factory Method — create()
    // =====================================================================

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("creates a PENDING payout order with all required fields")
        void createsPayoutOrderInPendingState() {
            var order = aPendingOrder();

            var expected = new Object() {
                final UUID paymentId = PAYMENT_ID;
                final UUID correlationId = CORRELATION_ID;
                final UUID transferId = TRANSFER_ID;
                final PayoutType payoutType = PayoutType.FIAT;
                final BigDecimal redeemedAmount = REDEEMED_AMOUNT;
                final String targetCurrency = TARGET_CURRENCY;
                final BigDecimal appliedFxRate = APPLIED_FX_RATE;
                final UUID recipientId = RECIPIENT_ID;
                final String recipientAccountHash = RECIPIENT_ACCOUNT_HASH;
                final PayoutStatus status = PENDING;
                final PaymentRail paymentRail = PaymentRail.SEPA;
            };

            assertThat(order)
                    .usingRecursiveComparison()
                    .comparingOnlyFields(
                            "paymentId", "correlationId", "transferId", "payoutType",
                            "redeemedAmount", "targetCurrency", "appliedFxRate",
                            "recipientId", "recipientAccountHash", "status", "paymentRail")
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("generates a random payoutId")
        void generatesRandomPayoutId() {
            var order = aPendingOrder();

            assertThat(order.payoutId()).isNotNull();
        }

        @Test
        @DisplayName("sets createdAt and updatedAt timestamps")
        void setsTimestamps() {
            var before = Instant.now();
            var order = aPendingOrder();

            assertThat(order.createdAt()).isAfterOrEqualTo(before);
            assertThat(order.updatedAt()).isAfterOrEqualTo(before);
        }

        @Test
        @DisplayName("throws when paymentId is null")
        void throwsWhenPaymentIdNull() {
            assertThatThrownBy(() -> PayoutOrder.create(
                    null, CORRELATION_ID, TRANSFER_ID,
                    PayoutType.FIAT, aStablecoinTicker(),
                    REDEEMED_AMOUNT, TARGET_CURRENCY,
                    APPLIED_FX_RATE, RECIPIENT_ID,
                    RECIPIENT_ACCOUNT_HASH,
                    aBankAccount(), null,
                    PaymentRail.SEPA, aPartnerIdentifier()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("paymentId");
        }

        @Test
        @DisplayName("throws when correlationId is null")
        void throwsWhenCorrelationIdNull() {
            assertThatThrownBy(() -> PayoutOrder.create(
                    PAYMENT_ID, null, TRANSFER_ID,
                    PayoutType.FIAT, aStablecoinTicker(),
                    REDEEMED_AMOUNT, TARGET_CURRENCY,
                    APPLIED_FX_RATE, RECIPIENT_ID,
                    RECIPIENT_ACCOUNT_HASH,
                    aBankAccount(), null,
                    PaymentRail.SEPA, aPartnerIdentifier()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("correlationId");
        }

        @Test
        @DisplayName("throws when transferId is null")
        void throwsWhenTransferIdNull() {
            assertThatThrownBy(() -> PayoutOrder.create(
                    PAYMENT_ID, CORRELATION_ID, null,
                    PayoutType.FIAT, aStablecoinTicker(),
                    REDEEMED_AMOUNT, TARGET_CURRENCY,
                    APPLIED_FX_RATE, RECIPIENT_ID,
                    RECIPIENT_ACCOUNT_HASH,
                    aBankAccount(), null,
                    PaymentRail.SEPA, aPartnerIdentifier()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("transferId");
        }

        @Test
        @DisplayName("throws when payoutType is null")
        void throwsWhenPayoutTypeNull() {
            assertThatThrownBy(() -> PayoutOrder.create(
                    PAYMENT_ID, CORRELATION_ID, TRANSFER_ID,
                    null, aStablecoinTicker(),
                    REDEEMED_AMOUNT, TARGET_CURRENCY,
                    APPLIED_FX_RATE, RECIPIENT_ID,
                    RECIPIENT_ACCOUNT_HASH,
                    aBankAccount(), null,
                    PaymentRail.SEPA, aPartnerIdentifier()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("payoutType");
        }

        @Test
        @DisplayName("throws when stablecoin is null")
        void throwsWhenStablecoinNull() {
            assertThatThrownBy(() -> PayoutOrder.create(
                    PAYMENT_ID, CORRELATION_ID, TRANSFER_ID,
                    PayoutType.FIAT, null,
                    REDEEMED_AMOUNT, TARGET_CURRENCY,
                    APPLIED_FX_RATE, RECIPIENT_ID,
                    RECIPIENT_ACCOUNT_HASH,
                    aBankAccount(), null,
                    PaymentRail.SEPA, aPartnerIdentifier()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("stablecoin");
        }

        @Test
        @DisplayName("throws when redeemedAmount is null")
        void throwsWhenRedeemedAmountNull() {
            assertThatThrownBy(() -> PayoutOrder.create(
                    PAYMENT_ID, CORRELATION_ID, TRANSFER_ID,
                    PayoutType.FIAT, aStablecoinTicker(),
                    null, TARGET_CURRENCY,
                    APPLIED_FX_RATE, RECIPIENT_ID,
                    RECIPIENT_ACCOUNT_HASH,
                    aBankAccount(), null,
                    PaymentRail.SEPA, aPartnerIdentifier()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("redeemedAmount");
        }

        @Test
        @DisplayName("throws when redeemedAmount is zero")
        void throwsWhenRedeemedAmountZero() {
            assertThatThrownBy(() -> PayoutOrder.create(
                    PAYMENT_ID, CORRELATION_ID, TRANSFER_ID,
                    PayoutType.FIAT, aStablecoinTicker(),
                    BigDecimal.ZERO, TARGET_CURRENCY,
                    APPLIED_FX_RATE, RECIPIENT_ID,
                    RECIPIENT_ACCOUNT_HASH,
                    aBankAccount(), null,
                    PaymentRail.SEPA, aPartnerIdentifier()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("redeemedAmount");
        }

        @Test
        @DisplayName("throws when redeemedAmount is negative")
        void throwsWhenRedeemedAmountNegative() {
            assertThatThrownBy(() -> PayoutOrder.create(
                    PAYMENT_ID, CORRELATION_ID, TRANSFER_ID,
                    PayoutType.FIAT, aStablecoinTicker(),
                    new BigDecimal("-100"), TARGET_CURRENCY,
                    APPLIED_FX_RATE, RECIPIENT_ID,
                    RECIPIENT_ACCOUNT_HASH,
                    aBankAccount(), null,
                    PaymentRail.SEPA, aPartnerIdentifier()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("redeemedAmount");
        }

        @Test
        @DisplayName("throws when targetCurrency is blank")
        void throwsWhenTargetCurrencyBlank() {
            assertThatThrownBy(() -> PayoutOrder.create(
                    PAYMENT_ID, CORRELATION_ID, TRANSFER_ID,
                    PayoutType.FIAT, aStablecoinTicker(),
                    REDEEMED_AMOUNT, "  ",
                    APPLIED_FX_RATE, RECIPIENT_ID,
                    RECIPIENT_ACCOUNT_HASH,
                    aBankAccount(), null,
                    PaymentRail.SEPA, aPartnerIdentifier()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("targetCurrency");
        }

        @Test
        @DisplayName("throws when appliedFxRate is null")
        void throwsWhenAppliedFxRateNull() {
            assertThatThrownBy(() -> PayoutOrder.create(
                    PAYMENT_ID, CORRELATION_ID, TRANSFER_ID,
                    PayoutType.FIAT, aStablecoinTicker(),
                    REDEEMED_AMOUNT, TARGET_CURRENCY,
                    null, RECIPIENT_ID,
                    RECIPIENT_ACCOUNT_HASH,
                    aBankAccount(), null,
                    PaymentRail.SEPA, aPartnerIdentifier()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("appliedFxRate");
        }

        @Test
        @DisplayName("throws when appliedFxRate is zero")
        void throwsWhenAppliedFxRateZero() {
            assertThatThrownBy(() -> PayoutOrder.create(
                    PAYMENT_ID, CORRELATION_ID, TRANSFER_ID,
                    PayoutType.FIAT, aStablecoinTicker(),
                    REDEEMED_AMOUNT, TARGET_CURRENCY,
                    BigDecimal.ZERO, RECIPIENT_ID,
                    RECIPIENT_ACCOUNT_HASH,
                    aBankAccount(), null,
                    PaymentRail.SEPA, aPartnerIdentifier()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("appliedFxRate");
        }

        @Test
        @DisplayName("throws when recipientId is null")
        void throwsWhenRecipientIdNull() {
            assertThatThrownBy(() -> PayoutOrder.create(
                    PAYMENT_ID, CORRELATION_ID, TRANSFER_ID,
                    PayoutType.FIAT, aStablecoinTicker(),
                    REDEEMED_AMOUNT, TARGET_CURRENCY,
                    APPLIED_FX_RATE, null,
                    RECIPIENT_ACCOUNT_HASH,
                    aBankAccount(), null,
                    PaymentRail.SEPA, aPartnerIdentifier()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("recipientId");
        }

        @Test
        @DisplayName("throws when recipientAccountHash is blank")
        void throwsWhenRecipientAccountHashBlank() {
            assertThatThrownBy(() -> PayoutOrder.create(
                    PAYMENT_ID, CORRELATION_ID, TRANSFER_ID,
                    PayoutType.FIAT, aStablecoinTicker(),
                    REDEEMED_AMOUNT, TARGET_CURRENCY,
                    APPLIED_FX_RATE, RECIPIENT_ID,
                    "",
                    aBankAccount(), null,
                    PaymentRail.SEPA, aPartnerIdentifier()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("recipientAccountHash");
        }

        @Test
        @DisplayName("throws when paymentRail is null")
        void throwsWhenPaymentRailNull() {
            assertThatThrownBy(() -> PayoutOrder.create(
                    PAYMENT_ID, CORRELATION_ID, TRANSFER_ID,
                    PayoutType.FIAT, aStablecoinTicker(),
                    REDEEMED_AMOUNT, TARGET_CURRENCY,
                    APPLIED_FX_RATE, RECIPIENT_ID,
                    RECIPIENT_ACCOUNT_HASH,
                    aBankAccount(), null,
                    null, aPartnerIdentifier()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("paymentRail");
        }

        @Test
        @DisplayName("throws when offRampPartner is null")
        void throwsWhenOffRampPartnerNull() {
            assertThatThrownBy(() -> PayoutOrder.create(
                    PAYMENT_ID, CORRELATION_ID, TRANSFER_ID,
                    PayoutType.FIAT, aStablecoinTicker(),
                    REDEEMED_AMOUNT, TARGET_CURRENCY,
                    APPLIED_FX_RATE, RECIPIENT_ID,
                    RECIPIENT_ACCOUNT_HASH,
                    aBankAccount(), null,
                    PaymentRail.SEPA, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("offRampPartner");
        }

        @Test
        @DisplayName("throws when both bankAccount and mobileMoneyAccount are null")
        void throwsWhenBothAccountsNull() {
            assertThatThrownBy(() -> PayoutOrder.create(
                    PAYMENT_ID, CORRELATION_ID, TRANSFER_ID,
                    PayoutType.FIAT, aStablecoinTicker(),
                    REDEEMED_AMOUNT, TARGET_CURRENCY,
                    APPLIED_FX_RATE, RECIPIENT_ID,
                    RECIPIENT_ACCOUNT_HASH,
                    null, null,
                    PaymentRail.SEPA, aPartnerIdentifier()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("bankAccount or mobileMoneyAccount");
        }
    }

    // =====================================================================
    // Happy Path — Fiat: PENDING → REDEEMING → REDEEMED → PAYOUT_INITIATED → PAYOUT_PROCESSING → COMPLETED
    // =====================================================================

    @Nested
    @DisplayName("Fiat happy path transitions")
    class FiatHappyPath {

        @Test
        @DisplayName("startRedemption: PENDING → REDEEMING")
        void startRedemption() {
            var order = aPendingOrder().startRedemption();

            assertThat(order.status()).isEqualTo(REDEEMING);
        }

        @Test
        @DisplayName("completeRedemption: REDEEMING → REDEEMED with fiat amount")
        void completeRedemption() {
            var order = aRedeemingOrder().completeRedemption(EXPECTED_FIAT_AMOUNT);

            assertThat(order.status()).isEqualTo(REDEEMED);
            assertThat(order.fiatAmount()).isEqualByComparingTo(EXPECTED_FIAT_AMOUNT);
        }

        @Test
        @DisplayName("initiatePayout: REDEEMED → PAYOUT_INITIATED with partner reference")
        void initiatePayout() {
            var order = aRedeemedOrder().initiatePayout(PARTNER_REFERENCE);

            assertThat(order.status()).isEqualTo(PAYOUT_INITIATED);
            assertThat(order.partnerReference()).isEqualTo(PARTNER_REFERENCE);
        }

        @Test
        @DisplayName("markPayoutProcessing: PAYOUT_INITIATED → PAYOUT_PROCESSING")
        void markPayoutProcessing() {
            var order = aPayoutInitiatedOrder().markPayoutProcessing();

            assertThat(order.status()).isEqualTo(PAYOUT_PROCESSING);
        }

        @Test
        @DisplayName("completePayout: PAYOUT_PROCESSING → COMPLETED with settlement time")
        void completePayout() {
            var settledAt = Instant.now();
            var order = aPayoutProcessingOrder().completePayout(PARTNER_REFERENCE, settledAt);

            assertThat(order.status()).isEqualTo(COMPLETED);
            assertThat(order.partnerSettledAt()).isEqualTo(settledAt);
        }

        @Test
        @DisplayName("full happy path walks through all 5 transitions")
        void fullHappyPath() {
            var settledAt = Instant.now();
            var order = aPendingOrder()
                    .startRedemption()
                    .completeRedemption(EXPECTED_FIAT_AMOUNT)
                    .initiatePayout(PARTNER_REFERENCE)
                    .markPayoutProcessing()
                    .completePayout(PARTNER_REFERENCE, settledAt);

            assertThat(order.status()).isEqualTo(COMPLETED);
            assertThat(order.isTerminal()).isTrue();
        }
    }

    // =====================================================================
    // Hold Stablecoin Path — PENDING → STABLECOIN_HELD → COMPLETED
    // =====================================================================

    @Nested
    @DisplayName("Hold stablecoin path")
    class HoldStablecoinPath {

        @Test
        @DisplayName("holdStablecoin: PENDING → STABLECOIN_HELD for HOLD_STABLECOIN type")
        void holdStablecoin() {
            var order = aPendingHoldOrder().holdStablecoin();

            assertThat(order.status()).isEqualTo(STABLECOIN_HELD);
        }

        @Test
        @DisplayName("completeHold: STABLECOIN_HELD → COMPLETED")
        void completeHold() {
            var order = aStablecoinHeldOrder().completeHold();

            assertThat(order.status()).isEqualTo(COMPLETED);
            assertThat(order.isTerminal()).isTrue();
        }

        @Test
        @DisplayName("holdStablecoin rejects FIAT payout type")
        void holdStablecoinRejectsFiatType() {
            var order = aPendingOrder(); // FIAT type

            assertThatThrownBy(order::holdStablecoin)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("HOLD_STABLECOIN");
        }

        @Test
        @DisplayName("full hold path walks both transitions")
        void fullHoldPath() {
            var order = aPendingHoldOrder()
                    .holdStablecoin()
                    .completeHold();

            assertThat(order.status()).isEqualTo(COMPLETED);
            assertThat(order.isTerminal()).isTrue();
        }
    }

    // =====================================================================
    // Redemption Failure Path — REDEEMING → REDEMPTION_FAILED → MANUAL_REVIEW
    // =====================================================================

    @Nested
    @DisplayName("Redemption failure path")
    class RedemptionFailurePath {

        @Test
        @DisplayName("failRedemption: REDEEMING → REDEMPTION_FAILED with reason and error code")
        void failRedemption() {
            var order = aRedeemingOrder().failRedemption(FAILURE_REASON);

            assertThat(order.status()).isEqualTo(REDEMPTION_FAILED);
            assertThat(order.failureReason()).isEqualTo(FAILURE_REASON);
            assertThat(order.errorCode()).isEqualTo("FR-2002");
        }

        @Test
        @DisplayName("escalateToManualReview: REDEMPTION_FAILED → MANUAL_REVIEW")
        void escalateFromRedemptionFailed() {
            var order = aRedemptionFailedOrder().escalateToManualReview();

            assertThat(order.status()).isEqualTo(MANUAL_REVIEW);
            assertThat(order.isTerminal()).isTrue();
        }
    }

    // =====================================================================
    // Payout Failure Path — PAYOUT_INITIATED/PROCESSING → PAYOUT_FAILED → MANUAL_REVIEW
    // =====================================================================

    @Nested
    @DisplayName("Payout failure path")
    class PayoutFailurePath {

        @Test
        @DisplayName("failPayout from PAYOUT_INITIATED: → PAYOUT_FAILED with error code FR-2003")
        void failPayoutFromInitiated() {
            var order = aPayoutInitiatedOrder().failPayout(FAILURE_REASON);

            assertThat(order.status()).isEqualTo(PAYOUT_FAILED);
            assertThat(order.failureReason()).isEqualTo(FAILURE_REASON);
            assertThat(order.errorCode()).isEqualTo("FR-2003");
        }

        @Test
        @DisplayName("failPayout from PAYOUT_PROCESSING: → PAYOUT_FAILED")
        void failPayoutFromProcessing() {
            var order = aPayoutProcessingOrder().failPayout(FAILURE_REASON);

            assertThat(order.status()).isEqualTo(PAYOUT_FAILED);
        }

        @Test
        @DisplayName("escalateToManualReview: PAYOUT_FAILED → MANUAL_REVIEW")
        void escalateFromPayoutFailed() {
            var order = aPayoutFailedOrder().escalateToManualReview();

            assertThat(order.status()).isEqualTo(MANUAL_REVIEW);
            assertThat(order.isTerminal()).isTrue();
        }
    }

    // =====================================================================
    // Fiat Amount Tolerance Invariant
    // =====================================================================

    @Nested
    @DisplayName("Fiat amount tolerance invariant")
    class FiatAmountTolerance {

        @Test
        @DisplayName("accepts fiat amount within ±0.01 tolerance")
        void acceptsWithinTolerance() {
            // 1000 * 0.92 = 920.00; 920.01 is within ±0.01
            var order = aRedeemingOrder().completeRedemption(new BigDecimal("920.01"));

            assertThat(order.status()).isEqualTo(REDEEMED);
        }

        @Test
        @DisplayName("accepts exact expected fiat amount")
        void acceptsExactAmount() {
            var order = aRedeemingOrder().completeRedemption(EXPECTED_FIAT_AMOUNT);

            assertThat(order.status()).isEqualTo(REDEEMED);
        }

        @Test
        @DisplayName("accepts fiat amount at lower tolerance boundary")
        void acceptsLowerBoundary() {
            // 920.00 - 0.01 = 919.99
            var order = aRedeemingOrder().completeRedemption(new BigDecimal("919.99"));

            assertThat(order.status()).isEqualTo(REDEEMED);
        }

        @Test
        @DisplayName("rejects fiat amount exceeding upper tolerance")
        void rejectsExceedingUpperTolerance() {
            // 920.00 + 0.02 = 920.02 exceeds ±0.01
            assertThatThrownBy(() -> aRedeemingOrder().completeRedemption(new BigDecimal("920.02")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tolerance");
        }

        @Test
        @DisplayName("rejects fiat amount below lower tolerance")
        void rejectsBelowLowerTolerance() {
            // 920.00 - 0.02 = 919.98 exceeds ±0.01
            assertThatThrownBy(() -> aRedeemingOrder().completeRedemption(new BigDecimal("919.98")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tolerance");
        }

        @Test
        @DisplayName("rejects null fiat amount")
        void rejectsNullFiatAmount() {
            assertThatThrownBy(() -> aRedeemingOrder().completeRedemption(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("rejects zero fiat amount")
        void rejectsZeroFiatAmount() {
            assertThatThrownBy(() -> aRedeemingOrder().completeRedemption(BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("rejects negative fiat amount")
        void rejectsNegativeFiatAmount() {
            assertThatThrownBy(() -> aRedeemingOrder().completeRedemption(new BigDecimal("-100")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }
    }

    // =====================================================================
    // Terminal State Guards
    // =====================================================================

    @Nested
    @DisplayName("Terminal state guards")
    class TerminalStateGuards {

        @Test
        @DisplayName("COMPLETED is terminal")
        void completedIsTerminal() {
            assertThat(aCompletedOrder().isTerminal()).isTrue();
        }

        @Test
        @DisplayName("MANUAL_REVIEW is terminal")
        void manualReviewIsTerminal() {
            assertThat(aManualReviewOrder().isTerminal()).isTrue();
        }

        @Test
        @DisplayName("PENDING is not terminal")
        void pendingIsNotTerminal() {
            assertThat(aPendingOrder().isTerminal()).isFalse();
        }

        @Test
        @DisplayName("COMPLETED order rejects startRedemption")
        void completedRejectsStartRedemption() {
            assertThatThrownBy(() -> aCompletedOrder().startRedemption())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal");
        }

        @Test
        @DisplayName("COMPLETED order rejects holdStablecoin")
        void completedRejectsHoldStablecoin() {
            assertThatThrownBy(() -> aCompletedHoldOrder().holdStablecoin())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal");
        }

        @Test
        @DisplayName("MANUAL_REVIEW order rejects escalateToManualReview")
        void manualReviewRejectsEscalate() {
            assertThatThrownBy(() -> aManualReviewOrder().escalateToManualReview())
                    .isInstanceOf(StateMachineException.class);
        }

        @Test
        @DisplayName("COMPLETED order rejects initiatePayout")
        void completedRejectsInitiatePayout() {
            assertThatThrownBy(() -> aCompletedOrder().initiatePayout("ref"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal");
        }

        @Test
        @DisplayName("COMPLETED order rejects markPayoutProcessing")
        void completedRejectsMarkPayoutProcessing() {
            assertThatThrownBy(() -> aCompletedOrder().markPayoutProcessing())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal");
        }

        @Test
        @DisplayName("COMPLETED order rejects completePayout")
        void completedRejectsCompletePayout() {
            assertThatThrownBy(() -> aCompletedOrder().completePayout("ref", Instant.now()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal");
        }

        @Test
        @DisplayName("COMPLETED order rejects completeHold")
        void completedRejectsCompleteHold() {
            assertThatThrownBy(() -> aCompletedHoldOrder().completeHold())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal");
        }
    }

    // =====================================================================
    // Invalid State Transitions
    // =====================================================================

    @Nested
    @DisplayName("Invalid state transitions")
    class InvalidTransitions {

        @Test
        @DisplayName("PENDING cannot completeRedemption")
        void pendingCannotCompleteRedemption() {
            assertThatThrownBy(() -> aPendingOrder().completeRedemption(EXPECTED_FIAT_AMOUNT))
                    .isInstanceOf(StateMachineException.class);
        }

        @Test
        @DisplayName("PENDING cannot initiatePayout")
        void pendingCannotInitiatePayout() {
            assertThatThrownBy(() -> aPendingOrder().initiatePayout(PARTNER_REFERENCE))
                    .isInstanceOf(StateMachineException.class);
        }

        @Test
        @DisplayName("PENDING cannot failPayout")
        void pendingCannotFailPayout() {
            assertThatThrownBy(() -> aPendingOrder().failPayout(FAILURE_REASON))
                    .isInstanceOf(StateMachineException.class);
        }

        @Test
        @DisplayName("REDEEMING cannot initiatePayout")
        void redeemingCannotInitiatePayout() {
            assertThatThrownBy(() -> aRedeemingOrder().initiatePayout(PARTNER_REFERENCE))
                    .isInstanceOf(StateMachineException.class);
        }

        @Test
        @DisplayName("REDEEMED cannot startRedemption")
        void redeemedCannotStartRedemption() {
            assertThatThrownBy(() -> aRedeemedOrder().startRedemption())
                    .isInstanceOf(StateMachineException.class);
        }

        @Test
        @DisplayName("REDEEMED cannot failRedemption")
        void redeemedCannotFailRedemption() {
            assertThatThrownBy(() -> aRedeemedOrder().failRedemption("reason"))
                    .isInstanceOf(StateMachineException.class);
        }

        @Test
        @DisplayName("PAYOUT_INITIATED cannot startRedemption")
        void payoutInitiatedCannotStartRedemption() {
            assertThatThrownBy(() -> aPayoutInitiatedOrder().startRedemption())
                    .isInstanceOf(StateMachineException.class);
        }

        @Test
        @DisplayName("PAYOUT_PROCESSING cannot initiatePayout")
        void payoutProcessingCannotInitiatePayout() {
            assertThatThrownBy(() -> aPayoutProcessingOrder().initiatePayout("ref"))
                    .isInstanceOf(StateMachineException.class);
        }

        @Test
        @DisplayName("REDEMPTION_FAILED cannot completeRedemption")
        void redemptionFailedCannotComplete() {
            assertThatThrownBy(() -> aRedemptionFailedOrder().completeRedemption(EXPECTED_FIAT_AMOUNT))
                    .isInstanceOf(StateMachineException.class);
        }

        @Test
        @DisplayName("PAYOUT_FAILED cannot completePayout")
        void payoutFailedCannotComplete() {
            assertThatThrownBy(() -> aPayoutFailedOrder().completePayout("ref", Instant.now()))
                    .isInstanceOf(StateMachineException.class);
        }

        @Test
        @DisplayName("STABLECOIN_HELD cannot startRedemption")
        void stablecoinHeldCannotStartRedemption() {
            assertThatThrownBy(() -> aStablecoinHeldOrder().startRedemption())
                    .isInstanceOf(StateMachineException.class);
        }
    }

    // =====================================================================
    // canApply() query
    // =====================================================================

    @Nested
    @DisplayName("canApply()")
    class CanApply {

        @Test
        @DisplayName("PENDING canApply START_REDEMPTION")
        void pendingCanStartRedemption() {
            assertThat(aPendingOrder().canApply(START_REDEMPTION)).isTrue();
        }

        @Test
        @DisplayName("PENDING canApply HOLD_STABLECOIN")
        void pendingCanHoldStablecoin() {
            assertThat(aPendingOrder().canApply(HOLD_STABLECOIN)).isTrue();
        }

        @Test
        @DisplayName("PENDING cannot apply COMPLETE_REDEMPTION")
        void pendingCannotCompleteRedemption() {
            assertThat(aPendingOrder().canApply(COMPLETE_REDEMPTION)).isFalse();
        }

        @Test
        @DisplayName("REDEEMING canApply COMPLETE_REDEMPTION")
        void redeemingCanComplete() {
            assertThat(aRedeemingOrder().canApply(COMPLETE_REDEMPTION)).isTrue();
        }

        @Test
        @DisplayName("REDEEMING canApply FAIL_REDEMPTION")
        void redeemingCanFail() {
            assertThat(aRedeemingOrder().canApply(FAIL_REDEMPTION)).isTrue();
        }

        @Test
        @DisplayName("REDEEMED canApply INITIATE_PAYOUT")
        void redeemedCanInitiatePayout() {
            assertThat(aRedeemedOrder().canApply(INITIATE_PAYOUT)).isTrue();
        }

        @Test
        @DisplayName("PAYOUT_INITIATED canApply MARK_PAYOUT_PROCESSING")
        void payoutInitiatedCanMarkProcessing() {
            assertThat(aPayoutInitiatedOrder().canApply(MARK_PAYOUT_PROCESSING)).isTrue();
        }

        @Test
        @DisplayName("PAYOUT_INITIATED canApply FAIL_PAYOUT")
        void payoutInitiatedCanFail() {
            assertThat(aPayoutInitiatedOrder().canApply(FAIL_PAYOUT)).isTrue();
        }

        @Test
        @DisplayName("PAYOUT_PROCESSING canApply COMPLETE_PAYOUT")
        void payoutProcessingCanComplete() {
            assertThat(aPayoutProcessingOrder().canApply(COMPLETE_PAYOUT)).isTrue();
        }

        @Test
        @DisplayName("PAYOUT_PROCESSING canApply FAIL_PAYOUT")
        void payoutProcessingCanFail() {
            assertThat(aPayoutProcessingOrder().canApply(FAIL_PAYOUT)).isTrue();
        }

        @Test
        @DisplayName("REDEMPTION_FAILED canApply ESCALATE_MANUAL_REVIEW")
        void redemptionFailedCanEscalate() {
            assertThat(aRedemptionFailedOrder().canApply(ESCALATE_MANUAL_REVIEW)).isTrue();
        }

        @Test
        @DisplayName("PAYOUT_FAILED canApply ESCALATE_MANUAL_REVIEW")
        void payoutFailedCanEscalate() {
            assertThat(aPayoutFailedOrder().canApply(ESCALATE_MANUAL_REVIEW)).isTrue();
        }

        @Test
        @DisplayName("STABLECOIN_HELD canApply COMPLETE_HOLD")
        void stablecoinHeldCanComplete() {
            assertThat(aStablecoinHeldOrder().canApply(COMPLETE_HOLD)).isTrue();
        }

        @Test
        @DisplayName("COMPLETED cannot apply any trigger")
        void completedCannotApplyAny() {
            var completed = aCompletedOrder();
            for (PayoutTrigger trigger : PayoutTrigger.values()) {
                assertThat(completed.canApply(trigger))
                        .as("COMPLETED should reject trigger %s", trigger)
                        .isFalse();
            }
        }
    }

    // =====================================================================
    // Transition Method Validations
    // =====================================================================

    @Nested
    @DisplayName("Transition method validations")
    class TransitionValidations {

        @Test
        @DisplayName("initiatePayout rejects null partner reference")
        void initiatePayoutRejectsNullRef() {
            assertThatThrownBy(() -> aRedeemedOrder().initiatePayout(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Partner reference");
        }

        @Test
        @DisplayName("initiatePayout rejects blank partner reference")
        void initiatePayoutRejectsBlankRef() {
            assertThatThrownBy(() -> aRedeemedOrder().initiatePayout("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Partner reference");
        }

        @Test
        @DisplayName("completePayout rejects null settledAt")
        void completePayoutRejectsNullSettledAt() {
            assertThatThrownBy(() -> aPayoutProcessingOrder().completePayout(PARTNER_REFERENCE, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Settlement timestamp");
        }

        @Test
        @DisplayName("completePayout preserves existing partner reference when new one is null")
        void completePayoutPreservesExistingRef() {
            var order = aPayoutProcessingOrder().completePayout(null, Instant.now());

            assertThat(order.partnerReference()).isEqualTo(PARTNER_REFERENCE);
        }

        @Test
        @DisplayName("completePayout updates partner reference when new one is provided")
        void completePayoutUpdatesRef() {
            var order = aPayoutProcessingOrder().completePayout("new_ref_123", Instant.now());

            assertThat(order.partnerReference()).isEqualTo("new_ref_123");
        }
    }

    // =====================================================================
    // Immutability — transitions return new instances
    // =====================================================================

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("startRedemption returns a new instance")
        void startRedemptionReturnsNewInstance() {
            var pending = aPendingOrder();
            var redeeming = pending.startRedemption();

            assertThat(pending.status()).isEqualTo(PENDING);
            assertThat(redeeming.status()).isEqualTo(REDEEMING);
        }

        @Test
        @DisplayName("transitions update updatedAt timestamp")
        void transitionsUpdateTimestamp() {
            var pending = aPendingOrder();
            var redeeming = pending.startRedemption();

            assertThat(redeeming.updatedAt()).isAfterOrEqualTo(pending.updatedAt());
        }

        @Test
        @DisplayName("payoutId is preserved across transitions")
        void payoutIdPreservedAcrossTransitions() {
            var pending = aPendingOrder();
            var redeeming = pending.startRedemption();

            assertThat(redeeming.payoutId()).isEqualTo(pending.payoutId());
        }
    }
}
