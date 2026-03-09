package com.stablecoin.payments.custody.domain.service;

import com.stablecoin.payments.custody.domain.event.TransferConfirmedEvent;
import com.stablecoin.payments.custody.domain.event.TransferFailedEvent;
import com.stablecoin.payments.custody.domain.model.ChainTransfer;
import com.stablecoin.payments.custody.domain.model.TransferLifecycleEvent;
import com.stablecoin.payments.custody.domain.model.TransferStatus;
import com.stablecoin.payments.custody.domain.port.ChainConfirmationProperties;
import com.stablecoin.payments.custody.domain.port.ChainRpcProvider;
import com.stablecoin.payments.custody.domain.port.ChainTransferRepository;
import com.stablecoin.payments.custody.domain.port.CustodyEngine;
import com.stablecoin.payments.custody.domain.port.SignRequest;
import com.stablecoin.payments.custody.domain.port.TransferEventPublisher;
import com.stablecoin.payments.custody.domain.port.TransferLifecycleEventRepository;
import com.stablecoin.payments.custody.domain.port.TransferMonitorProperties;
import com.stablecoin.payments.custody.domain.port.WalletBalanceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;

/**
 * Domain service responsible for monitoring in-flight chain transfers.
 * <p>
 * Handles three categories of transfers:
 * <ul>
 *   <li><b>SUBMITTED</b> — checks for receipt; if found, transitions to CONFIRMING;
 *       if stuck beyond timeout, transitions to RESUBMITTING</li>
 *   <li><b>CONFIRMING</b> — checks if confirmations meet the chain's minimum;
 *       if so, confirms and releases reserved balance.
 *       If receipt disappears (reorg) beyond grace window, marks for resubmission.</li>
 *   <li><b>RESUBMITTING</b> — re-signs and resubmits; fails if max attempts exceeded.
 *       Uses claim-before-submit pattern for crash safety.</li>
 * </ul>
 * <p>
 * Each transfer is processed in its own transaction to prevent partial commits
 * and isolate failures between transfers.
 */
@Slf4j
@Service
public class TransferMonitorCommandHandler {

