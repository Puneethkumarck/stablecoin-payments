package com.stablecoin.payments.orchestrator.domain.model;

import com.stablecoin.payments.orchestrator.domain.statemachine.StateMachine;
import com.stablecoin.payments.orchestrator.domain.statemachine.StateTransition;
import lombok.AccessLevel;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.stablecoin.payments.orchestrator.domain.model.PaymentState.COMPENSATING_FIAT_REFUND;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentState.COMPENSATING_STABLECOIN_RETURN;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentState.COMPLETED;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentState.COMPLIANCE_CHECK;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentState.FAILED;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentState.INITIATED;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentTrigger.COMPLETE;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentTrigger.COMPLIANCE_PASSED;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentTrigger.CONFIRM_ON_CHAIN;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentTrigger.FAIL;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentTrigger.INITIATE_OFF_RAMP;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentTrigger.LOCK_FX;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentTrigger.SETTLE;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentTrigger.START_COMPENSATION;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentTrigger.START_COMPLIANCE;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentTrigger.START_FIAT_COLLECTION;
import static com.stablecoin.payments.orchestrator.domain.model.PaymentTrigger.SUBMIT_ON_CHAIN;

/**
 * Aggregate root for a cross-border payment.
 * <p>
 * Enforces the payment saga pipeline via an internal state machine:
 * {@code INITIATED -> COMPLIANCE_CHECK -> FX_LOCKED -> FIAT_COLLECTION_PENDING ->
 * FIAT_COLLECTED -> ON_CHAIN_SUBMITTED -> ON_CHAIN_CONFIRMED -> OFF_RAMP_INITIATED ->
 * SETTLED -> COMPLETED}.
 * <p>
 * Compensation states handle failure recovery: {@code COMPENSATING_FIAT_REFUND},
 * {@code COMPENSATING_STABLECOIN_RETURN}.
 * <p>
 * Immutable record — all state transitions return new instances via {@code toBuilder()}.
 */
