package com.stablecoin.payments.offramp.domain.model;

import com.stablecoin.payments.offramp.domain.statemachine.StateMachine;
import com.stablecoin.payments.offramp.domain.statemachine.StateTransition;
import lombok.AccessLevel;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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

/**
 * Aggregate root for a fiat off-ramp payout order.
 * <p>
 * Enforces the payout lifecycle via an internal state machine:
 * {@code PENDING -> REDEEMING -> REDEEMED -> PAYOUT_INITIATED -> PAYOUT_PROCESSING -> COMPLETED}.
 * <p>
 * For HOLD_STABLECOIN type: {@code PENDING -> STABLECOIN_HELD -> COMPLETED}.
 * <p>
 * Failure paths lead to REDEMPTION_FAILED or PAYOUT_FAILED, both of which can escalate to MANUAL_REVIEW.
 * <p>
 * Immutable record — all state transitions return new instances via {@code toBuilder()}.
 */
@Builder(toBuilder = true, access = AccessLevel.PACKAGE)
public record PayoutOrder(
        UUID payoutId,
        UUID paymentId,
        UUID correlationId,
        UUID transferId,
        PayoutType payoutType,
        StablecoinTicker stablecoin,
        BigDecimal redeemedAmount,
        String targetCurrency,
        BigDecimal fiatAmount,
        BigDecimal appliedFxRate,
        UUID recipientId,
        String recipientAccountHash,
        BankAccount bankAccount,
        MobileMoneyAccount mobileMoneyAccount,
        PaymentRail paymentRail,
        PartnerIdentifier offRampPartner,
        PayoutStatus status,
        String partnerReference,
        Instant partnerSettledAt,
        String failureReason,
        String errorCode,
        Instant createdAt,
        Instant updatedAt
) {

    private static final Set<PayoutStatus> TERMINAL_STATES = Set.of(COMPLETED, MANUAL_REVIEW);

    /** Fiat payout tolerance: fiat_amount must match redeemed * fx_rate within ±0.01 */
    private static final BigDecimal FIAT_AMOUNT_TOLERANCE = new BigDecimal("0.01");

    private static final StateMachine<PayoutStatus, PayoutTrigger> STATE_MACHINE =
            new StateMachine<>(List.of(
                    // -- Fiat happy path ------------------------------------------
                    new StateTransition<>(PENDING, START_REDEMPTION, REDEEMING),
                    new StateTransition<>(REDEEMING, COMPLETE_REDEMPTION, REDEEMED),
                    new StateTransition<>(REDEEMED, INITIATE_PAYOUT, PAYOUT_INITIATED),
                    new StateTransition<>(PAYOUT_INITIATED, MARK_PAYOUT_PROCESSING, PAYOUT_PROCESSING),
                    new StateTransition<>(PAYOUT_PROCESSING, COMPLETE_PAYOUT, COMPLETED),

                    // -- Redemption failure ----------------------------------------
                    new StateTransition<>(REDEEMING, FAIL_REDEMPTION, REDEMPTION_FAILED),
                    new StateTransition<>(REDEMPTION_FAILED, ESCALATE_MANUAL_REVIEW, MANUAL_REVIEW),

                    // -- Payout failure --------------------------------------------
                    new StateTransition<>(PAYOUT_INITIATED, FAIL_PAYOUT, PAYOUT_FAILED),
                    new StateTransition<>(PAYOUT_PROCESSING, FAIL_PAYOUT, PAYOUT_FAILED),
                    new StateTransition<>(PAYOUT_FAILED, ESCALATE_MANUAL_REVIEW, MANUAL_REVIEW),

                    // -- Hold stablecoin path --------------------------------------
                    new StateTransition<>(PENDING, HOLD_STABLECOIN, STABLECOIN_HELD),
                    new StateTransition<>(STABLECOIN_HELD, COMPLETE_HOLD, COMPLETED)
            ));

    // -- Factory Method ---------------------------------------------------

    /**
     * Creates a new payout order in PENDING state.
     */
    public static PayoutOrder create(UUID paymentId, UUID correlationId, UUID transferId,
                                     PayoutType payoutType, StablecoinTicker stablecoin,
                                     BigDecimal redeemedAmount, String targetCurrency,
                                     BigDecimal appliedFxRate, UUID recipientId,
                                     String recipientAccountHash,
                                     BankAccount bankAccount, MobileMoneyAccount mobileMoneyAccount,
                                     PaymentRail paymentRail, PartnerIdentifier offRampPartner) {
        if (paymentId == null) {
            throw new IllegalArgumentException("paymentId is required");
        }
        if (correlationId == null) {
            throw new IllegalArgumentException("correlationId is required");
        }
        if (transferId == null) {
            throw new IllegalArgumentException("transferId is required");
        }
        if (payoutType == null) {
            throw new IllegalArgumentException("payoutType is required");
        }
        if (stablecoin == null) {
            throw new IllegalArgumentException("stablecoin is required");
        }
        if (redeemedAmount == null || redeemedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("redeemedAmount must be positive");
        }
        if (targetCurrency == null || targetCurrency.isBlank()) {
            throw new IllegalArgumentException("targetCurrency is required");
        }
        if (appliedFxRate == null || appliedFxRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("appliedFxRate must be positive");
        }
        if (recipientId == null) {
            throw new IllegalArgumentException("recipientId is required");
        }
        if (recipientAccountHash == null || recipientAccountHash.isBlank()) {
            throw new IllegalArgumentException("recipientAccountHash is required");
        }
        if (paymentRail == null) {
            throw new IllegalArgumentException("paymentRail is required");
        }
        if (offRampPartner == null) {
            throw new IllegalArgumentException("offRampPartner is required");
        }
        if (bankAccount == null && mobileMoneyAccount == null) {
            throw new IllegalArgumentException("Either bankAccount or mobileMoneyAccount is required");
        }

        var now = Instant.now();
        return PayoutOrder.builder()
                .payoutId(UUID.randomUUID())
                .paymentId(paymentId)
                .correlationId(correlationId)
                .transferId(transferId)
                .payoutType(payoutType)
                .stablecoin(stablecoin)
                .redeemedAmount(redeemedAmount)
                .targetCurrency(targetCurrency)
                .appliedFxRate(appliedFxRate)
                .recipientId(recipientId)
                .recipientAccountHash(recipientAccountHash)
                .bankAccount(bankAccount)
                .mobileMoneyAccount(mobileMoneyAccount)
                .paymentRail(paymentRail)
                .offRampPartner(offRampPartner)
                .status(PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    // -- State Transition Methods -----------------------------------------

    /**
     * Starts the stablecoin redemption process. Transitions PENDING -> REDEEMING.
     */
    public PayoutOrder startRedemption() {
        assertNotTerminal();
        var nextState = STATE_MACHINE.transition(status, START_REDEMPTION);
        return toBuilder()
                .status(nextState)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Completes the redemption and records the fiat amount received.
     * Transitions REDEEMING -> REDEEMED.
     * <p>
     * Invariant: fiatAmount must match redeemedAmount * appliedFxRate within ±0.01 tolerance.
     */
    public PayoutOrder completeRedemption(BigDecimal receivedFiatAmount) {
        assertNotTerminal();
        if (receivedFiatAmount == null || receivedFiatAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Received fiat amount must be positive");
        }
        validateFiatAmountTolerance(receivedFiatAmount);

        var nextState = STATE_MACHINE.transition(status, COMPLETE_REDEMPTION);
        return toBuilder()
                .status(nextState)
                .fiatAmount(receivedFiatAmount)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Fails the redemption. Transitions REDEEMING -> REDEMPTION_FAILED.
     */
    public PayoutOrder failRedemption(String reason) {
        var nextState = STATE_MACHINE.transition(status, FAIL_REDEMPTION);
        return toBuilder()
                .status(nextState)
                .failureReason(reason)
                .errorCode("FR-2002")
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Initiates the fiat payout with a partner. Transitions REDEEMED -> PAYOUT_INITIATED.
     * <p>
     * Invariant: cannot initiate payout unless stablecoin has been redeemed (status == REDEEMED).
     */
    public PayoutOrder initiatePayout(String partnerRef) {
        assertNotTerminal();
        if (partnerRef == null || partnerRef.isBlank()) {
            throw new IllegalArgumentException("Partner reference is required");
        }
        var nextState = STATE_MACHINE.transition(status, INITIATE_PAYOUT);
        return toBuilder()
                .status(nextState)
                .partnerReference(partnerRef)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Marks the payout as processing (partner acknowledged). Transitions PAYOUT_INITIATED -> PAYOUT_PROCESSING.
     */
    public PayoutOrder markPayoutProcessing() {
        assertNotTerminal();
        var nextState = STATE_MACHINE.transition(status, MARK_PAYOUT_PROCESSING);
        return toBuilder()
                .status(nextState)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Completes the payout. Transitions PAYOUT_PROCESSING -> COMPLETED.
     */
    public PayoutOrder completePayout(String partnerRef, Instant settledAt) {
        assertNotTerminal();
        if (settledAt == null) {
            throw new IllegalArgumentException("Settlement timestamp is required");
        }
        var nextState = STATE_MACHINE.transition(status, COMPLETE_PAYOUT);
        return toBuilder()
                .status(nextState)
                .partnerReference(partnerRef != null ? partnerRef : partnerReference)
                .partnerSettledAt(settledAt)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Fails the payout. Can be triggered from PAYOUT_INITIATED or PAYOUT_PROCESSING.
     */
    public PayoutOrder failPayout(String reason) {
        var nextState = STATE_MACHINE.transition(status, FAIL_PAYOUT);
        return toBuilder()
                .status(nextState)
                .failureReason(reason)
                .errorCode("FR-2003")
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Escalates to manual review. Can be triggered from REDEMPTION_FAILED or PAYOUT_FAILED.
     */
    public PayoutOrder escalateToManualReview() {
        var nextState = STATE_MACHINE.transition(status, ESCALATE_MANUAL_REVIEW);
        return toBuilder()
                .status(nextState)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Holds stablecoin without redemption. Transitions PENDING -> STABLECOIN_HELD.
     * Only valid for HOLD_STABLECOIN payout type.
     */
    public PayoutOrder holdStablecoin() {
        assertNotTerminal();
        if (payoutType != PayoutType.HOLD_STABLECOIN) {
            throw new IllegalStateException(
                    "holdStablecoin() is only valid for HOLD_STABLECOIN payout type, current: " + payoutType);
        }
        var nextState = STATE_MACHINE.transition(status, HOLD_STABLECOIN);
        return toBuilder()
                .status(nextState)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Completes the hold. Transitions STABLECOIN_HELD -> COMPLETED.
     */
    public PayoutOrder completeHold() {
        assertNotTerminal();
        var nextState = STATE_MACHINE.transition(status, COMPLETE_HOLD);
        return toBuilder()
                .status(nextState)
                .updatedAt(Instant.now())
                .build();
    }

    // -- Query Methods ----------------------------------------------------

    /**
     * Returns true if this payout order is in a terminal state (COMPLETED or MANUAL_REVIEW).
     */
    public boolean isTerminal() {
        return TERMINAL_STATES.contains(status);
    }

    /**
     * Returns true if a given trigger can be applied from the current state.
     */
    public boolean canApply(PayoutTrigger trigger) {
        return STATE_MACHINE.canTransition(status, trigger);
    }

    // -- Invariant Guards -------------------------------------------------

    private void assertNotTerminal() {
        if (isTerminal()) {
            throw new IllegalStateException(
                    "PayoutOrder %s is in terminal state %s and cannot be modified"
                            .formatted(payoutId, status));
        }
    }

    /**
     * Validates that the fiat amount matches redeemedAmount * appliedFxRate within tolerance.
     */
    private void validateFiatAmountTolerance(BigDecimal receivedFiatAmount) {
        var expectedFiat = redeemedAmount.multiply(appliedFxRate);
        var difference = receivedFiatAmount.subtract(expectedFiat).abs();
        if (difference.compareTo(FIAT_AMOUNT_TOLERANCE) > 0) {
            throw new IllegalArgumentException(
                    "Fiat amount %s exceeds tolerance ±%s from expected %s (redeemed %s * rate %s)"
                            .formatted(receivedFiatAmount, FIAT_AMOUNT_TOLERANCE,
                                    expectedFiat, redeemedAmount, appliedFxRate));
        }
    }
}
