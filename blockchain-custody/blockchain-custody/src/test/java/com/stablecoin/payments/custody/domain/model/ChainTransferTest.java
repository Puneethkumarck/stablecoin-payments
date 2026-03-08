package com.stablecoin.payments.custody.domain.model;

import com.stablecoin.payments.custody.domain.statemachine.StateMachineException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

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
import static com.stablecoin.payments.custody.domain.model.TransferType.FORWARD;
import static com.stablecoin.payments.custody.domain.model.TransferType.RETURN;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.AMOUNT;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.CHAIN_BASE;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.CORRELATION_ID;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.FROM_ADDRESS;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.FROM_WALLET_ID;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.PARENT_TRANSFER_ID;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.PAYMENT_ID;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.TO_ADDRESS;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.TX_HASH;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.TX_HASH_RESUBMIT;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.USDC;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.aChainSelectedTransfer;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.aConfirmedTransfer;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.aConfirmingTransfer;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.aFailedTransfer;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.aPendingReturnTransfer;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.aPendingTransfer;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.aResubmittingTransfer;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.aSigningTransfer;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.aSubmittedTransfer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ChainTransfer")
class ChainTransferTest {

    @Nested
    @DisplayName("Factory Method")
    class FactoryMethod {

        @Test
        @DisplayName("creates PENDING FORWARD transfer with correct fields")
        void createsPendingForwardTransfer() {
            var result = aPendingTransfer();

            assertThat(result.transferId()).isNotNull();
            assertThat(result)
                    .satisfies(t -> {
                        assertThat(t.paymentId()).isEqualTo(PAYMENT_ID);
                        assertThat(t.correlationId()).isEqualTo(CORRELATION_ID);
                        assertThat(t.transferType()).isEqualTo(FORWARD);
                        assertThat(t.parentTransferId()).isNull();
                        assertThat(t.stablecoin()).isEqualTo(USDC);
                        assertThat(t.amount()).isEqualByComparingTo(AMOUNT);
                        assertThat(t.fromWalletId()).isEqualTo(FROM_WALLET_ID);
                        assertThat(t.toWalletAddress()).isEqualTo(TO_ADDRESS);
                        assertThat(t.fromAddress()).isEqualTo(FROM_ADDRESS);
                        assertThat(t.status()).isEqualTo(PENDING);
                        assertThat(t.attemptCount()).isZero();
                        assertThat(t.createdAt()).isNotNull();
                        assertThat(t.updatedAt()).isNotNull();
                    });
        }

        @Test
        @DisplayName("creates RETURN transfer with parentTransferId")
        void createsReturnTransferWithParent() {
            var result = aPendingReturnTransfer();

            assertThat(result.transferType()).isEqualTo(RETURN);
            assertThat(result.parentTransferId()).isEqualTo(PARENT_TRANSFER_ID);
            assertThat(result.status()).isEqualTo(PENDING);
        }

        @Test
        @DisplayName("FORWARD transfer allows null parentTransferId")
        void forwardTransferAllowsNullParent() {
            var result = ChainTransfer.initiate(
                    PAYMENT_ID, CORRELATION_ID, FORWARD, null,
                    USDC, AMOUNT, FROM_WALLET_ID, TO_ADDRESS, FROM_ADDRESS
            );

            assertThat(result.parentTransferId()).isNull();
        }

