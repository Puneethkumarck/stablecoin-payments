package com.stablecoin.payments.onramp.domain.model;

import com.stablecoin.payments.onramp.domain.statemachine.StateMachine;
import com.stablecoin.payments.onramp.domain.statemachine.StateTransition;
import lombok.AccessLevel;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.stablecoin.payments.onramp.domain.model.CollectionStatus.AMOUNT_MISMATCH;
import static com.stablecoin.payments.onramp.domain.model.CollectionStatus.AWAITING_CONFIRMATION;
import static com.stablecoin.payments.onramp.domain.model.CollectionStatus.COLLECTED;
import static com.stablecoin.payments.onramp.domain.model.CollectionStatus.COLLECTION_FAILED;
import static com.stablecoin.payments.onramp.domain.model.CollectionStatus.MANUAL_REVIEW;
import static com.stablecoin.payments.onramp.domain.model.CollectionStatus.PAYMENT_INITIATED;
import static com.stablecoin.payments.onramp.domain.model.CollectionStatus.PENDING;
import static com.stablecoin.payments.onramp.domain.model.CollectionStatus.REFUNDED;
import static com.stablecoin.payments.onramp.domain.model.CollectionStatus.REFUND_INITIATED;
import static com.stablecoin.payments.onramp.domain.model.CollectionStatus.REFUND_PROCESSING;
import static com.stablecoin.payments.onramp.domain.model.CollectionTrigger.AMOUNT_MISMATCH_DETECTED;
import static com.stablecoin.payments.onramp.domain.model.CollectionTrigger.ESCALATE_MANUAL_REVIEW;
import static com.stablecoin.payments.onramp.domain.model.CollectionTrigger.FAIL;
import static com.stablecoin.payments.onramp.domain.model.CollectionTrigger.INITIATE_PAYMENT;
import static com.stablecoin.payments.onramp.domain.model.CollectionTrigger.PAYMENT_CONFIRMED;
import static com.stablecoin.payments.onramp.domain.model.CollectionTrigger.PAYMENT_TIMEOUT;
import static com.stablecoin.payments.onramp.domain.model.CollectionTrigger.PSP_SESSION_CREATED;
import static com.stablecoin.payments.onramp.domain.model.CollectionTrigger.REFUND_COMPLETED;
import static com.stablecoin.payments.onramp.domain.model.CollectionTrigger.REFUND_PROCESSING_STARTED;
import static com.stablecoin.payments.onramp.domain.model.CollectionTrigger.START_REFUND;

/**
 * Aggregate root for a fiat collection order.
 * <p>
 * Enforces the collection lifecycle via an internal state machine:
 * {@code PENDING -> PAYMENT_INITIATED -> AWAITING_CONFIRMATION -> COLLECTED}.
 * <p>
 * Handles edge cases: amount mismatches, manual review escalation,
 * and refund flows for compensation.
 * <p>
 * Immutable record — all state transitions return new instances via {@code toBuilder()}.
 */
