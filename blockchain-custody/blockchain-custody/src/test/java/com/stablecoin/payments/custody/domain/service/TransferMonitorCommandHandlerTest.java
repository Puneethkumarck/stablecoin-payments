package com.stablecoin.payments.custody.domain.service;

import com.stablecoin.payments.custody.domain.event.TransferConfirmedEvent;
import com.stablecoin.payments.custody.domain.event.TransferFailedEvent;
import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.TransferLifecycleEvent;
import com.stablecoin.payments.custody.domain.model.TransferStatus;
import com.stablecoin.payments.custody.domain.port.ChainRpcProvider;
import com.stablecoin.payments.custody.domain.port.ChainTransferRepository;
import com.stablecoin.payments.custody.domain.port.CustodyEngine;
import com.stablecoin.payments.custody.domain.port.SignRequest;
import com.stablecoin.payments.custody.domain.port.TransferEventPublisher;
import com.stablecoin.payments.custody.domain.port.TransferLifecycleEventRepository;
import com.stablecoin.payments.custody.domain.port.WalletBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static com.stablecoin.payments.custody.fixtures.TestUtils.eqIgnoring;
import static com.stablecoin.payments.custody.fixtures.TestUtils.eqIgnoringTimestamps;
import static com.stablecoin.payments.custody.fixtures.TransferMonitorFixtures.CHAIN_BASE;
import static com.stablecoin.payments.custody.fixtures.TransferMonitorFixtures.FROM_WALLET_ID;
import static com.stablecoin.payments.custody.fixtures.TransferMonitorFixtures.GAS_PRICE;
import static com.stablecoin.payments.custody.fixtures.TransferMonitorFixtures.GAS_USED;
import static com.stablecoin.payments.custody.fixtures.TransferMonitorFixtures.LATEST_BLOCK;
import static com.stablecoin.payments.custody.fixtures.TransferMonitorFixtures.RECEIPT_BLOCK;
import static com.stablecoin.payments.custody.fixtures.TransferMonitorFixtures.RESUBMIT_TX_HASH;
import static com.stablecoin.payments.custody.fixtures.TransferMonitorFixtures.TX_HASH;
import static com.stablecoin.payments.custody.fixtures.TransferMonitorFixtures.USDC;
import static com.stablecoin.payments.custody.fixtures.TransferMonitorFixtures.aBalanceWithReserved;
import static com.stablecoin.payments.custody.fixtures.TransferMonitorFixtures.aConfirmingTransferOnBase;
import static com.stablecoin.payments.custody.fixtures.TransferMonitorFixtures.aFailedReceipt;
import static com.stablecoin.payments.custody.fixtures.TransferMonitorFixtures.aMaxAttemptsResubmittingTransfer;
import static com.stablecoin.payments.custody.fixtures.TransferMonitorFixtures.aResubmitSignResult;
import static com.stablecoin.payments.custody.fixtures.TransferMonitorFixtures.aResubmittingTransfer;
import static com.stablecoin.payments.custody.fixtures.TransferMonitorFixtures.aSubmittedTransferOnBase;
import static com.stablecoin.payments.custody.fixtures.TransferMonitorFixtures.aSubmittedTransferOnEthereum;
import static com.stablecoin.payments.custody.fixtures.TransferMonitorFixtures.aSuccessfulReceipt;
import static com.stablecoin.payments.custody.fixtures.TransferMonitorFixtures.defaultChainConfirmationProperties;
import static com.stablecoin.payments.custody.fixtures.TransferMonitorFixtures.defaultMonitorProperties;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferMonitorCommandHandler")
class TransferMonitorCommandHandlerTest {

    @Mock private ChainTransferRepository chainTransferRepository;
    @Mock private WalletBalanceRepository walletBalanceRepository;
    @Mock private TransferLifecycleEventRepository lifecycleEventRepository;
    @Mock private ChainRpcProvider chainRpcProvider;
    @Mock private CustodyEngine custodyEngine;
    @Mock private TransferEventPublisher transferEventPublisher;

