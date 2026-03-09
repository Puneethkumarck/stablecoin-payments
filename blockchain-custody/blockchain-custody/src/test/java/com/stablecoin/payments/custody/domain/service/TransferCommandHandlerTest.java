package com.stablecoin.payments.custody.domain.service;

import com.stablecoin.payments.custody.domain.event.TransferSubmittedEvent;
import com.stablecoin.payments.custody.domain.exception.CustodySigningException;
import com.stablecoin.payments.custody.domain.exception.InsufficientBalanceException;
import com.stablecoin.payments.custody.domain.exception.TransferNotFoundException;
import com.stablecoin.payments.custody.domain.exception.WalletNotFoundException;
import com.stablecoin.payments.custody.domain.model.ChainCandidate;
import com.stablecoin.payments.custody.domain.model.ChainSelectionResult;
import com.stablecoin.payments.custody.domain.model.ChainTransfer;
import com.stablecoin.payments.custody.domain.model.NonceAssignment;
import com.stablecoin.payments.custody.domain.model.ParticipantType;
import com.stablecoin.payments.custody.domain.model.TransferLifecycleEvent;
import com.stablecoin.payments.custody.domain.model.TransferParticipant;
import com.stablecoin.payments.custody.domain.model.TransferResult;
import com.stablecoin.payments.custody.domain.model.TransferStatus;
import com.stablecoin.payments.custody.domain.model.TransferType;
import com.stablecoin.payments.custody.domain.model.WalletPurpose;
import com.stablecoin.payments.custody.domain.port.ChainTransferRepository;
import com.stablecoin.payments.custody.domain.port.CustodyEngine;
import com.stablecoin.payments.custody.domain.port.SignRequest;
import com.stablecoin.payments.custody.domain.port.SignResult;
import com.stablecoin.payments.custody.domain.port.TransferEventPublisher;
import com.stablecoin.payments.custody.domain.port.TransferLifecycleEventRepository;
import com.stablecoin.payments.custody.domain.port.TransferParticipantRepository;
import com.stablecoin.payments.custody.domain.port.WalletBalanceRepository;
import com.stablecoin.payments.custody.domain.port.WalletRepository;
import com.stablecoin.payments.custody.domain.service.ChainSelectionEngine.ChainSelectionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.AMOUNT;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.CHAIN_BASE;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.CORRELATION_ID;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.PAYMENT_ID;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.TO_ADDRESS;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.TX_HASH;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.USDC;
import static com.stablecoin.payments.custody.fixtures.ChainTransferFixtures.aSubmittedTransfer;
import static com.stablecoin.payments.custody.fixtures.TestUtils.eqIgnoring;
import static com.stablecoin.payments.custody.fixtures.TestUtils.eqIgnoringTimestamps;
import static com.stablecoin.payments.custody.fixtures.WalletBalanceFixtures.aBalanceWith;
import static com.stablecoin.payments.custody.fixtures.WalletFixtures.anActiveWallet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class TransferCommandHandlerTest {

    @Mock
    private ChainTransferRepository chainTransferRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletBalanceRepository walletBalanceRepository;
    @Mock
    private TransferParticipantRepository transferParticipantRepository;
    @Mock
    private TransferLifecycleEventRepository lifecycleEventRepository;
    @Mock
    private ChainSelectionEngine chainSelectionEngine;
    @Mock
    private NonceManager nonceManager;
    @Mock
    private CustodyEngine custodyEngine;
    @Mock
    private TransferEventPublisher transferEventPublisher;

    @InjectMocks
    private TransferCommandHandler handler;

    private static final List<ChainCandidate> BASE_CANDIDATES = List.of(
            ChainCandidate.builder()
                    .chainId(CHAIN_BASE).feeUsd(0.01).finalitySeconds(12)
                    .healthScore(1.0).score(50.0).selected(true).build());

    private ChainSelectionResult aSelectionResult() {
        return ChainSelectionResult.builder()
                .selectedChain(CHAIN_BASE)
                .candidates(BASE_CANDIDATES)
                .transferId(UUID.randomUUID())
                .build();
    }

    @Nested
    @DisplayName("initiateTransfer")
    class InitiateTransfer {

        @Test
        @DisplayName("should initiate forward transfer through full pipeline")
        void shouldInitiateForwardTransfer() {
            var wallet = anActiveWallet();
            var balance = aBalanceWith(new BigDecimal("5000.00"), BigDecimal.ZERO);
            var signResult = new SignResult(TX_HASH, "custody-tx-001");
            var nonceAssignment = NonceAssignment.incremented(42L);

            given(chainTransferRepository.findByPaymentIdAndType(PAYMENT_ID, TransferType.FORWARD))
                    .willReturn(Optional.empty());
            given(chainSelectionEngine.selectChain(eqIgnoring(
                    new ChainSelectionRequest(UUID.randomUUID(), USDC, AMOUNT, "base"), "transferId")))
                    .willReturn(aSelectionResult());
            given(walletRepository.findByChainIdAndPurpose(CHAIN_BASE, WalletPurpose.ON_RAMP))
                    .willReturn(List.of(wallet));
            given(walletBalanceRepository.findByWalletIdAndStablecoinForUpdate(wallet.walletId(), USDC))
                    .willReturn(Optional.of(balance));
            given(nonceManager.assignNonce(wallet.walletId(), CHAIN_BASE, false))
                    .willReturn(nonceAssignment);
            given(custodyEngine.signAndSubmit(eqIgnoring(
                    new SignRequest(UUID.randomUUID(), CHAIN_BASE, wallet.address(), TO_ADDRESS,
                            AMOUNT, USDC, 42L, wallet.vaultAccountId()), "transferId")))
                    .willReturn(signResult);
            given(chainTransferRepository.save(eqIgnoring(
                    ChainTransfer.initiate(PAYMENT_ID, CORRELATION_ID, TransferType.FORWARD, null,
                                    USDC, AMOUNT, wallet.walletId(), TO_ADDRESS, wallet.address())
                            .selectChain(CHAIN_BASE).startSigning(42L).submit(TX_HASH),
                    "transferId")))
                    .willAnswer(inv -> inv.getArgument(0));

            var result = handler.initiateTransfer(
                    PAYMENT_ID, CORRELATION_ID, TransferType.FORWARD, null,
                    USDC, AMOUNT, TO_ADDRESS, "base");

            assertThat(result.created()).isTrue();
            assertThat(result.transfer().status()).isEqualTo(TransferStatus.SUBMITTED);

            then(walletBalanceRepository).should().save(eqIgnoringTimestamps(balance.reserve(AMOUNT)));
            then(transferEventPublisher).should().publish(eqIgnoring(
                    new TransferSubmittedEvent(
                            UUID.randomUUID(), PAYMENT_ID, CORRELATION_ID,
                            "base", "USDC", AMOUNT, TX_HASH,
                            wallet.address(), TO_ADDRESS, null),
                    "transferId"));
        }

        @Test
        @DisplayName("should return existing transfer on idempotent replay")
        void shouldReturnExistingOnIdempotentReplay() {
            var existingTransfer = aSubmittedTransfer();

            given(chainTransferRepository.findByPaymentIdAndType(PAYMENT_ID, TransferType.FORWARD))
                    .willReturn(Optional.of(existingTransfer));

            var result = handler.initiateTransfer(
                    PAYMENT_ID, CORRELATION_ID, TransferType.FORWARD, null,
                    USDC, AMOUNT, TO_ADDRESS, "base");

            var expected = new TransferResult(existingTransfer, false);
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);

            then(chainSelectionEngine).should(never())
                    .selectChain(eqIgnoring(
                            new ChainSelectionRequest(UUID.randomUUID(), USDC, AMOUNT, "base"), "transferId"));
        }

        @Test
        @DisplayName("should throw InsufficientBalanceException when balance too low")
        void shouldThrowWhenInsufficientBalance() {
            var wallet = anActiveWallet();
            var lowBalance = aBalanceWith(new BigDecimal("100.00"), BigDecimal.ZERO);

            given(chainTransferRepository.findByPaymentIdAndType(PAYMENT_ID, TransferType.FORWARD))
                    .willReturn(Optional.empty());
            given(chainSelectionEngine.selectChain(eqIgnoring(
                    new ChainSelectionRequest(UUID.randomUUID(), USDC, AMOUNT, null), "transferId")))
                    .willReturn(aSelectionResult());
            given(walletRepository.findByChainIdAndPurpose(CHAIN_BASE, WalletPurpose.ON_RAMP))
                    .willReturn(List.of(wallet));
            given(walletBalanceRepository.findByWalletIdAndStablecoinForUpdate(wallet.walletId(), USDC))
                    .willReturn(Optional.of(lowBalance));

            assertThatThrownBy(() -> handler.initiateTransfer(
                    PAYMENT_ID, CORRELATION_ID, TransferType.FORWARD, null,
                    USDC, AMOUNT, TO_ADDRESS, null))
                    .isInstanceOf(InsufficientBalanceException.class);
        }

        @Test
        @DisplayName("should throw WalletNotFoundException when no active wallet found")
        void shouldThrowWhenNoActiveWallet() {
            given(chainTransferRepository.findByPaymentIdAndType(PAYMENT_ID, TransferType.FORWARD))
                    .willReturn(Optional.empty());
            given(chainSelectionEngine.selectChain(eqIgnoring(
                    new ChainSelectionRequest(UUID.randomUUID(), USDC, AMOUNT, null), "transferId")))
                    .willReturn(aSelectionResult());
            given(walletRepository.findByChainIdAndPurpose(CHAIN_BASE, WalletPurpose.ON_RAMP))
                    .willReturn(List.of());

            assertThatThrownBy(() -> handler.initiateTransfer(
                    PAYMENT_ID, CORRELATION_ID, TransferType.FORWARD, null,
                    USDC, AMOUNT, TO_ADDRESS, null))
                    .isInstanceOf(WalletNotFoundException.class);
        }

        @Test
        @DisplayName("should fail transfer and release balance on custody signing failure")
        void shouldFailTransferOnCustodySigningFailure() {
            var wallet = anActiveWallet();
            var balance = aBalanceWith(new BigDecimal("5000.00"), BigDecimal.ZERO);
            var nonceAssignment = NonceAssignment.incremented(42L);

            given(chainTransferRepository.findByPaymentIdAndType(PAYMENT_ID, TransferType.FORWARD))
                    .willReturn(Optional.empty());
            given(chainSelectionEngine.selectChain(eqIgnoring(
                    new ChainSelectionRequest(UUID.randomUUID(), USDC, AMOUNT, null), "transferId")))
                    .willReturn(aSelectionResult());
            given(walletRepository.findByChainIdAndPurpose(CHAIN_BASE, WalletPurpose.ON_RAMP))
                    .willReturn(List.of(wallet));
            given(walletBalanceRepository.findByWalletIdAndStablecoinForUpdate(wallet.walletId(), USDC))
                    .willReturn(Optional.of(balance));
            given(nonceManager.assignNonce(wallet.walletId(), CHAIN_BASE, false))
                    .willReturn(nonceAssignment);
            given(custodyEngine.signAndSubmit(eqIgnoring(
                    new SignRequest(UUID.randomUUID(), CHAIN_BASE, wallet.address(), TO_ADDRESS,
                            AMOUNT, USDC, 42L, wallet.vaultAccountId()), "transferId")))
                    .willThrow(new RuntimeException("MPC signing timeout"));
            // Stub the save for the FAILED transfer (handler saves failed state in catch block)
            given(chainTransferRepository.save(eqIgnoring(
                    ChainTransfer.initiate(PAYMENT_ID, CORRELATION_ID, TransferType.FORWARD, null,
                                    USDC, AMOUNT, wallet.walletId(), TO_ADDRESS, wallet.address())
                            .selectChain(CHAIN_BASE).startSigning(42L)
                            .fail("Custody signing failed: MPC signing timeout", "BC-1004"),
                    "transferId")))
                    .willAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> handler.initiateTransfer(
                    PAYMENT_ID, CORRELATION_ID, TransferType.FORWARD, null,
                    USDC, AMOUNT, TO_ADDRESS, null))
                    .isInstanceOf(CustodySigningException.class);

            // Verify balance was released back
            then(walletBalanceRepository).should().save(eqIgnoringTimestamps(balance));
        }

        @Test
        @DisplayName("should record INPUT and OUTPUT participants")
        void shouldRecordParticipants() {
            var wallet = anActiveWallet();
            var balance = aBalanceWith(new BigDecimal("5000.00"), BigDecimal.ZERO);
            var signResult = new SignResult(TX_HASH, "custody-tx-001");
            var nonceAssignment = NonceAssignment.incremented(42L);

            given(chainTransferRepository.findByPaymentIdAndType(PAYMENT_ID, TransferType.FORWARD))
                    .willReturn(Optional.empty());
            given(chainSelectionEngine.selectChain(eqIgnoring(
                    new ChainSelectionRequest(UUID.randomUUID(), USDC, AMOUNT, null), "transferId")))
                    .willReturn(aSelectionResult());
            given(walletRepository.findByChainIdAndPurpose(CHAIN_BASE, WalletPurpose.ON_RAMP))
                    .willReturn(List.of(wallet));
            given(walletBalanceRepository.findByWalletIdAndStablecoinForUpdate(wallet.walletId(), USDC))
                    .willReturn(Optional.of(balance));
            given(nonceManager.assignNonce(wallet.walletId(), CHAIN_BASE, false))
                    .willReturn(nonceAssignment);
            given(custodyEngine.signAndSubmit(eqIgnoring(
                    new SignRequest(UUID.randomUUID(), CHAIN_BASE, wallet.address(), TO_ADDRESS,
                            AMOUNT, USDC, 42L, wallet.vaultAccountId()), "transferId")))
                    .willReturn(signResult);
            given(chainTransferRepository.save(eqIgnoring(
                    ChainTransfer.initiate(PAYMENT_ID, CORRELATION_ID, TransferType.FORWARD, null,
                                    USDC, AMOUNT, wallet.walletId(), TO_ADDRESS, wallet.address())
                            .selectChain(CHAIN_BASE).startSigning(42L).submit(TX_HASH),
                    "transferId")))
                    .willAnswer(inv -> inv.getArgument(0));

            handler.initiateTransfer(
                    PAYMENT_ID, CORRELATION_ID, TransferType.FORWARD, null,
                    USDC, AMOUNT, TO_ADDRESS, null);

            then(transferParticipantRepository).should().save(eqIgnoring(
                    TransferParticipant.create(UUID.randomUUID(), ParticipantType.INPUT,
                            wallet.address(), wallet.walletId(), AMOUNT, "USDC"),
                    "participantId", "transferId"));
            then(transferParticipantRepository).should().save(eqIgnoring(
                    TransferParticipant.create(UUID.randomUUID(), ParticipantType.OUTPUT,
                            TO_ADDRESS, null, AMOUNT, "USDC"),
                    "participantId", "transferId"));
        }

        @Test
        @DisplayName("should record lifecycle events")
        void shouldRecordLifecycleEvents() {
            var wallet = anActiveWallet();
            var balance = aBalanceWith(new BigDecimal("5000.00"), BigDecimal.ZERO);
            var signResult = new SignResult(TX_HASH, "custody-tx-001");
            var nonceAssignment = NonceAssignment.incremented(42L);

            given(chainTransferRepository.findByPaymentIdAndType(PAYMENT_ID, TransferType.FORWARD))
                    .willReturn(Optional.empty());
            given(chainSelectionEngine.selectChain(eqIgnoring(
                    new ChainSelectionRequest(UUID.randomUUID(), USDC, AMOUNT, null), "transferId")))
                    .willReturn(aSelectionResult());
            given(walletRepository.findByChainIdAndPurpose(CHAIN_BASE, WalletPurpose.ON_RAMP))
                    .willReturn(List.of(wallet));
            given(walletBalanceRepository.findByWalletIdAndStablecoinForUpdate(wallet.walletId(), USDC))
                    .willReturn(Optional.of(balance));
            given(nonceManager.assignNonce(wallet.walletId(), CHAIN_BASE, false))
                    .willReturn(nonceAssignment);
            given(custodyEngine.signAndSubmit(eqIgnoring(
                    new SignRequest(UUID.randomUUID(), CHAIN_BASE, wallet.address(), TO_ADDRESS,
                            AMOUNT, USDC, 42L, wallet.vaultAccountId()), "transferId")))
                    .willReturn(signResult);
            given(chainTransferRepository.save(eqIgnoring(
                    ChainTransfer.initiate(PAYMENT_ID, CORRELATION_ID, TransferType.FORWARD, null,
                                    USDC, AMOUNT, wallet.walletId(), TO_ADDRESS, wallet.address())
                            .selectChain(CHAIN_BASE).startSigning(42L).submit(TX_HASH),
                    "transferId")))
                    .willAnswer(inv -> inv.getArgument(0));

            handler.initiateTransfer(
                    PAYMENT_ID, CORRELATION_ID, TransferType.FORWARD, null,
                    USDC, AMOUNT, TO_ADDRESS, null);

            then(lifecycleEventRepository).should().save(eqIgnoring(
                    TransferLifecycleEvent.record(UUID.randomUUID(), "BALANCE_RESERVED"),
                    "eventId", "transferId"));
            then(lifecycleEventRepository).should().save(eqIgnoring(
                    TransferLifecycleEvent.record(UUID.randomUUID(), "NONCE_ACQUIRED"),
                    "eventId", "transferId"));
            then(lifecycleEventRepository).should().save(eqIgnoring(
                    TransferLifecycleEvent.record(UUID.randomUUID(), "SIGNED"),
                    "eventId", "transferId"));
            then(lifecycleEventRepository).should().save(eqIgnoring(
                    TransferLifecycleEvent.record(UUID.randomUUID(), "SUBMITTED"),
                    "eventId", "transferId"));
        }
    }

    @Nested
    @DisplayName("getTransfer")
    class GetTransfer {

        @Test
        @DisplayName("should return transfer when found")
        void shouldReturnTransferWhenFound() {
            var transfer = aSubmittedTransfer();
            given(chainTransferRepository.findById(transfer.transferId()))
                    .willReturn(Optional.of(transfer));

            var result = handler.getTransfer(transfer.transferId());

            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(transfer);
        }

        @Test
        @DisplayName("should throw TransferNotFoundException when not found")
        void shouldThrowWhenTransferNotFound() {
            var transferId = UUID.randomUUID();
            given(chainTransferRepository.findById(transferId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> handler.getTransfer(transferId))
                    .isInstanceOf(TransferNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getWalletBalance")
    class GetWalletBalance {

        @Test
        @DisplayName("should return wallet balance details when found")
        void shouldReturnWalletBalanceWhenFound() {
            var wallet = anActiveWallet();
            var balance = aBalanceWith(new BigDecimal("5000.00"), new BigDecimal("1000.00"));

            given(walletRepository.findById(wallet.walletId()))
                    .willReturn(Optional.of(wallet));
            given(walletBalanceRepository.findByWalletId(wallet.walletId()))
                    .willReturn(List.of(balance));

            var result = handler.getWalletBalance(wallet.walletId());

            assertThat(result.wallet())
                    .usingRecursiveComparison()
                    .isEqualTo(wallet);
            assertThat(result.balances())
                    .hasSize(1);
        }

        @Test
        @DisplayName("should throw WalletNotFoundException when not found")
        void shouldThrowWhenWalletNotFound() {
            var walletId = UUID.randomUUID();
            given(walletRepository.findById(walletId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> handler.getWalletBalance(walletId))
                    .isInstanceOf(WalletNotFoundException.class);
        }
    }
}