@Builder(toBuilder = true, access = AccessLevel.PACKAGE)
public record CollectionOrder(
        UUID collectionId,
        UUID paymentId,
        UUID correlationId,
        Money amount,
        PaymentRail paymentRail,
        PspIdentifier psp,
        BankAccount senderAccount,
        CollectionStatus status,
        Money collectedAmount,
        String pspReference,
        Instant pspSettledAt,
        String failureReason,
        String errorCode,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt
) {

    private static final Set<CollectionStatus> TERMINAL_STATES =
            Set.of(COLLECTION_FAILED, REFUNDED, MANUAL_REVIEW);

    private static final StateMachine<CollectionStatus, CollectionTrigger> STATE_MACHINE =
            new StateMachine<>(List.of(
                    // -- Happy path -----------------------------------------------
                    new StateTransition<>(PENDING, INITIATE_PAYMENT, PAYMENT_INITIATED),
                    new StateTransition<>(PAYMENT_INITIATED, PSP_SESSION_CREATED, AWAITING_CONFIRMATION),
                    new StateTransition<>(AWAITING_CONFIRMATION, PAYMENT_CONFIRMED, COLLECTED),

                    // -- Failure paths --------------------------------------------
                    new StateTransition<>(AWAITING_CONFIRMATION, PAYMENT_TIMEOUT, COLLECTION_FAILED),
                    new StateTransition<>(AWAITING_CONFIRMATION, AMOUNT_MISMATCH_DETECTED, AMOUNT_MISMATCH),
                    new StateTransition<>(AMOUNT_MISMATCH, ESCALATE_MANUAL_REVIEW, MANUAL_REVIEW),
                    new StateTransition<>(PENDING, FAIL, COLLECTION_FAILED),
                    new StateTransition<>(PAYMENT_INITIATED, FAIL, COLLECTION_FAILED),

                    // -- Refund paths (compensation) ------------------------------
                    new StateTransition<>(COLLECTED, START_REFUND, REFUND_INITIATED),
                    new StateTransition<>(REFUND_INITIATED, REFUND_PROCESSING_STARTED, REFUND_PROCESSING),
                    new StateTransition<>(REFUND_PROCESSING, REFUND_COMPLETED, REFUNDED)
            ));

    // -- Factory Method ---------------------------------------------------

    /**
     * Creates a new collection order in PENDING state.
     */
    public static CollectionOrder initiate(UUID paymentId, UUID correlationId,
                                           Money amount, PaymentRail paymentRail,
                                           PspIdentifier psp, BankAccount senderAccount) {
        if (paymentId == null) {
            throw new IllegalArgumentException("paymentId is required");
        }
        if (correlationId == null) {
            throw new IllegalArgumentException("correlationId is required");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }
        if (paymentRail == null) {
            throw new IllegalArgumentException("paymentRail is required");
        }
        if (psp == null) {
            throw new IllegalArgumentException("psp is required");
        }
        if (senderAccount == null) {
            throw new IllegalArgumentException("senderAccount is required");
        }

        var now = Instant.now();
        return CollectionOrder.builder()
                .collectionId(UUID.randomUUID())
                .paymentId(paymentId)
                .correlationId(correlationId)
                .amount(amount)
                .paymentRail(paymentRail)
                .psp(psp)
                .senderAccount(senderAccount)
                .status(PENDING)
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(now.plusSeconds(1800))
                .build();
    }

    // -- State Transition Methods -----------------------------------------

    /**
     * Initiates payment with PSP. Transitions PENDING -> PAYMENT_INITIATED.
     */
    public CollectionOrder initiatePayment() {
        assertNotTerminal();
        var nextState = STATE_MACHINE.transition(status, INITIATE_PAYMENT);
        return toBuilder()
                .status(nextState)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Records PSP session creation. Transitions PAYMENT_INITIATED -> AWAITING_CONFIRMATION.
     */
    public CollectionOrder awaitConfirmation(String pspReference) {
        assertNotTerminal();
        if (pspReference == null || pspReference.isBlank()) {
            throw new IllegalArgumentException("PSP reference is required");
        }
        var nextState = STATE_MACHINE.transition(status, PSP_SESSION_CREATED);
        return toBuilder()
                .status(nextState)
                .pspReference(pspReference)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Confirms collection. Transitions AWAITING_CONFIRMATION -> COLLECTED.
     */
    public CollectionOrder confirmCollection(Money collectedAmount) {
        assertNotTerminal();
        if (collectedAmount == null) {
            throw new IllegalArgumentException("Collected amount is required");
        }
        var nextState = STATE_MACHINE.transition(status, PAYMENT_CONFIRMED);
        return toBuilder()
                .status(nextState)
                .collectedAmount(collectedAmount)
                .pspSettledAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Fails the collection. Can be triggered from PENDING or PAYMENT_INITIATED.
     */
    public CollectionOrder failCollection(String reason, String errorCode) {
        var nextState = STATE_MACHINE.transition(status, FAIL);
        return toBuilder()
                .status(nextState)
                .failureReason(reason)
                .errorCode(errorCode)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Detects amount mismatch. Transitions AWAITING_CONFIRMATION -> AMOUNT_MISMATCH.
     */
    public CollectionOrder detectAmountMismatch() {
        assertNotTerminal();
        var nextState = STATE_MACHINE.transition(status, AMOUNT_MISMATCH_DETECTED);
        return toBuilder()
                .status(nextState)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Escalates to manual review. Transitions AMOUNT_MISMATCH -> MANUAL_REVIEW.
     */
    public CollectionOrder escalateToManualReview() {
        assertNotTerminal();
        var nextState = STATE_MACHINE.transition(status, ESCALATE_MANUAL_REVIEW);
        return toBuilder()
                .status(nextState)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Initiates refund (compensation). Transitions COLLECTED -> REFUND_INITIATED.
     */
    public CollectionOrder initiateRefund() {
        assertNotTerminal();
        var nextState = STATE_MACHINE.transition(status, START_REFUND);
        return toBuilder()
                .status(nextState)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Starts refund processing. Transitions REFUND_INITIATED -> REFUND_PROCESSING.
     */
    public CollectionOrder startRefundProcessing() {
        assertNotTerminal();
        var nextState = STATE_MACHINE.transition(status, REFUND_PROCESSING_STARTED);
        return toBuilder()
                .status(nextState)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Completes refund. Transitions REFUND_PROCESSING -> REFUNDED.
     */
    public CollectionOrder completeRefund() {
        assertNotTerminal();
        var nextState = STATE_MACHINE.transition(status, REFUND_COMPLETED);
        return toBuilder()
                .status(nextState)
                .updatedAt(Instant.now())
                .build();
    }

    // -- Query Methods ----------------------------------------------------

    /**
     * Returns true if this collection order is in a terminal state.
     */
    public boolean isTerminal() {
        return TERMINAL_STATES.contains(status);
    }

    /**
     * Returns true if a given trigger can be applied from the current state.
     */
    public boolean canApply(CollectionTrigger trigger) {
        return STATE_MACHINE.canTransition(status, trigger);
    }

    // -- Invariant Guards -------------------------------------------------

    private void assertNotTerminal() {
        if (isTerminal()) {
            throw new IllegalStateException(
                    "CollectionOrder %s is in terminal state %s and cannot be modified"
                            .formatted(collectionId, status));
        }
    }
}