    private TransferMonitorCommandHandler handler;

    // Use system clock for the default handler — transfers are created with Instant.now()
    private static final Clock SYSTEM_CLOCK = Clock.systemUTC();

    @BeforeEach
    void setUp() {
        handler = new TransferMonitorCommandHandler(
                chainTransferRepository,
                walletBalanceRepository,
                lifecycleEventRepository,
                chainRpcProvider,
                custodyEngine,
                transferEventPublisher,
                defaultMonitorProperties(),
                defaultChainConfirmationProperties(),
                SYSTEM_CLOCK
        );
    }

    @Nested
    @DisplayName("SUBMITTED transfers")
    class SubmittedTransfers {

        @Test
        @DisplayName("should confirm transfer when receipt found and confirmations sufficient")
        void shouldConfirmWhenReceiptFoundAndConfirmationsSufficient() {
            // given
            var transfer = aSubmittedTransferOnBase();
            var receipt = aSuccessfulReceipt();
            var balance = aBalanceWithReserved();

            given(chainTransferRepository.findByStatus(TransferStatus.SUBMITTED))
                    .willReturn(List.of(transfer));
            given(chainTransferRepository.findByStatus(TransferStatus.CONFIRMING))
                    .willReturn(List.of());
            given(chainTransferRepository.findByStatus(TransferStatus.RESUBMITTING))
                    .willReturn(List.of());
            given(chainRpcProvider.getTransactionReceipt(CHAIN_BASE, TX_HASH))
                    .willReturn(receipt);
            given(chainRpcProvider.getLatestBlockNumber(CHAIN_BASE))
                    .willReturn(LATEST_BLOCK);
            given(walletBalanceRepository.findByWalletIdAndStablecoinForUpdate(FROM_WALLET_ID, USDC))
                    .willReturn(Optional.of(balance));

            var confirming = transfer.startConfirming();
            var expectedConfirmed = confirming.confirm(
                    RECEIPT_BLOCK, (int) (LATEST_BLOCK - RECEIPT_BLOCK), GAS_USED, GAS_PRICE);
            var expectedDebitedBalance = balance.confirmDebit(transfer.amount());
            var expectedEvent = new TransferConfirmedEvent(
                    expectedConfirmed.transferId(),
                    expectedConfirmed.paymentId(),
                    expectedConfirmed.correlationId(),
                    expectedConfirmed.chainId().value(),
                    expectedConfirmed.txHash(),
                    expectedConfirmed.blockNumber(),
                    expectedConfirmed.confirmations(),
                    expectedConfirmed.gasUsed(),
                    null
            );

            // when
            handler.monitorPendingTransfers();

            // then
            then(chainTransferRepository).should().save(eqIgnoringTimestamps(confirming));
            then(chainTransferRepository).should().save(eqIgnoringTimestamps(expectedConfirmed));
            then(walletBalanceRepository).should().save(eqIgnoringTimestamps(expectedDebitedBalance));
            then(transferEventPublisher).should().publish(eqIgnoringTimestamps(expectedEvent));
            then(lifecycleEventRepository).should().save(
                    eqIgnoring(TransferLifecycleEvent.record(transfer.transferId(), "CONFIRMING"), "eventId"));
            then(lifecycleEventRepository).should().save(
                    eqIgnoring(TransferLifecycleEvent.record(transfer.transferId(), "CONFIRMED"), "eventId"));
        }

