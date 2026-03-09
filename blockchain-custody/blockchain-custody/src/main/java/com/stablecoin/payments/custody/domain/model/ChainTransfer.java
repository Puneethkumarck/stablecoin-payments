package com.stablecoin.payments.custody.domain.model;

import com.stablecoin.payments.custody.domain.statemachine.StateMachine;
import com.stablecoin.payments.custody.domain.statemachine.StateTransition;
import lombok.AccessLevel;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.stablecoin.payments.custody.domain.model.TransferStatus.CHAIN_SELECTED;
import static com.stablecoin.payments.custody.domain.model.TransferStatus.CONFIRMED;
import static com.stablecoin.payments.custody.domain.model.TransferStatus.CONFIRMING;
import static com.stablecoin.payments.custody.domain.model.TransferStatus.FAILED;
import static com.stablecoin.payments.custody.domain.model.TransferStatus.PENDING;
import static com.stablecoin.payments.custody.domain.model.TransferStatus.RESUBMITTING;
import static com.stablecoin.payments.custody.domain.model.TransferStatus.SIGNING;
import static com.stablecoin.payments.custody.domain.model.TransferStatus.SUBMITTED;
import static com.stablecoin.payments.custody.domain.model.TransferTrigger.CONFIRM;
import static com.stablecoin.payments.custody.domain.model.TransferTrigger.CONFIRM_SUBMISSION;
import static com.stablecoin.payments.custody.domain.model.TransferTrigger.FAIL;
import static com.stablecoin.payments.custody.domain.model.TransferTrigger.RESUBMIT;
import static com.stablecoin.payments.custody.domain.model.TransferTrigger.SELECT_CHAIN;
import static com.stablecoin.payments.custody.domain.model.TransferTrigger.START_CONFIRMING;
import static com.stablecoin.payments.custody.domain.model.TransferTrigger.START_SIGNING;
import static com.stablecoin.payments.custody.domain.model.TransferTrigger.SUBMIT;

/**
 * Aggregate root for a blockchain chain transfer.
 * <p>
 * Enforces the transfer lifecycle via an internal state machine:
 * {@code PENDING -> CHAIN_SELECTED -> SIGNING -> SUBMITTED -> CONFIRMING -> CONFIRMED}.
 * <p>
 * Resubmission path handles mempool drops: {@code SUBMITTED -> RESUBMITTING -> SUBMITTED}.
 * <p>
 * Failure can occur from multiple states: CHAIN_SELECTED, SIGNING, SUBMITTED, RESUBMITTING, CONFIRMING.
 * <p>
 * Immutable record — all state transitions return new instances via {@code toBuilder()}.
 */