@Builder(toBuilder = true, access = AccessLevel.PACKAGE)
public record Payment(
        UUID paymentId,
        String idempotencyKey,
        UUID correlationId,
        PaymentState state,
        UUID senderId,
        UUID recipientId,
        Money sourceAmount,
        String sourceCurrency,
        String targetCurrency,
        FxRate lockedFxRate,
        Money targetAmount,
        Corridor corridor,
        ChainId chainSelected,
        String txHash,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        Map<String, String> metadata
) {

    private static final Set<PaymentState> TERMINAL_STATES = Set.of(COMPLETED, FAILED);

    private static final StateMachine<PaymentState, PaymentTrigger> STATE_MACHINE =
            new StateMachine<>(List.of(
                    // ── Happy path ──────────────────────────────────────────────
                    new StateTransition<>(INITIATED, START_COMPLIANCE, COMPLIANCE_CHECK),
                    new StateTransition<>(COMPLIANCE_CHECK, COMPLIANCE_PASSED, PaymentState.FX_LOCKED),
                    new StateTransition<>(PaymentState.FX_LOCKED, LOCK_FX, PaymentState.FIAT_COLLECTION_PENDING),
                    new StateTransition<>(PaymentState.FIAT_COLLECTION_PENDING, START_FIAT_COLLECTION, PaymentState.FIAT_COLLECTION_PENDING),
                    new StateTransition<>(PaymentState.FIAT_COLLECTION_PENDING, PaymentTrigger.FIAT_COLLECTED, PaymentState.FIAT_COLLECTED),
                    new StateTransition<>(PaymentState.FIAT_COLLECTED, SUBMIT_ON_CHAIN, PaymentState.ON_CHAIN_SUBMITTED),
                    new StateTransition<>(PaymentState.ON_CHAIN_SUBMITTED, CONFIRM_ON_CHAIN, PaymentState.ON_CHAIN_CONFIRMED),
                    new StateTransition<>(PaymentState.ON_CHAIN_CONFIRMED, INITIATE_OFF_RAMP, PaymentState.OFF_RAMP_INITIATED),
                    new StateTransition<>(PaymentState.OFF_RAMP_INITIATED, SETTLE, PaymentState.SETTLED),
                    new StateTransition<>(PaymentState.SETTLED, COMPLETE, COMPLETED),

                    // ── Failure from any active state ───────────────────────────
                    new StateTransition<>(INITIATED, FAIL, FAILED),
                    new StateTransition<>(COMPLIANCE_CHECK, FAIL, FAILED),
                    new StateTransition<>(PaymentState.FX_LOCKED, FAIL, FAILED),
                    new StateTransition<>(PaymentState.FIAT_COLLECTION_PENDING, FAIL, FAILED),

                    // ── Compensation paths ──────────────────────────────────────
                    new StateTransition<>(PaymentState.FIAT_COLLECTED, START_COMPENSATION, COMPENSATING_FIAT_REFUND),
                    new StateTransition<>(PaymentState.ON_CHAIN_SUBMITTED, START_COMPENSATION, COMPENSATING_STABLECOIN_RETURN),
                    new StateTransition<>(PaymentState.ON_CHAIN_CONFIRMED, START_COMPENSATION, COMPENSATING_STABLECOIN_RETURN),
                    new StateTransition<>(PaymentState.OFF_RAMP_INITIATED, START_COMPENSATION, COMPENSATING_STABLECOIN_RETURN),

                    // ── Compensation terminal ───────────────────────────────────
                    new StateTransition<>(COMPENSATING_FIAT_REFUND, FAIL, FAILED),
                    new StateTransition<>(COMPENSATING_STABLECOIN_RETURN, FAIL, FAILED)
            ));

    // ── Factory Method ──────────────────────────────────────────────

    /**
     * Creates a new payment in INITIATED state.
     */
    public static Payment initiate(String idempotencyKey, UUID correlationId,
                                   UUID senderId, UUID recipientId,
                                   Money sourceAmount,
                                   String sourceCurrency, String targetCurrency,
                                   Corridor corridor) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        if (correlationId == null) {
            throw new IllegalArgumentException("correlationId is required");
        }
        if (senderId == null) {
            throw new IllegalArgumentException("senderId is required");
        }
        if (recipientId == null) {
            throw new IllegalArgumentException("recipientId is required");
        }
        if (sourceAmount == null) {
            throw new IllegalArgumentException("sourceAmount is required");
        }
        if (sourceCurrency == null || sourceCurrency.isBlank()) {
            throw new IllegalArgumentException("sourceCurrency is required");
        }
        if (targetCurrency == null || targetCurrency.isBlank()) {
            throw new IllegalArgumentException("targetCurrency is required");
        }
        if (corridor == null) {
            throw new IllegalArgumentException("corridor is required");
        }

        var now = Instant.now();
        return Payment.builder()
                .paymentId(UUID.randomUUID())
                .idempotencyKey(idempotencyKey)
                .correlationId(correlationId)
                .state(INITIATED)
                .senderId(senderId)
                .recipientId(recipientId)
                .sourceAmount(sourceAmount)
                .sourceCurrency(sourceCurrency)
                .targetCurrency(targetCurrency)
                .corridor(corridor)
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(now.plusSeconds(1800))
                .metadata(Map.of())
                .build();
    }

    // ── State Transition Methods ────────────────────────────────────

    /**
     * Starts compliance check. Transitions INITIATED -> COMPLIANCE_CHECK.
     */
    public Payment startComplianceCheck() {
        assertNotTerminal();
        var nextState = STATE_MACHINE.transition(state, START_COMPLIANCE);
        return toBuilder()
                .state(nextState)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Locks FX rate after compliance passes. Transitions COMPLIANCE_CHECK -> FX_LOCKED.
     */
    public Payment lockFxRate(FxRate fxRate) {
        assertNotTerminal();
        if (fxRate == null) {
            throw new IllegalArgumentException("FX rate is required");
        }
        var nextState = STATE_MACHINE.transition(state, COMPLIANCE_PASSED);
        var convertedAmount = sourceAmount.amount().multiply(fxRate.rate());
        return toBuilder()
                .state(nextState)
                .lockedFxRate(fxRate)
                .targetAmount(new Money(convertedAmount, targetCurrency))
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Starts fiat collection. Transitions FX_LOCKED -> FIAT_COLLECTION_PENDING.
     */
    public Payment startFiatCollection() {
        assertNotTerminal();
        var nextState = STATE_MACHINE.transition(state, LOCK_FX);
        return toBuilder()
                .state(nextState)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Confirms fiat collected. Transitions FIAT_COLLECTION_PENDING -> FIAT_COLLECTED.
     */
    public Payment confirmFiatCollected() {
        assertNotTerminal();
        var nextState = STATE_MACHINE.transition(state, PaymentTrigger.FIAT_COLLECTED);
        return toBuilder()
                .state(nextState)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Submits on-chain transaction. Transitions FIAT_COLLECTED -> ON_CHAIN_SUBMITTED.
     */
    public Payment submitOnChain(ChainId chainId) {
        assertNotTerminal();
        if (chainId == null) {
            throw new IllegalArgumentException("Chain ID is required");
        }
        var nextState = STATE_MACHINE.transition(state, SUBMIT_ON_CHAIN);
        return toBuilder()
                .state(nextState)
                .chainSelected(chainId)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Confirms on-chain transaction. Transitions ON_CHAIN_SUBMITTED -> ON_CHAIN_CONFIRMED.
     */
    public Payment confirmOnChain(String transactionHash) {
        assertNotTerminal();
        if (transactionHash == null || transactionHash.isBlank()) {
            throw new IllegalArgumentException("Transaction hash is required");
        }
        var nextState = STATE_MACHINE.transition(state, CONFIRM_ON_CHAIN);
        return toBuilder()
                .state(nextState)
                .txHash(transactionHash)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Initiates off-ramp. Transitions ON_CHAIN_CONFIRMED -> OFF_RAMP_INITIATED.
     */
    public Payment initiateOffRamp() {
        assertNotTerminal();
        var nextState = STATE_MACHINE.transition(state, INITIATE_OFF_RAMP);
        return toBuilder()
                .state(nextState)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Settles the payment. Transitions OFF_RAMP_INITIATED -> SETTLED.
     */
    public Payment settle() {
        assertNotTerminal();
        var nextState = STATE_MACHINE.transition(state, SETTLE);
        return toBuilder()
                .state(nextState)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Completes the payment. Transitions SETTLED -> COMPLETED.
     */
    public Payment complete() {
        assertNotTerminal();
        var nextState = STATE_MACHINE.transition(state, COMPLETE);
        return toBuilder()
                .state(nextState)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Fails the payment. Can be triggered from any non-terminal active state.
     */
    public Payment fail(String reason) {
        var nextState = STATE_MACHINE.transition(state, FAIL);
        return toBuilder()
                .state(nextState)
                .failureReason(reason)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Starts compensation flow. Applicable from post-fiat-collected states.
     */
    public Payment startCompensation(String reason) {
        assertNotTerminal();
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Compensation reason is required");
        }
        var nextState = STATE_MACHINE.transition(state, START_COMPENSATION);
        return toBuilder()
                .state(nextState)
                .failureReason(reason)
                .updatedAt(Instant.now())
                .build();
    }

    // ── Query Methods ───────────────────────────────────────────────

    /**
     * Returns true if this payment is in a terminal state (COMPLETED or FAILED).
     */
    public boolean isTerminal() {
        return TERMINAL_STATES.contains(state);
    }

    /**
     * Returns true if a given trigger can be applied from the current state.
     */
    public boolean canApply(PaymentTrigger trigger) {
        return STATE_MACHINE.canTransition(state, trigger);
    }

    /**
     * Returns true if this payment is in a compensation state.
     */
    public boolean isCompensating() {
        return state == COMPENSATING_FIAT_REFUND || state == COMPENSATING_STABLECOIN_RETURN;
    }

    // ── Invariant Guards ────────────────────────────────────────────

    private void assertNotTerminal() {
        if (isTerminal()) {
            throw new IllegalStateException(
                    "Payment %s is in terminal state %s and cannot be modified"
                            .formatted(paymentId, state));
        }
    }
}