        @Test
        @DisplayName("should stay CONFIRMING when receipt found but not enough confirmations")
        void shouldStayConfirmingWhenNotEnoughConfirmations() {
            // given — Ethereum requires 32 confirmations, only 5 available
            var transfer = aSubmittedTransferOnEthereum();
            var chainEthereum = new ChainId("ethereum");
            var receipt = aSuccessfulReceipt();

            given(chainTransferRepository.findByStatus(TransferStatus.SUBMITTED))
                    .willReturn(List.of(transfer));
            given(chainTransferRepository.findByStatus(TransferStatus.CONFIRMING))
                    .willReturn(List.of());
            given(chainTransferRepository.findByStatus(TransferStatus.RESUBMITTING))
                    .willReturn(List.of());
            given(chainRpcProvider.getTransactionReceipt(chainEthereum, TX_HASH))
                    .willReturn(receipt);
            given(chainRpcProvider.getLatestBlockNumber(chainEthereum))
                    .willReturn(RECEIPT_BLOCK + 5);

            // when
            handler.monitorPendingTransfers();

            // then — should save the CONFIRMING transition but NOT the CONFIRMED transition
            var confirming = transfer.startConfirming();
            then(chainTransferRepository).should().save(eqIgnoringTimestamps(confirming));
            then(transferEventPublisher).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should mark as RESUBMITTING when no receipt and stuck beyond timeout")
        void shouldMarkResubmittingWhenStuckBeyondTimeout() {
            // given — use a clock far enough in the future so the transfer is stuck
            // Transfer's updatedAt uses Instant.now() (real system time)
            // We set the handler's clock 300s ahead of real time to simulate passage of time
            var futureClock = Clock.offset(Clock.systemUTC(), java.time.Duration.ofSeconds(300));
            var handlerWithFutureClock = new TransferMonitorCommandHandler(
                    chainTransferRepository,
                    walletBalanceRepository,
                    lifecycleEventRepository,
                    chainRpcProvider,
                    custodyEngine,
                    transferEventPublisher,
                    defaultMonitorProperties(),
                    defaultChainConfirmationProperties(),
                    futureClock
            );

            var transfer = aSubmittedTransferOnBase();

            given(chainTransferRepository.findByStatus(TransferStatus.SUBMITTED))
                    .willReturn(List.of(transfer));
            given(chainTransferRepository.findByStatus(TransferStatus.CONFIRMING))
                    .willReturn(List.of());
            given(chainTransferRepository.findByStatus(TransferStatus.RESUBMITTING))
                    .willReturn(List.of());
            given(chainRpcProvider.getTransactionReceipt(CHAIN_BASE, TX_HASH))
                    .willReturn(null);

            var expectedResubmitting = transfer.markForResubmission();

            // when
            handlerWithFutureClock.monitorPendingTransfers();

            // then
            then(chainTransferRepository).should().save(eqIgnoringTimestamps(expectedResubmitting));
            then(lifecycleEventRepository).should().save(
                    eqIgnoring(TransferLifecycleEvent.record(transfer.transferId(), "RESUBMITTING"), "eventId"));
        }

        @Test
        @DisplayName("should not mark as RESUBMITTING when no receipt but not stuck yet")
        void shouldNotMarkResubmittingWhenNotStuckYet() {
            // given — transfer just submitted (updatedAt ≈ now), 120s timeout not reached
            var transfer = aSubmittedTransferOnBase();

            given(chainTransferRepository.findByStatus(TransferStatus.SUBMITTED))
                    .willReturn(List.of(transfer));
            given(chainTransferRepository.findByStatus(TransferStatus.CONFIRMING))
                    .willReturn(List.of());
            given(chainTransferRepository.findByStatus(TransferStatus.RESUBMITTING))
                    .willReturn(List.of());
            given(chainRpcProvider.getTransactionReceipt(CHAIN_BASE, TX_HASH))
                    .willReturn(null);

            // when
            handler.monitorPendingTransfers();

            // then — no state change, nothing saved
            then(chainTransferRepository).should(never()).save(eqIgnoringTimestamps(transfer));
            then(lifecycleEventRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should fail transfer when receipt indicates on-chain revert")
        void shouldFailWhenReceiptIndicatesRevert() {
            // given
            var transfer = aSubmittedTransferOnBase();
            var failedReceipt = aFailedReceipt();

            given(chainTransferRepository.findByStatus(TransferStatus.SUBMITTED))
                    .willReturn(List.of(transfer));
            given(chainTransferRepository.findByStatus(TransferStatus.CONFIRMING))
                    .willReturn(List.of());
            given(chainTransferRepository.findByStatus(TransferStatus.RESUBMITTING))
                    .willReturn(List.of());
            given(chainRpcProvider.getTransactionReceipt(CHAIN_BASE, TX_HASH))
                    .willReturn(failedReceipt);

            var expectedFailed = transfer.fail("Transaction reverted on-chain", "TX_REVERTED");
            var expectedFailedEvent = new TransferFailedEvent(
                    expectedFailed.transferId(),
                    expectedFailed.paymentId(),
                    expectedFailed.correlationId(),
                    expectedFailed.chainId().value(),
                    "Transaction reverted on-chain",
                    "TX_REVERTED",
                    null
            );

            // when
            handler.monitorPendingTransfers();

            // then
            then(chainTransferRepository).should().save(eqIgnoringTimestamps(expectedFailed));
            then(transferEventPublisher).should().publish(eqIgnoringTimestamps(expectedFailedEvent));
            then(lifecycleEventRepository).should().save(
                    eqIgnoring(TransferLifecycleEvent.record(transfer.transferId(), "FAILED"), "eventId"));
        }
    }

    @Nested
    @DisplayName("CONFIRMING transfers")
    class ConfirmingTransfers {

        @Test
        @DisplayName("should confirm transfer when confirmations meet minimum")
        void shouldConfirmWhenConfirmationsMeetMinimum() {
            // given
            var transfer = aConfirmingTransferOnBase();
            var receipt = aSuccessfulReceipt();
            var balance = aBalanceWithReserved();

            given(chainTransferRepository.findByStatus(TransferStatus.SUBMITTED))
                    .willReturn(List.of());
            given(chainTransferRepository.findByStatus(TransferStatus.CONFIRMING))
                    .willReturn(List.of(transfer));
            given(chainTransferRepository.findByStatus(TransferStatus.RESUBMITTING))
                    .willReturn(List.of());
            given(chainRpcProvider.getTransactionReceipt(CHAIN_BASE, TX_HASH))
                    .willReturn(receipt);
            given(chainRpcProvider.getLatestBlockNumber(CHAIN_BASE))
                    .willReturn(LATEST_BLOCK);
            given(walletBalanceRepository.findByWalletIdAndStablecoinForUpdate(FROM_WALLET_ID, USDC))
                    .willReturn(Optional.of(balance));

            var expectedConfirmed = transfer.confirm(
                    RECEIPT_BLOCK, (int) (LATEST_BLOCK - RECEIPT_BLOCK), GAS_USED, GAS_PRICE);
            var expectedDebitedBalance = balance.confirmDebit(transfer.amount());

            // when
            handler.monitorPendingTransfers();

            // then
            then(chainTransferRepository).should().save(eqIgnoringTimestamps(expectedConfirmed));
            then(walletBalanceRepository).should().save(eqIgnoringTimestamps(expectedDebitedBalance));
            then(transferEventPublisher).should().publish(eqIgnoringTimestamps(
                    new TransferConfirmedEvent(
                            expectedConfirmed.transferId(),
                            expectedConfirmed.paymentId(),
                            expectedConfirmed.correlationId(),
                            expectedConfirmed.chainId().value(),
                            expectedConfirmed.txHash(),
                            expectedConfirmed.blockNumber(),
                            expectedConfirmed.confirmations(),
                            expectedConfirmed.gasUsed(),
                            null
                    )));
        }
    }

    @Nested
    @DisplayName("RESUBMITTING transfers")
    class ResubmittingTransfers {

        @Test
        @DisplayName("should resubmit transfer when attempts below maximum")
        void shouldResubmitWhenAttemptsBelow() {
            // given
            var transfer = aResubmittingTransfer();
            var signResult = aResubmitSignResult();
            var signRequest = new SignRequest(
                    transfer.transferId(),
                    transfer.chainId(),
                    transfer.fromAddress(),
                    transfer.toWalletAddress(),
                    transfer.amount(),
                    transfer.stablecoin(),
                    transfer.nonce(),
                    null
            );

            given(chainTransferRepository.findByStatus(TransferStatus.SUBMITTED))
                    .willReturn(List.of());
            given(chainTransferRepository.findByStatus(TransferStatus.CONFIRMING))
                    .willReturn(List.of());
            given(chainTransferRepository.findByStatus(TransferStatus.RESUBMITTING))
                    .willReturn(List.of(transfer));
            given(custodyEngine.signAndSubmit(eqIgnoringTimestamps(signRequest)))
                    .willReturn(signResult);

            var expectedResubmitted = transfer.resubmit(RESUBMIT_TX_HASH);

            // when
            handler.monitorPendingTransfers();

            // then
            then(custodyEngine).should().signAndSubmit(eqIgnoringTimestamps(signRequest));
            then(chainTransferRepository).should().save(eqIgnoringTimestamps(expectedResubmitted));
            then(lifecycleEventRepository).should().save(
                    eqIgnoring(TransferLifecycleEvent.record(transfer.transferId(), "SUBMITTED"), "eventId"));
        }

        @Test
        @DisplayName("should fail transfer when max attempts exceeded")
        void shouldFailWhenMaxAttemptsExceeded() {
            // given
            var transfer = aMaxAttemptsResubmittingTransfer();

            given(chainTransferRepository.findByStatus(TransferStatus.SUBMITTED))
                    .willReturn(List.of());
            given(chainTransferRepository.findByStatus(TransferStatus.CONFIRMING))
                    .willReturn(List.of());
            given(chainTransferRepository.findByStatus(TransferStatus.RESUBMITTING))
                    .willReturn(List.of(transfer));

            var expectedFailed = transfer.fail(
                    "Max resubmission attempts (3) exceeded", "MAX_ATTEMPTS_EXCEEDED");

            // when
            handler.monitorPendingTransfers();

            // then
            then(chainTransferRepository).should().save(eqIgnoringTimestamps(expectedFailed));
            then(transferEventPublisher).should().publish(eqIgnoringTimestamps(
                    new TransferFailedEvent(
                            expectedFailed.transferId(),
                            expectedFailed.paymentId(),
                            expectedFailed.correlationId(),
                            expectedFailed.chainId().value(),
                            "Max resubmission attempts (3) exceeded",
                            "MAX_ATTEMPTS_EXCEEDED",
                            null
                    )));
            then(lifecycleEventRepository).should().save(
                    eqIgnoring(TransferLifecycleEvent.record(transfer.transferId(), "FAILED"), "eventId"));
        }
    }

    @Nested
    @DisplayName("No transfers")
    class NoTransfers {

        @Test
        @DisplayName("should do nothing when no in-flight transfers exist")
        void shouldDoNothingWhenNoTransfers() {
            // given
            given(chainTransferRepository.findByStatus(TransferStatus.SUBMITTED))
                    .willReturn(List.of());
            given(chainTransferRepository.findByStatus(TransferStatus.CONFIRMING))
                    .willReturn(List.of());
            given(chainTransferRepository.findByStatus(TransferStatus.RESUBMITTING))
                    .willReturn(List.of());

            // when
            handler.monitorPendingTransfers();

            // then
            then(chainRpcProvider).shouldHaveNoInteractions();
            then(custodyEngine).shouldHaveNoInteractions();
            then(transferEventPublisher).shouldHaveNoInteractions();
        }
    }
}