@Builder(toBuilder = true, access = AccessLevel.PACKAGE)
public record ChainTransfer(
        UUID transferId,
        UUID paymentId,
        UUID correlationId,
        TransferType transferType,
        UUID parentTransferId,
        ChainId chainId,
        StablecoinTicker stablecoin,
        BigDecimal amount,
        UUID fromWalletId,
        String toWalletAddress,
        String fromAddress,
        Long nonce,
        String txHash,
        TransferStatus status,
        Long blockNumber,
        Instant blockConfirmedAt,
        Integer confirmations,
        BigDecimal gasUsed,
        BigDecimal gasPriceGwei,
        int attemptCount,
        String failureReason,
        String errorCode,
        Instant createdAt,
        Instant updatedAt
) {

    private static final Set<TransferStatus> TERMINAL_STATES = Set.of(CONFIRMED, FAILED);

    private static final StateMachine<TransferStatus, TransferTrigger> STATE_MACHINE =
            new StateMachine<>(List.of(
                    // -- Happy path -------------------------------------------------
                    new StateTransition<>(PENDING, SELECT_CHAIN, CHAIN_SELECTED),
                    new StateTransition<>(CHAIN_SELECTED, START_SIGNING, SIGNING),
                    new StateTransition<>(SIGNING, SUBMIT, SUBMITTED),
                    new StateTransition<>(SUBMITTED, START_CONFIRMING, CONFIRMING),
                    new StateTransition<>(CONFIRMING, CONFIRM, CONFIRMED),

                    // -- Resubmission path ------------------------------------------
                    new StateTransition<>(SUBMITTED, RESUBMIT, RESUBMITTING),
                    new StateTransition<>(CONFIRMING, RESUBMIT, RESUBMITTING),
                    new StateTransition<>(RESUBMITTING, CONFIRM_SUBMISSION, SUBMITTED),

                    // -- Failure from multiple states --------------------------------
                    new StateTransition<>(CHAIN_SELECTED, FAIL, FAILED),
                    new StateTransition<>(SIGNING, FAIL, FAILED),
                    new StateTransition<>(SUBMITTED, FAIL, FAILED),
                    new StateTransition<>(RESUBMITTING, FAIL, FAILED),
                    new StateTransition<>(CONFIRMING, FAIL, FAILED)
            ));

    // -- Factory Method -------------------------------------------------

    /**
     * Creates a new chain transfer in PENDING state.
     */
    public static ChainTransfer initiate(UUID paymentId, UUID correlationId,
                                         TransferType transferType, UUID parentTransferId,
                                         StablecoinTicker stablecoin, BigDecimal amount,
                                         UUID fromWalletId, String toWalletAddress,
                                         String fromAddress) {
        if (paymentId == null) {
            throw new IllegalArgumentException("paymentId is required");
        }
        if (correlationId == null) {
            throw new IllegalArgumentException("correlationId is required");
        }
        if (transferType == null) {
            throw new IllegalArgumentException("transferType is required");
        }
        if (stablecoin == null) {
            throw new IllegalArgumentException("stablecoin is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (fromWalletId == null) {
            throw new IllegalArgumentException("fromWalletId is required");
        }
        if (toWalletAddress == null || toWalletAddress.isBlank()) {
            throw new IllegalArgumentException("toWalletAddress is required");
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalArgumentException("fromAddress is required");
        }

        var now = Instant.now();
        return ChainTransfer.builder()
                .transferId(UUID.randomUUID())
                .paymentId(paymentId)
                .correlationId(correlationId)
                .transferType(transferType)
                .parentTransferId(parentTransferId)
                .stablecoin(stablecoin)
                .amount(amount)
                .fromWalletId(fromWalletId)
                .toWalletAddress(toWalletAddress)
                .fromAddress(fromAddress)
                .status(PENDING)
                .attemptCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    // -- State Transition Methods ---------------------------------------

    /**
     * Selects the blockchain chain. Transitions PENDING -> CHAIN_SELECTED.
     */
    public ChainTransfer selectChain(ChainId selectedChainId) {
        assertNotTerminal();
        if (selectedChainId == null) {
            throw new IllegalArgumentException("Chain ID is required");
        }
        var nextState = STATE_MACHINE.transition(status, SELECT_CHAIN);
        return toBuilder()
                .status(nextState)
                .chainId(selectedChainId)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Starts the signing process. Transitions CHAIN_SELECTED -> SIGNING.
     */
    public ChainTransfer startSigning(Long signingNonce) {
        assertNotTerminal();
        var nextState = STATE_MACHINE.transition(status, START_SIGNING);
        return toBuilder()
                .status(nextState)
                .nonce(signingNonce)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Submits the signed transaction to the chain. Transitions SIGNING -> SUBMITTED.
     */
    public ChainTransfer submit(String transactionHash) {
        assertNotTerminal();
        if (transactionHash == null || transactionHash.isBlank()) {
            throw new IllegalArgumentException("Transaction hash is required");
        }
        var nextState = STATE_MACHINE.transition(status, SUBMIT);
        return toBuilder()
                .status(nextState)
                .txHash(transactionHash)
                .attemptCount(attemptCount + 1)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Starts the confirmation monitoring. Transitions SUBMITTED -> CONFIRMING.
     */
    public ChainTransfer startConfirming() {
        assertNotTerminal();
        var nextState = STATE_MACHINE.transition(status, START_CONFIRMING);
        return toBuilder()
                .status(nextState)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Confirms the transfer after sufficient block confirmations.
     * Transitions CONFIRMING -> CONFIRMED.
     */
    public ChainTransfer confirm(long confirmedBlockNumber, int confirmedConfirmations,
                                 BigDecimal confirmedGasUsed, BigDecimal confirmedGasPriceGwei) {
        assertNotTerminal();
        var nextState = STATE_MACHINE.transition(status, CONFIRM);
        return toBuilder()
                .status(nextState)
                .blockNumber(confirmedBlockNumber)
                .confirmations(confirmedConfirmations)
                .gasUsed(confirmedGasUsed)
                .gasPriceGwei(confirmedGasPriceGwei)
                .blockConfirmedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Marks the transfer for resubmission (mempool drop / timeout).
     * Transitions SUBMITTED -> RESUBMITTING.
     */
    public ChainTransfer markForResubmission() {
        assertNotTerminal();
        var nextState = STATE_MACHINE.transition(status, RESUBMIT);
        return toBuilder()
                .status(nextState)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Resubmits with a new transaction hash. Transitions RESUBMITTING -> SUBMITTED.
     */
    public ChainTransfer resubmit(String newTxHash) {
        assertNotTerminal();
        if (newTxHash == null || newTxHash.isBlank()) {
            throw new IllegalArgumentException("Transaction hash is required for resubmission");
        }
        var nextState = STATE_MACHINE.transition(status, CONFIRM_SUBMISSION);
        return toBuilder()
                .status(nextState)
                .txHash(newTxHash)
                .attemptCount(attemptCount + 1)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Claims a resubmission attempt by incrementing the attempt counter.
     * Must be persisted BEFORE calling the custody engine to ensure crash safety.
     * Stays in RESUBMITTING state.
     */
    public ChainTransfer claimResubmission() {
        assertNotTerminal();
        if (status != RESUBMITTING) {
            throw new IllegalStateException(
                    "Can only claim resubmission in RESUBMITTING state, current: " + status);
        }
        return toBuilder()
                .attemptCount(attemptCount + 1)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Completes a previously claimed resubmission with the new transaction hash.
     * Transitions RESUBMITTING -> SUBMITTED without incrementing attempt count
     * (already incremented by {@link #claimResubmission()}).
     */
    public ChainTransfer confirmResubmission(String newTxHash) {
        assertNotTerminal();
        if (newTxHash == null || newTxHash.isBlank()) {
            throw new IllegalArgumentException("Transaction hash is required for resubmission");
        }
        var nextState = STATE_MACHINE.transition(status, CONFIRM_SUBMISSION);
        return toBuilder()
                .status(nextState)
                .txHash(newTxHash)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Fails the transfer. Can be triggered from multiple non-terminal states.
     */
    public ChainTransfer fail(String reason, String code) {
        var nextState = STATE_MACHINE.transition(status, FAIL);
        return toBuilder()
                .status(nextState)
                .failureReason(reason)
                .errorCode(code)
                .updatedAt(Instant.now())
                .build();
    }

    // -- Query Methods --------------------------------------------------

    /**
     * Returns true if this transfer is in a terminal state (CONFIRMED or FAILED).
     */
    public boolean isTerminal() {
        return TERMINAL_STATES.contains(status);
    }

    /**
     * Returns true if a given trigger can be applied from the current status.
     */
    public boolean canApply(TransferTrigger trigger) {
        return STATE_MACHINE.canTransition(status, trigger);
    }

    // -- Invariant Guards -----------------------------------------------

    private void assertNotTerminal() {
        if (isTerminal()) {
            throw new IllegalStateException(
                    "Transfer %s is in terminal state %s and cannot be modified"
                            .formatted(transferId, status));
        }
    }
}