    private final ChainTransferRepository chainTransferRepository;
    private final WalletBalanceRepository walletBalanceRepository;
    private final TransferLifecycleEventRepository lifecycleEventRepository;
    private final ChainRpcProvider chainRpcProvider;
    private final CustodyEngine custodyEngine;
    private final TransferEventPublisher transferEventPublisher;
    private final TransferMonitorProperties transferMonitorProperties;
    private final ChainConfirmationProperties chainConfirmationProperties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public TransferMonitorCommandHandler(
            ChainTransferRepository chainTransferRepository,
            WalletBalanceRepository walletBalanceRepository,
            TransferLifecycleEventRepository lifecycleEventRepository,
            ChainRpcProvider chainRpcProvider,
            CustodyEngine custodyEngine,
            TransferEventPublisher transferEventPublisher,
            TransferMonitorProperties transferMonitorProperties,
            ChainConfirmationProperties chainConfirmationProperties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.chainTransferRepository = chainTransferRepository;
        this.walletBalanceRepository = walletBalanceRepository;
        this.lifecycleEventRepository = lifecycleEventRepository;
        this.chainRpcProvider = chainRpcProvider;
        this.custodyEngine = custodyEngine;
        this.transferEventPublisher = transferEventPublisher;
        this.transferMonitorProperties = transferMonitorProperties;
        this.chainConfirmationProperties = chainConfirmationProperties;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Polls all in-flight transfers and processes them according to their current status.
     * Each transfer is processed in its own transaction.
     */
    public void monitorPendingTransfers() {
        var submitted = chainTransferRepository.findByStatus(TransferStatus.SUBMITTED);
        var confirming = chainTransferRepository.findByStatus(TransferStatus.CONFIRMING);
        var resubmitting = chainTransferRepository.findByStatus(TransferStatus.RESUBMITTING);

        var allTransfers = new ArrayList<ChainTransfer>();
        allTransfers.addAll(submitted);
        allTransfers.addAll(confirming);
        allTransfers.addAll(resubmitting);

        if (allTransfers.isEmpty()) {
            log.debug("No in-flight transfers to monitor");
            return;
        }

        log.info("Monitoring {} in-flight transfers (submitted={}, confirming={}, resubmitting={})",
                allTransfers.size(), submitted.size(), confirming.size(), resubmitting.size());

        for (var transfer : allTransfers) {
            try {
                transactionTemplate.executeWithoutResult(status -> processTransfer(transfer));
            } catch (Exception e) {
                log.error("Error monitoring transfer transferId={}: {}",
                        transfer.transferId(), e.getMessage(), e);
            }
        }
    }

    private void processTransfer(ChainTransfer transfer) {
        switch (transfer.status()) {
            case SUBMITTED -> processSubmitted(transfer);
            case CONFIRMING -> processConfirming(transfer);
            case RESUBMITTING -> processResubmitting(transfer);
            default -> log.warn("Unexpected status {} for transfer {}", transfer.status(), transfer.transferId());
        }
    }

    private void processSubmitted(ChainTransfer transfer) {
        var receipt = chainRpcProvider.getTransactionReceipt(transfer.chainId(), transfer.txHash());

        if (receipt != null && receipt.txHash() != null) {
            if (!receipt.success()) {
                var failed = transfer.fail("Transaction reverted on-chain", "TX_REVERTED");
                chainTransferRepository.save(failed);
                lifecycleEventRepository.save(
                        TransferLifecycleEvent.record(failed.transferId(), "FAILED"));
                publishFailedEvent(failed);
                log.warn("Transfer {} failed — transaction reverted on-chain", transfer.transferId());
                return;
            }

            var confirming = transfer.startConfirming();
            chainTransferRepository.save(confirming);
            lifecycleEventRepository.save(
                    TransferLifecycleEvent.record(confirming.transferId(), "CONFIRMING"));
            log.info("Transfer {} moved to CONFIRMING (receipt found at block {})",
                    transfer.transferId(), receipt.blockNumber());

            checkConfirmations(confirming, receipt.blockNumber(), receipt.gasUsed(), receipt.effectiveGasPrice());
            return;
        }

        // No receipt yet — check if stuck
        var stuckThreshold = Instant.now(clock).minusSeconds(transferMonitorProperties.resubmitTimeoutS());
        if (stuckThreshold.isAfter(transfer.updatedAt())) {
            var resubmitting = transfer.markForResubmission();
            chainTransferRepository.save(resubmitting);
            lifecycleEventRepository.save(
                    TransferLifecycleEvent.record(resubmitting.transferId(), "RESUBMITTING"));
            log.warn("Transfer {} stuck for >{}s — marked for resubmission",
                    transfer.transferId(), transferMonitorProperties.resubmitTimeoutS());
        }
    }

    private void processConfirming(ChainTransfer transfer) {
        var receipt = chainRpcProvider.getTransactionReceipt(transfer.chainId(), transfer.txHash());

        if (receipt == null || receipt.txHash() == null) {
            // Check if stuck beyond grace window — possible chain reorg
            var graceThreshold = Instant.now(clock).minusSeconds(transferMonitorProperties.confirmingTimeoutS());
            if (graceThreshold.isAfter(transfer.updatedAt())) {
                var resubmitting = transfer.markForResubmission();
                chainTransferRepository.save(resubmitting);
                lifecycleEventRepository.save(
                        TransferLifecycleEvent.record(resubmitting.transferId(), "RESUBMITTING"));
                log.warn("Transfer {} receipt disappeared for >{}s — marking for resubmission (possible reorg)",
                        transfer.transferId(), transferMonitorProperties.confirmingTimeoutS());
            } else {
                log.warn("Receipt disappeared for CONFIRMING transfer {} — possible reorg, waiting for grace window",
                        transfer.transferId());
            }
            return;
        }

        checkConfirmations(transfer, receipt.blockNumber(), receipt.gasUsed(), receipt.effectiveGasPrice());
    }

    private void checkConfirmations(ChainTransfer transfer, long receiptBlockNumber,
                                    BigDecimal gasUsed, BigDecimal effectiveGasPrice) {
        var latestBlock = chainRpcProvider.getLatestBlockNumber(transfer.chainId());
        var currentConfirmations = (int) (latestBlock - receiptBlockNumber);
        var minConfirmations = chainConfirmationProperties.getMinConfirmations(transfer.chainId().value());

        if (currentConfirmations >= minConfirmations) {
            var confirmed = transfer.confirm(receiptBlockNumber, currentConfirmations, gasUsed, effectiveGasPrice);
            chainTransferRepository.save(confirmed);
            lifecycleEventRepository.save(
                    TransferLifecycleEvent.record(confirmed.transferId(), "CONFIRMED"));

            confirmDebitBalance(confirmed);
            publishConfirmedEvent(confirmed);

            log.info("Transfer {} CONFIRMED at block {} with {} confirmations",
                    transfer.transferId(), receiptBlockNumber, currentConfirmations);
        } else {
            log.debug("Transfer {} has {} of {} required confirmations",
                    transfer.transferId(), currentConfirmations, minConfirmations);
        }
    }

    private void processResubmitting(ChainTransfer transfer) {
        if (transfer.attemptCount() >= transferMonitorProperties.maxAttempts()) {
            var failed = transfer.fail(
                    "Max resubmission attempts (%d) exceeded".formatted(transferMonitorProperties.maxAttempts()),
                    "MAX_ATTEMPTS_EXCEEDED");
            chainTransferRepository.save(failed);
            lifecycleEventRepository.save(
                    TransferLifecycleEvent.record(failed.transferId(), "FAILED"));
            publishFailedEvent(failed);
            log.error("Transfer {} FAILED — max attempts ({}) exceeded",
                    transfer.transferId(), transferMonitorProperties.maxAttempts());
            return;
        }

        // Step 1: Claim resubmission (increment attempt count) and persist BEFORE calling custody.
        // If crash occurs after custody call but before final persist, next poll will see
        // the incremented attempt count and re-attempt with the same nonce (idempotent on-chain).
        var claimed = transfer.claimResubmission();
        chainTransferRepository.save(claimed);
        lifecycleEventRepository.save(
                TransferLifecycleEvent.record(claimed.transferId(), "RESUBMISSION_CLAIMED"));

        // Step 2: Call custody engine
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
        var signResult = custodyEngine.signAndSubmit(signRequest);

        // Step 3: Complete resubmission with actual tx hash
        var resubmitted = claimed.confirmResubmission(signResult.txHash());
        chainTransferRepository.save(resubmitted);
        lifecycleEventRepository.save(
                TransferLifecycleEvent.record(resubmitted.transferId(), "SUBMITTED"));
        log.info("Transfer {} resubmitted with new txHash={} (attempt {})",
                transfer.transferId(), signResult.txHash(), resubmitted.attemptCount());
    }

    private void confirmDebitBalance(ChainTransfer transfer) {
        var balanceOpt = walletBalanceRepository.findByWalletIdAndStablecoinForUpdate(
                transfer.fromWalletId(), transfer.stablecoin());

        balanceOpt.ifPresent(balance -> {
            var updated = balance.confirmDebit(transfer.amount());
            walletBalanceRepository.save(updated);
            log.info("Confirmed debit of {} {} from wallet {}",
                    transfer.amount(), transfer.stablecoin().ticker(), transfer.fromWalletId());
        });
    }

    private void publishConfirmedEvent(ChainTransfer transfer) {
        var event = new TransferConfirmedEvent(
                transfer.transferId(),
                transfer.paymentId(),
                transfer.correlationId(),
                transfer.chainId().value(),
                transfer.txHash(),
                transfer.blockNumber(),
                transfer.confirmations(),
                transfer.gasUsed(),
                transfer.blockConfirmedAt()
        );
        transferEventPublisher.publish(event);
    }

    private void publishFailedEvent(ChainTransfer transfer) {
        var event = new TransferFailedEvent(
                transfer.transferId(),
                transfer.paymentId(),
                transfer.correlationId(),
                transfer.chainId() != null ? transfer.chainId().value() : null,
                transfer.failureReason(),
                transfer.errorCode(),
                Instant.now(clock)
        );
        transferEventPublisher.publish(event);
    }
}