        @Test
        @DisplayName("rejects null paymentId")
        void rejectsNullPaymentId() {
            assertThatThrownBy(() -> ChainTransfer.initiate(
                    null, CORRELATION_ID, FORWARD, null,
                    USDC, AMOUNT, FROM_WALLET_ID, TO_ADDRESS, FROM_ADDRESS
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("paymentId is required");
        }

        @Test
        @DisplayName("rejects null correlationId")
        void rejectsNullCorrelationId() {
            assertThatThrownBy(() -> ChainTransfer.initiate(
                    PAYMENT_ID, null, FORWARD, null,
                    USDC, AMOUNT, FROM_WALLET_ID, TO_ADDRESS, FROM_ADDRESS
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("correlationId is required");
        }

        @Test
        @DisplayName("rejects null transferType")
        void rejectsNullTransferType() {
            assertThatThrownBy(() -> ChainTransfer.initiate(
                    PAYMENT_ID, CORRELATION_ID, null, null,
                    USDC, AMOUNT, FROM_WALLET_ID, TO_ADDRESS, FROM_ADDRESS
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("transferType is required");
        }

        @Test
        @DisplayName("rejects null stablecoin")
        void rejectsNullStablecoin() {
            assertThatThrownBy(() -> ChainTransfer.initiate(
                    PAYMENT_ID, CORRELATION_ID, FORWARD, null,
                    null, AMOUNT, FROM_WALLET_ID, TO_ADDRESS, FROM_ADDRESS
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("stablecoin is required");
        }

        @Test
        @DisplayName("rejects null amount")
        void rejectsNullAmount() {
            assertThatThrownBy(() -> ChainTransfer.initiate(
                    PAYMENT_ID, CORRELATION_ID, FORWARD, null,
                    USDC, null, FROM_WALLET_ID, TO_ADDRESS, FROM_ADDRESS
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("amount must be positive");
        }

        @Test
        @DisplayName("rejects zero amount")
        void rejectsZeroAmount() {
            assertThatThrownBy(() -> ChainTransfer.initiate(
                    PAYMENT_ID, CORRELATION_ID, FORWARD, null,
                    USDC, BigDecimal.ZERO, FROM_WALLET_ID, TO_ADDRESS, FROM_ADDRESS
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("amount must be positive");
        }

        @Test
        @DisplayName("rejects negative amount")
        void rejectsNegativeAmount() {
            assertThatThrownBy(() -> ChainTransfer.initiate(
                    PAYMENT_ID, CORRELATION_ID, FORWARD, null,
                    USDC, new BigDecimal("-10.00"), FROM_WALLET_ID, TO_ADDRESS, FROM_ADDRESS
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("amount must be positive");
        }

        @Test
        @DisplayName("rejects null fromWalletId")
        void rejectsNullFromWalletId() {
            assertThatThrownBy(() -> ChainTransfer.initiate(
                    PAYMENT_ID, CORRELATION_ID, FORWARD, null,
                    USDC, AMOUNT, null, TO_ADDRESS, FROM_ADDRESS
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("fromWalletId is required");
        }

        @Test
        @DisplayName("rejects null toWalletAddress")
        void rejectsNullToWalletAddress() {
            assertThatThrownBy(() -> ChainTransfer.initiate(
                    PAYMENT_ID, CORRELATION_ID, FORWARD, null,
                    USDC, AMOUNT, FROM_WALLET_ID, null, FROM_ADDRESS
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("toWalletAddress is required");
        }

        @Test
        @DisplayName("rejects blank toWalletAddress")
        void rejectsBlankToWalletAddress() {
            assertThatThrownBy(() -> ChainTransfer.initiate(
                    PAYMENT_ID, CORRELATION_ID, FORWARD, null,
                    USDC, AMOUNT, FROM_WALLET_ID, "  ", FROM_ADDRESS
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("toWalletAddress is required");
        }

        @Test
        @DisplayName("rejects null fromAddress")
        void rejectsNullFromAddress() {
            assertThatThrownBy(() -> ChainTransfer.initiate(
                    PAYMENT_ID, CORRELATION_ID, FORWARD, null,
                    USDC, AMOUNT, FROM_WALLET_ID, TO_ADDRESS, null
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("fromAddress is required");
        }

        @Test
        @DisplayName("rejects blank fromAddress")
        void rejectsBlankFromAddress() {
            assertThatThrownBy(() -> ChainTransfer.initiate(
                    PAYMENT_ID, CORRELATION_ID, FORWARD, null,
                    USDC, AMOUNT, FROM_WALLET_ID, TO_ADDRESS, "  "
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("fromAddress is required");
        }
    }

    @Nested
    @DisplayName("Happy Path Transitions")
    class HappyPathTransitions {

        @Test
        @DisplayName("PENDING -> CHAIN_SELECTED via selectChain()")
        void pendingToChainSelected() {
            var pending = aPendingTransfer();

            var result = pending.selectChain(CHAIN_BASE);

            assertThat(result.status()).isEqualTo(CHAIN_SELECTED);
            assertThat(result.chainId()).isEqualTo(CHAIN_BASE);
            assertThat(result.updatedAt()).isAfterOrEqualTo(pending.updatedAt());
        }

        @Test
        @DisplayName("CHAIN_SELECTED -> SIGNING via startSigning()")
        void chainSelectedToSigning() {
            var chainSelected = aChainSelectedTransfer();

            var result = chainSelected.startSigning(42L);

            assertThat(result.status()).isEqualTo(SIGNING);
            assertThat(result.nonce()).isEqualTo(42L);
        }

        @Test
        @DisplayName("SIGNING -> SUBMITTED via submit() — attemptCount increments to 1")
        void signingToSubmitted() {
            var signing = aSigningTransfer();

            var result = signing.submit(TX_HASH);

            assertThat(result.status()).isEqualTo(SUBMITTED);
            assertThat(result.txHash()).isEqualTo(TX_HASH);
            assertThat(result.attemptCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("SUBMITTED -> CONFIRMING via startConfirming()")
        void submittedToConfirming() {
            var submitted = aSubmittedTransfer();

            var result = submitted.startConfirming();

            assertThat(result.status()).isEqualTo(CONFIRMING);
        }

        @Test
        @DisplayName("CONFIRMING -> CONFIRMED via confirm()")
        void confirmingToConfirmed() {
            var confirming = aConfirmingTransfer();
            var gasUsed = new BigDecimal("0.002100");
            var gasPriceGwei = new BigDecimal("25.5");

            var result = confirming.confirm(12345L, 15, gasUsed, gasPriceGwei);

            assertThat(result.status()).isEqualTo(CONFIRMED);
            assertThat(result.blockNumber()).isEqualTo(12345L);
            assertThat(result.confirmations()).isEqualTo(15);
            assertThat(result.gasUsed()).isEqualByComparingTo(gasUsed);
            assertThat(result.gasPriceGwei()).isEqualByComparingTo(gasPriceGwei);
            assertThat(result.blockConfirmedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Resubmission Path")
    class ResubmissionPath {

        @Test
        @DisplayName("SUBMITTED -> RESUBMITTING via markForResubmission()")
        void submittedToResubmitting() {
            var submitted = aSubmittedTransfer();

            var result = submitted.markForResubmission();

            assertThat(result.status()).isEqualTo(RESUBMITTING);
        }

        @Test
        @DisplayName("RESUBMITTING -> SUBMITTED via resubmit() — attemptCount increments")
        void resubmittingToSubmitted() {
            var resubmitting = aResubmittingTransfer();
            int previousAttemptCount = resubmitting.attemptCount();

            var result = resubmitting.resubmit(TX_HASH_RESUBMIT);

            assertThat(result.status()).isEqualTo(SUBMITTED);
            assertThat(result.txHash()).isEqualTo(TX_HASH_RESUBMIT);
            assertThat(result.attemptCount()).isEqualTo(previousAttemptCount + 1);
        }

        @Test
        @DisplayName("full resubmission cycle: SUBMITTED -> RESUBMITTING -> SUBMITTED increments attemptCount twice")
        void fullResubmissionCycleIncrementsAttemptCount() {
            var submitted = aSubmittedTransfer();
            assertThat(submitted.attemptCount()).isEqualTo(1);

            var resubmitting = submitted.markForResubmission();
            assertThat(resubmitting.attemptCount()).isEqualTo(1);

            var resubmitted = resubmitting.resubmit(TX_HASH_RESUBMIT);
            assertThat(resubmitted.attemptCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("resubmit() rejects null txHash")
        void resubmitRejectsNullTxHash() {
            var resubmitting = aResubmittingTransfer();

            assertThatThrownBy(() -> resubmitting.resubmit(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Transaction hash is required for resubmission");
        }

        @Test
        @DisplayName("resubmit() rejects blank txHash")
        void resubmitRejectsBlankTxHash() {
            var resubmitting = aResubmittingTransfer();

            assertThatThrownBy(() -> resubmitting.resubmit("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Transaction hash is required for resubmission");
        }
    }

    @Nested
    @DisplayName("Failure Transitions")
    class FailureTransitions {

        @Test
        @DisplayName("CHAIN_SELECTED -> FAILED via fail()")
        void chainSelectedToFailed() {
            var transfer = aChainSelectedTransfer();

            var result = transfer.fail("Chain unavailable", "CHAIN_DOWN");

            assertThat(result.status()).isEqualTo(FAILED);
            assertThat(result.failureReason()).isEqualTo("Chain unavailable");
            assertThat(result.errorCode()).isEqualTo("CHAIN_DOWN");
        }

        @Test
        @DisplayName("SIGNING -> FAILED via fail()")
        void signingToFailed() {
            var transfer = aSigningTransfer();

            var result = transfer.fail("Key not found", "SIGNING_ERROR");

            assertThat(result.status()).isEqualTo(FAILED);
            assertThat(result.failureReason()).isEqualTo("Key not found");
            assertThat(result.errorCode()).isEqualTo("SIGNING_ERROR");
        }

        @Test
        @DisplayName("SUBMITTED -> FAILED via fail()")
        void submittedToFailed() {
            var transfer = aSubmittedTransfer();

            var result = transfer.fail("Transaction reverted", "REVERTED");

            assertThat(result.status()).isEqualTo(FAILED);
            assertThat(result.failureReason()).isEqualTo("Transaction reverted");
        }

        @Test
        @DisplayName("RESUBMITTING -> FAILED via fail()")
        void resubmittingToFailed() {
            var transfer = aResubmittingTransfer();

            var result = transfer.fail("Max retries exceeded", "MAX_RETRIES");

            assertThat(result.status()).isEqualTo(FAILED);
            assertThat(result.failureReason()).isEqualTo("Max retries exceeded");
        }

        @Test
        @DisplayName("CONFIRMING -> FAILED via fail()")
        void confirmingToFailed() {
            var transfer = aConfirmingTransfer();

            var result = transfer.fail("Chain reorg", "REORG");

            assertThat(result.status()).isEqualTo(FAILED);
            assertThat(result.failureReason()).isEqualTo("Chain reorg");
        }

        @Test
        @DisplayName("fail() allows null reason and code")
        void failAllowsNullReasonAndCode() {
            var transfer = aChainSelectedTransfer();

            var result = transfer.fail(null, null);

            assertThat(result.status()).isEqualTo(FAILED);
            assertThat(result.failureReason()).isNull();
            assertThat(result.errorCode()).isNull();
        }
    }

    @Nested
    @DisplayName("Terminal State Guard")
    class TerminalStateGuard {

        @Test
        @DisplayName("CONFIRMED rejects selectChain()")
        void confirmedRejectsSelectChain() {
            var confirmed = aConfirmedTransfer();

            assertThatThrownBy(() -> confirmed.selectChain(CHAIN_BASE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("CONFIRMED rejects startSigning()")
        void confirmedRejectsStartSigning() {
            var confirmed = aConfirmedTransfer();

            assertThatThrownBy(() -> confirmed.startSigning(1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("CONFIRMED rejects submit()")
        void confirmedRejectsSubmit() {
            var confirmed = aConfirmedTransfer();

            assertThatThrownBy(() -> confirmed.submit(TX_HASH))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("CONFIRMED rejects startConfirming()")
        void confirmedRejectsStartConfirming() {
            var confirmed = aConfirmedTransfer();

            assertThatThrownBy(() -> confirmed.startConfirming())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("CONFIRMED rejects confirm()")
        void confirmedRejectsConfirm() {
            var confirmed = aConfirmedTransfer();

            assertThatThrownBy(() -> confirmed.confirm(999L, 20, BigDecimal.ONE, BigDecimal.ONE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("CONFIRMED rejects markForResubmission()")
        void confirmedRejectsMarkForResubmission() {
            var confirmed = aConfirmedTransfer();

            assertThatThrownBy(() -> confirmed.markForResubmission())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("CONFIRMED rejects resubmit()")
        void confirmedRejectsResubmit() {
            var confirmed = aConfirmedTransfer();

            assertThatThrownBy(() -> confirmed.resubmit(TX_HASH_RESUBMIT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("FAILED rejects selectChain()")
        void failedRejectsSelectChain() {
            var failed = aFailedTransfer();

            assertThatThrownBy(() -> failed.selectChain(CHAIN_BASE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("FAILED rejects startSigning()")
        void failedRejectsStartSigning() {
            var failed = aFailedTransfer();

            assertThatThrownBy(() -> failed.startSigning(1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("FAILED rejects submit()")
        void failedRejectsSubmit() {
            var failed = aFailedTransfer();

            assertThatThrownBy(() -> failed.submit(TX_HASH))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("FAILED rejects startConfirming()")
        void failedRejectsStartConfirming() {
            var failed = aFailedTransfer();

            assertThatThrownBy(() -> failed.startConfirming())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("FAILED rejects confirm()")
        void failedRejectsConfirm() {
            var failed = aFailedTransfer();

            assertThatThrownBy(() -> failed.confirm(999L, 20, BigDecimal.ONE, BigDecimal.ONE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("FAILED rejects markForResubmission()")
        void failedRejectsMarkForResubmission() {
            var failed = aFailedTransfer();

            assertThatThrownBy(() -> failed.markForResubmission())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }

        @Test
        @DisplayName("FAILED rejects resubmit()")
        void failedRejectsResubmit() {
            var failed = aFailedTransfer();

            assertThatThrownBy(() -> failed.resubmit(TX_HASH_RESUBMIT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }
    }

    @Nested
    @DisplayName("Invalid Transitions")
    class InvalidTransitions {

        static Stream<Arguments> invalidTransitions() {
            return Stream.of(
                    // PENDING only allows SELECT_CHAIN
                    Arguments.of(PENDING, START_SIGNING, "PENDING + START_SIGNING"),
                    Arguments.of(PENDING, SUBMIT, "PENDING + SUBMIT"),
                    Arguments.of(PENDING, START_CONFIRMING, "PENDING + START_CONFIRMING"),
                    Arguments.of(PENDING, CONFIRM, "PENDING + CONFIRM"),
                    Arguments.of(PENDING, RESUBMIT, "PENDING + RESUBMIT"),
                    Arguments.of(PENDING, CONFIRM_SUBMISSION, "PENDING + CONFIRM_SUBMISSION"),
                    Arguments.of(PENDING, FAIL, "PENDING + FAIL"),

                    // CHAIN_SELECTED only allows START_SIGNING and FAIL
                    Arguments.of(CHAIN_SELECTED, SELECT_CHAIN, "CHAIN_SELECTED + SELECT_CHAIN"),
                    Arguments.of(CHAIN_SELECTED, SUBMIT, "CHAIN_SELECTED + SUBMIT"),
                    Arguments.of(CHAIN_SELECTED, START_CONFIRMING, "CHAIN_SELECTED + START_CONFIRMING"),
                    Arguments.of(CHAIN_SELECTED, CONFIRM, "CHAIN_SELECTED + CONFIRM"),
                    Arguments.of(CHAIN_SELECTED, RESUBMIT, "CHAIN_SELECTED + RESUBMIT"),
                    Arguments.of(CHAIN_SELECTED, CONFIRM_SUBMISSION, "CHAIN_SELECTED + CONFIRM_SUBMISSION"),

                    // SIGNING only allows SUBMIT and FAIL
                    Arguments.of(SIGNING, SELECT_CHAIN, "SIGNING + SELECT_CHAIN"),
                    Arguments.of(SIGNING, START_SIGNING, "SIGNING + START_SIGNING"),
                    Arguments.of(SIGNING, START_CONFIRMING, "SIGNING + START_CONFIRMING"),
                    Arguments.of(SIGNING, CONFIRM, "SIGNING + CONFIRM"),
                    Arguments.of(SIGNING, RESUBMIT, "SIGNING + RESUBMIT"),
                    Arguments.of(SIGNING, CONFIRM_SUBMISSION, "SIGNING + CONFIRM_SUBMISSION"),

                    // SUBMITTED only allows START_CONFIRMING, RESUBMIT, and FAIL
                    Arguments.of(SUBMITTED, SELECT_CHAIN, "SUBMITTED + SELECT_CHAIN"),
                    Arguments.of(SUBMITTED, START_SIGNING, "SUBMITTED + START_SIGNING"),
                    Arguments.of(SUBMITTED, SUBMIT, "SUBMITTED + SUBMIT"),
                    Arguments.of(SUBMITTED, CONFIRM, "SUBMITTED + CONFIRM"),
                    Arguments.of(SUBMITTED, CONFIRM_SUBMISSION, "SUBMITTED + CONFIRM_SUBMISSION"),

                    // RESUBMITTING only allows CONFIRM_SUBMISSION and FAIL
                    Arguments.of(RESUBMITTING, SELECT_CHAIN, "RESUBMITTING + SELECT_CHAIN"),
                    Arguments.of(RESUBMITTING, START_SIGNING, "RESUBMITTING + START_SIGNING"),
                    Arguments.of(RESUBMITTING, SUBMIT, "RESUBMITTING + SUBMIT"),
                    Arguments.of(RESUBMITTING, START_CONFIRMING, "RESUBMITTING + START_CONFIRMING"),
                    Arguments.of(RESUBMITTING, CONFIRM, "RESUBMITTING + CONFIRM"),
                    Arguments.of(RESUBMITTING, RESUBMIT, "RESUBMITTING + RESUBMIT"),

                    // CONFIRMING only allows CONFIRM and FAIL
                    Arguments.of(CONFIRMING, SELECT_CHAIN, "CONFIRMING + SELECT_CHAIN"),
                    Arguments.of(CONFIRMING, START_SIGNING, "CONFIRMING + START_SIGNING"),
                    Arguments.of(CONFIRMING, SUBMIT, "CONFIRMING + SUBMIT"),
                    Arguments.of(CONFIRMING, START_CONFIRMING, "CONFIRMING + START_CONFIRMING"),
                    Arguments.of(CONFIRMING, RESUBMIT, "CONFIRMING + RESUBMIT"),
                    Arguments.of(CONFIRMING, CONFIRM_SUBMISSION, "CONFIRMING + CONFIRM_SUBMISSION")
            );
        }

        @ParameterizedTest(name = "{2} is invalid")
        @MethodSource("invalidTransitions")
        @DisplayName("rejects invalid state-trigger combination")
        void rejectsInvalidTransition(TransferStatus status, TransferTrigger trigger, String description) {
            // Build a transfer at the given status using the fixture helpers
            var transfer = transferInStatus(status);

            // canApply should return false for these combinations
            assertThat(transfer.canApply(trigger)).isFalse();
        }

        private ChainTransfer transferInStatus(TransferStatus status) {
            return switch (status) {
                case PENDING -> aPendingTransfer();
                case CHAIN_SELECTED -> aChainSelectedTransfer();
                case SIGNING -> aSigningTransfer();
                case SUBMITTED -> aSubmittedTransfer();
                case RESUBMITTING -> aResubmittingTransfer();
                case CONFIRMING -> aConfirmingTransfer();
                case CONFIRMED -> aConfirmedTransfer();
                case FAILED -> aFailedTransfer();
            };
        }
    }

    @Nested
    @DisplayName("Input Validation on Transition Methods")
    class InputValidation {

        @Test
        @DisplayName("selectChain() rejects null chainId")
        void selectChainRejectsNull() {
            var pending = aPendingTransfer();

            assertThatThrownBy(() -> pending.selectChain(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Chain ID is required");
        }

        @Test
        @DisplayName("submit() rejects null txHash")
        void submitRejectsNullTxHash() {
            var signing = aSigningTransfer();

            assertThatThrownBy(() -> signing.submit(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Transaction hash is required");
        }

        @Test
        @DisplayName("submit() rejects blank txHash")
        void submitRejectsBlankTxHash() {
            var signing = aSigningTransfer();

            assertThatThrownBy(() -> signing.submit("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Transaction hash is required");
        }

        @Test
        @DisplayName("submit() rejects empty txHash")
        void submitRejectsEmptyTxHash() {
            var signing = aSigningTransfer();

            assertThatThrownBy(() -> signing.submit(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Transaction hash is required");
        }

        @Test
        @DisplayName("startSigning() accepts null nonce")
        void startSigningAcceptsNullNonce() {
            var chainSelected = aChainSelectedTransfer();

            var result = chainSelected.startSigning(null);

            assertThat(result.status()).isEqualTo(SIGNING);
            assertThat(result.nonce()).isNull();
        }
    }

    @Nested
    @DisplayName("Query Methods")
    class QueryMethods {

        @Test
        @DisplayName("isTerminal() returns true for CONFIRMED")
        void isTerminalForConfirmed() {
            assertThat(aConfirmedTransfer().isTerminal()).isTrue();
        }

        @Test
        @DisplayName("isTerminal() returns true for FAILED")
        void isTerminalForFailed() {
            assertThat(aFailedTransfer().isTerminal()).isTrue();
        }

        @Test
        @DisplayName("isTerminal() returns false for PENDING")
        void isTerminalFalseForPending() {
            assertThat(aPendingTransfer().isTerminal()).isFalse();
        }

        @Test
        @DisplayName("isTerminal() returns false for CHAIN_SELECTED")
        void isTerminalFalseForChainSelected() {
            assertThat(aChainSelectedTransfer().isTerminal()).isFalse();
        }

        @Test
        @DisplayName("isTerminal() returns false for SIGNING")
        void isTerminalFalseForSigning() {
            assertThat(aSigningTransfer().isTerminal()).isFalse();
        }

        @Test
        @DisplayName("isTerminal() returns false for SUBMITTED")
        void isTerminalFalseForSubmitted() {
            assertThat(aSubmittedTransfer().isTerminal()).isFalse();
        }

        @Test
        @DisplayName("isTerminal() returns false for RESUBMITTING")
        void isTerminalFalseForResubmitting() {
            assertThat(aResubmittingTransfer().isTerminal()).isFalse();
        }

        @Test
        @DisplayName("isTerminal() returns false for CONFIRMING")
        void isTerminalFalseForConfirming() {
            assertThat(aConfirmingTransfer().isTerminal()).isFalse();
        }

        @Test
        @DisplayName("canApply(SELECT_CHAIN) returns true for PENDING")
        void canApplySelectChainFromPending() {
            assertThat(aPendingTransfer().canApply(SELECT_CHAIN)).isTrue();
        }

        @Test
        @DisplayName("canApply(START_SIGNING) returns true for CHAIN_SELECTED")
        void canApplyStartSigningFromChainSelected() {
            assertThat(aChainSelectedTransfer().canApply(START_SIGNING)).isTrue();
        }

        @Test
        @DisplayName("canApply(FAIL) returns true for CHAIN_SELECTED")
        void canApplyFailFromChainSelected() {
            assertThat(aChainSelectedTransfer().canApply(FAIL)).isTrue();
        }

        @Test
        @DisplayName("canApply(SUBMIT) returns false for PENDING")
        void canApplySubmitFromPendingIsFalse() {
            assertThat(aPendingTransfer().canApply(SUBMIT)).isFalse();
        }

        @Test
        @DisplayName("canApply(RESUBMIT) returns true for SUBMITTED")
        void canApplyResubmitFromSubmitted() {
            assertThat(aSubmittedTransfer().canApply(RESUBMIT)).isTrue();
        }

        @Test
        @DisplayName("canApply(CONFIRM_SUBMISSION) returns true for RESUBMITTING")
        void canApplyConfirmSubmissionFromResubmitting() {
            assertThat(aResubmittingTransfer().canApply(CONFIRM_SUBMISSION)).isTrue();
        }

        @Test
        @DisplayName("canApply returns false for all triggers from CONFIRMED")
        void canApplyReturnsFalseForConfirmed() {
            var confirmed = aConfirmedTransfer();
            for (var trigger : TransferTrigger.values()) {
                assertThat(confirmed.canApply(trigger))
                        .as("CONFIRMED + %s should be false", trigger)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("canApply returns false for all triggers from FAILED")
        void canApplyReturnsFalseForFailed() {
            var failed = aFailedTransfer();
            for (var trigger : TransferTrigger.values()) {
                assertThat(failed.canApply(trigger))
                        .as("FAILED + %s should be false", trigger)
                        .isFalse();
            }
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("selectChain returns a new instance, original unchanged")
        void selectChainPreservesOriginal() {
            var original = aPendingTransfer();

            var updated = original.selectChain(CHAIN_BASE);

            assertThat(original.status()).isEqualTo(PENDING);
            assertThat(updated.status()).isEqualTo(CHAIN_SELECTED);
            assertThat(original.transferId()).isEqualTo(updated.transferId());
        }

        @Test
        @DisplayName("fail returns a new instance, original unchanged")
        void failPreservesOriginal() {
            var original = aChainSelectedTransfer();

            var failed = original.fail("reason", "code");

            assertThat(original.status()).isEqualTo(CHAIN_SELECTED);
            assertThat(failed.status()).isEqualTo(FAILED);
        }
    }
}
