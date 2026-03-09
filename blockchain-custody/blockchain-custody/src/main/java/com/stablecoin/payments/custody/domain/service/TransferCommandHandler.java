package com.stablecoin.payments.custody.domain.service;

import com.stablecoin.payments.custody.domain.event.TransferSubmittedEvent;
import com.stablecoin.payments.custody.domain.exception.CustodySigningException;
import com.stablecoin.payments.custody.domain.exception.InsufficientBalanceException;
import com.stablecoin.payments.custody.domain.exception.TransferNotFoundException;
import com.stablecoin.payments.custody.domain.exception.WalletNotFoundException;
import com.stablecoin.payments.custody.domain.model.ChainTransfer;
import com.stablecoin.payments.custody.domain.model.ParticipantType;
import com.stablecoin.payments.custody.domain.model.StablecoinTicker;
import com.stablecoin.payments.custody.domain.model.TransferLifecycleEvent;
import com.stablecoin.payments.custody.domain.model.TransferParticipant;
import com.stablecoin.payments.custody.domain.model.TransferResult;
import com.stablecoin.payments.custody.domain.model.TransferType;
import com.stablecoin.payments.custody.domain.model.WalletBalance;
import com.stablecoin.payments.custody.domain.model.WalletPurpose;
import com.stablecoin.payments.custody.domain.port.ChainTransferRepository;
import com.stablecoin.payments.custody.domain.port.CustodyEngine;
import com.stablecoin.payments.custody.domain.port.SignRequest;
import com.stablecoin.payments.custody.domain.port.TransferEventPublisher;
import com.stablecoin.payments.custody.domain.port.TransferLifecycleEventRepository;
import com.stablecoin.payments.custody.domain.port.TransferParticipantRepository;
import com.stablecoin.payments.custody.domain.port.WalletBalanceRepository;
import com.stablecoin.payments.custody.domain.port.WalletRepository;
import com.stablecoin.payments.custody.domain.service.ChainSelectionEngine.ChainSelectionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain command handler that orchestrates blockchain transfer submission.
 * <p>
 * Full flow: chain selection → balance reservation → nonce acquisition →
 * custody signing → persist + lifecycle events + participants + outbox event.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TransferCommandHandler {

    private final ChainTransferRepository chainTransferRepository;
    private final WalletRepository walletRepository;
    private final WalletBalanceRepository walletBalanceRepository;
    private final TransferParticipantRepository transferParticipantRepository;
    private final TransferLifecycleEventRepository lifecycleEventRepository;
    private final ChainSelectionEngine chainSelectionEngine;
    private final NonceManager nonceManager;
    private final CustodyEngine custodyEngine;
    private final TransferEventPublisher transferEventPublisher;

    /**
     * Initiates a new chain transfer.
     * <p>
     * Idempotent: if a transfer already exists for the given paymentId + transferType,
     * returns the existing transfer with {@code created=false}.
     */
    public TransferResult initiateTransfer(UUID paymentId, UUID correlationId,
                                           TransferType transferType, UUID parentTransferId,
                                           StablecoinTicker stablecoin, BigDecimal amount,
                                           String toWalletAddress, String preferredChain) {

        // 1. Idempotency check
        var existing = chainTransferRepository.findByPaymentIdAndType(paymentId, transferType);
        if (existing.isPresent()) {
            log.info("Idempotent replay for paymentId={} transferType={}", paymentId, transferType);
            return new TransferResult(existing.get(), false);
        }

        // 2. Select optimal chain
        var tempTransferId = UUID.randomUUID();
        var selectionResult = chainSelectionEngine.selectChain(
                new ChainSelectionRequest(tempTransferId, stablecoin, amount, preferredChain));
        var selectedChain = selectionResult.selectedChain();

        // 3. Find source wallet
        var walletPurpose = transferType == TransferType.RETURN ? WalletPurpose.OFF_RAMP : WalletPurpose.ON_RAMP;
        var wallet = walletRepository.findByChainIdAndPurpose(selectedChain, walletPurpose)
                .stream()
                .filter(w -> w.isActive())
                .findFirst()
                .orElseThrow(() -> new WalletNotFoundException(
                        "No active %s wallet found for chain %s".formatted(walletPurpose, selectedChain.value())));

        // 4. Reserve balance (with pessimistic lock)
        var balance = walletBalanceRepository.findByWalletIdAndStablecoinForUpdate(
                        wallet.walletId(), stablecoin)
                .orElseThrow(() -> new InsufficientBalanceException(
                        "No balance record found for wallet %s stablecoin %s"
                                .formatted(wallet.walletId(), stablecoin.ticker())));

        if (!balance.hasSufficientBalance(amount)) {
            throw new InsufficientBalanceException(
                    "Insufficient balance: available=%s, requested=%s for wallet %s"
                            .formatted(balance.availableBalance(), amount, wallet.walletId()));
        }

        var reservedBalance = balance.reserve(amount);
        walletBalanceRepository.save(reservedBalance);

        // 5. Create transfer aggregate in PENDING state
        var transfer = ChainTransfer.initiate(
                paymentId, correlationId, transferType, parentTransferId,
                stablecoin, amount, wallet.walletId(), toWalletAddress, wallet.address());

        // 6. Transition through states: PENDING → CHAIN_SELECTED → SIGNING
        transfer = transfer.selectChain(selectedChain);
        var nonceAssignment = nonceManager.assignNonce(wallet.walletId(), selectedChain, false);
        transfer = transfer.startSigning(nonceAssignment.nonce());

        // 7. Sign via custody engine
        var signRequest = new SignRequest(
                transfer.transferId(), selectedChain,
                wallet.address(), toWalletAddress, amount, stablecoin,
                nonceAssignment.nonce(), wallet.vaultAccountId());

        String txHash;
        try {
            var signResult = custodyEngine.signAndSubmit(signRequest);
            txHash = signResult.txHash();
        } catch (Exception e) {
            // Fail the transfer and release balance
            transfer = transfer.fail("Custody signing failed: " + e.getMessage(), CustodySigningException.ERROR_CODE);
            chainTransferRepository.save(transfer);
            releaseBalance(reservedBalance, amount);
            lifecycleEventRepository.save(
                    TransferLifecycleEvent.record(transfer.transferId(), "FAILED"));
            throw new CustodySigningException("Custody signing failed for transfer " + transfer.transferId(), e);
        }

        // 8. SIGNING → SUBMITTED
        transfer = transfer.submit(txHash);
        transfer = chainTransferRepository.save(transfer);

        // 9. Record participants
        transferParticipantRepository.save(TransferParticipant.create(
                transfer.transferId(), ParticipantType.INPUT,
                wallet.address(), wallet.walletId(), amount, stablecoin.ticker()));
        transferParticipantRepository.save(TransferParticipant.create(
                transfer.transferId(), ParticipantType.OUTPUT,
                toWalletAddress, null, amount, stablecoin.ticker()));

        // 10. Record lifecycle events
        lifecycleEventRepository.save(
                TransferLifecycleEvent.record(transfer.transferId(), "BALANCE_RESERVED"));
        lifecycleEventRepository.save(
                TransferLifecycleEvent.record(transfer.transferId(), "NONCE_ACQUIRED"));
        lifecycleEventRepository.save(
                TransferLifecycleEvent.record(transfer.transferId(), "SIGNED"));
        lifecycleEventRepository.save(
                TransferLifecycleEvent.record(transfer.transferId(), "SUBMITTED"));

        // 11. Publish outbox event
        transferEventPublisher.publish(new TransferSubmittedEvent(
                transfer.transferId(), transfer.paymentId(), transfer.correlationId(),
                selectedChain.value(), stablecoin.ticker(), amount,
                txHash, wallet.address(), toWalletAddress, Instant.now()));

        log.info("Transfer {} submitted: paymentId={}, chain={}, txHash={}",
                transfer.transferId(), paymentId, selectedChain.value(), txHash);

        return new TransferResult(transfer, true);
    }

    /**
     * Retrieves a transfer by ID.
     */
    @Transactional(readOnly = true)
    public ChainTransfer getTransfer(UUID transferId) {
        return chainTransferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(
                        "Transfer not found: " + transferId));
    }

    /**
     * Retrieves wallet balance details.
     */
    @Transactional(readOnly = true)
    public WalletBalanceDetails getWalletBalance(UUID walletId) {
        var wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(
                        "Wallet not found: " + walletId));
        var balances = walletBalanceRepository.findByWalletId(walletId);
        return new WalletBalanceDetails(wallet, balances);
    }

    private void releaseBalance(WalletBalance reservedBalance, BigDecimal amount) {
        try {
            var released = reservedBalance.release(amount);
            walletBalanceRepository.save(released);
        } catch (Exception e) {
            log.error("Failed to release balance for wallet {}: {}",
                    reservedBalance.walletId(), e.getMessage());
        }
    }

    /**
     * Wrapper for wallet + balance list used by the controller.
     */
    public record WalletBalanceDetails(
            com.stablecoin.payments.custody.domain.model.Wallet wallet,
            java.util.List<WalletBalance> balances
    ) {}
}
