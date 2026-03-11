package com.stablecoin.payments.ledger.infrastructure.messaging;

import com.stablecoin.payments.ledger.domain.model.EntryType;
import com.stablecoin.payments.ledger.domain.model.ReconciliationLeg;
import com.stablecoin.payments.ledger.domain.model.ReconciliationLegType;
import com.stablecoin.payments.ledger.domain.port.LedgerTransactionRepository;
import com.stablecoin.payments.ledger.domain.service.AccountingRules;
import com.stablecoin.payments.ledger.domain.service.JournalCommandHandler;
import com.stablecoin.payments.ledger.domain.service.JournalEntryRequest;
import com.stablecoin.payments.ledger.domain.service.ReconciliationCommandHandler;
import com.stablecoin.payments.ledger.domain.service.TransactionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Kafka consumers for all 9 payment lifecycle events.
 * Each listener parses the event, delegates to {@link JournalCommandHandler}
 * for journal entry creation and {@link ReconciliationCommandHandler}
 * for reconciliation leg tracking.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerEventConsumer {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final int BPS_DIVISOR = 10_000;
    private static final int FEE_SCALE = 8;

    private final JournalCommandHandler journalCommandHandler;
    private final ReconciliationCommandHandler reconciliationCommandHandler;
    private final LedgerTransactionRepository transactionRepository;

    // --- payment.initiated ---

    @KafkaListener(topics = "payment.initiated", groupId = "ledger-payment")
    @Transactional
    public void onPaymentInitiated(String message) {
        var event = parseEvent(message, PaymentInitiatedEvent.class);
        log.info("[LEDGER-EVENT] Processing payment.initiated paymentId={}", event.paymentId());

        var sourceEventId = deriveSourceEventId(event.paymentId(), "payment.initiated");
        var request = AccountingRules.paymentInitiated(
                event.paymentId(), event.correlationId(), sourceEventId,
                event.sourceAmount().amount(), event.sourceAmount().currency());
        journalCommandHandler.postTransaction(request);

        reconciliationCommandHandler.createRecord(event.paymentId());
    }

    // --- fx.rate.locked ---

    @KafkaListener(topics = "fx.rate.locked", groupId = "ledger-fx")
    @Transactional
    public void onFxRateLocked(String message) {
        var event = parseEvent(message, FxRateLockedEvent.class);
        log.info("[LEDGER-EVENT] Processing fx.rate.locked paymentId={} lockId={}",
                event.paymentId(), event.lockId());

        // No journal entries — FX rate is metadata for reconciliation
        var feeAmount = calculateFee(event.sourceAmount(), event.feeBps());
        reconciliationCommandHandler.recordLeg(
                event.paymentId(), ReconciliationLegType.FX_RATE,
                feeAmount, event.fromCurrency(), event.lockId());
    }

    // --- fiat.collected ---

    @KafkaListener(topics = "fiat.collected", groupId = "ledger-onramp")
    @Transactional
    public void onFiatCollected(String message) {
        var event = parseEvent(message, FiatCollectedEvent.class);
        log.info("[LEDGER-EVENT] Processing fiat.collected paymentId={}", event.paymentId());

        var request = AccountingRules.fiatCollected(
                event.paymentId(), event.paymentId(), event.eventId(),
                event.settledAmount(), event.currency());
        journalCommandHandler.postTransaction(request);

        reconciliationCommandHandler.recordLeg(
                event.paymentId(), ReconciliationLegType.FIAT_IN,
                event.settledAmount(), event.currency(), event.eventId());
    }

    // --- chain.transfer.submitted ---

    @KafkaListener(topics = "chain.transfer.submitted", groupId = "ledger-chain-submit")
    @Transactional
    public void onChainTransferSubmitted(String message) {
        var event = parseEvent(message, ChainTransferSubmittedEvent.class);
        log.info("[LEDGER-EVENT] Processing chain.transfer.submitted paymentId={}", event.paymentId());

        var amount = new BigDecimal(event.amount());
        var request = AccountingRules.chainTransferSubmitted(
                event.paymentId(), event.correlationId(), event.eventId(),
                amount, event.stablecoin());
        journalCommandHandler.postTransaction(request);

        reconciliationCommandHandler.recordLeg(
                event.paymentId(), ReconciliationLegType.STABLECOIN_MINTED,
                amount, event.stablecoin(), event.eventId());
    }

    // --- chain.transfer.confirmed ---

    @KafkaListener(topics = "chain.transfer.confirmed", groupId = "ledger-chain-confirm")
    @Transactional
    public void onChainTransferConfirmed(String message) {
        var event = parseEvent(message, ChainTransferConfirmedEvent.class);
        log.info("[LEDGER-EVENT] Processing chain.transfer.confirmed paymentId={}", event.paymentId());

        // Confirmed event lacks amount — look up from prior chain.transfer.submitted entry
        var submittedEntry = transactionRepository.findByPaymentId(event.paymentId()).stream()
                .filter(t -> "chain.transfer.submitted".equals(t.sourceEvent()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No chain.transfer.submitted found for paymentId=" + event.paymentId()))
                .entries().stream()
                .filter(e -> e.entryType() == EntryType.DEBIT)
                .findFirst()
                .orElseThrow();

        var request = AccountingRules.chainTransferConfirmed(
                event.paymentId(), event.correlationId(), event.eventId(),
                submittedEntry.amount(), submittedEntry.currency());
        journalCommandHandler.postTransaction(request);

        reconciliationCommandHandler.recordLeg(
                event.paymentId(), ReconciliationLegType.CHAIN_TRANSFERRED,
                submittedEntry.amount(), submittedEntry.currency(), event.eventId());
    }

    // --- stablecoin.redeemed ---

    @KafkaListener(topics = "stablecoin.redeemed", groupId = "ledger-redeem")
    @Transactional
    public void onStablecoinRedeemed(String message) {
        var event = parseEvent(message, StablecoinRedeemedEvent.class);
        log.info("[LEDGER-EVENT] Processing stablecoin.redeemed paymentId={}", event.paymentId());

        var request = AccountingRules.stablecoinRedeemed(
                event.paymentId(), event.correlationId(), event.eventId(),
                event.redeemedAmount(), event.stablecoin());
        journalCommandHandler.postTransaction(request);

        reconciliationCommandHandler.recordLeg(
                event.paymentId(), ReconciliationLegType.STABLECOIN_REDEEMED,
                event.redeemedAmount(), event.stablecoin(), event.eventId());
    }

    // --- fiat.payout.completed ---

    @KafkaListener(topics = "fiat.payout.completed", groupId = "ledger-offramp")
    @Transactional
    public void onFiatPayoutCompleted(String message) {
        var event = parseEvent(message, FiatPayoutCompletedEvent.class);
        log.info("[LEDGER-EVENT] Processing fiat.payout.completed paymentId={}", event.paymentId());

        var request = AccountingRules.fiatPayoutCompleted(
                event.paymentId(), event.correlationId(), event.eventId(),
                event.fiatAmount(), event.targetCurrency());
        journalCommandHandler.postTransaction(request);

        reconciliationCommandHandler.recordLeg(
                event.paymentId(), ReconciliationLegType.FIAT_OUT,
                event.fiatAmount(), event.targetCurrency(), event.eventId());
    }

    // --- payment.completed ---

    @KafkaListener(topics = "payment.completed", groupId = "ledger-complete")
    @Transactional
    public void onPaymentCompleted(String message) {
        var event = parseEvent(message, PaymentCompletedEvent.class);
        log.info("[LEDGER-EVENT] Processing payment.completed paymentId={}", event.paymentId());

        // Clearing: close in-transit clearing (stablecoin amount from prior chain.transfer.submitted)
        var submittedEntry = transactionRepository.findByPaymentId(event.paymentId()).stream()
                .filter(t -> "chain.transfer.submitted".equals(t.sourceEvent()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No chain.transfer.submitted found for paymentId=" + event.paymentId()))
                .entries().stream()
                .filter(e -> e.entryType() == EntryType.DEBIT)
                .findFirst()
                .orElseThrow();

        var clearingEventId = deriveSourceEventId(event.paymentId(), "payment.completed.clearing");
        journalCommandHandler.postTransaction(AccountingRules.paymentCompletedClearing(
                event.paymentId(), event.correlationId(), clearingEventId,
                submittedEntry.amount(), submittedEntry.currency()));

        // Revenue: recognize FX spread (fee stored in FX_RATE reconciliation leg)
        reconciliationCommandHandler.findLeg(event.paymentId(), ReconciliationLegType.FX_RATE)
                .map(ReconciliationLeg::amount)
                .filter(fee -> fee != null && fee.compareTo(BigDecimal.ZERO) > 0)
                .ifPresent(feeAmount -> {
                    var fxLeg = reconciliationCommandHandler
                            .findLeg(event.paymentId(), ReconciliationLegType.FX_RATE)
                            .orElseThrow();
                    var revenueEventId = deriveSourceEventId(event.paymentId(), "payment.completed.revenue");
                    journalCommandHandler.postTransaction(AccountingRules.paymentCompletedRevenue(
                            event.paymentId(), event.correlationId(), revenueEventId,
                            feeAmount, fxLeg.currency()));
                });

        // Finalize reconciliation — checks all 5 legs present and amounts within tolerance
        reconciliationCommandHandler.finalizeReconciliation(event.paymentId());
    }

    // --- payment.failed ---

    @KafkaListener(topics = "payment.failed", groupId = "ledger-failed")
    @Transactional
    public void onPaymentFailed(String message) {
        var event = parseEvent(message, PaymentFailedEvent.class);
        log.info("[LEDGER-EVENT] Processing payment.failed paymentId={}", event.paymentId());

        var existingTransactions = transactionRepository.findByPaymentId(event.paymentId());
        if (existingTransactions.isEmpty()) {
            log.warn("[LEDGER-EVENT] No prior transactions for paymentId={}, skipping reversal",
                    event.paymentId());
            return;
        }

        List<JournalEntryRequest> originalEntries = existingTransactions.stream()
                .flatMap(t -> t.entries().stream())
                .map(e -> new JournalEntryRequest(e.entryType(), e.accountCode(), e.amount(), e.currency()))
                .toList();

        var reversalEntries = AccountingRules.reversalEntries(originalEntries);
        var sourceEventId = deriveSourceEventId(event.paymentId(), "payment.failed");
        journalCommandHandler.postTransaction(new TransactionRequest(
                event.paymentId(), event.correlationId(), "payment.failed", sourceEventId,
                "Reversal: " + event.reason(), reversalEntries));

        reconciliationCommandHandler.markDiscrepancy(event.paymentId());
    }

    // --- ACL Event DTOs (package-private) ---

    record PaymentInitiatedEvent(UUID paymentId, UUID correlationId,
                                 Money sourceAmount, Instant initiatedAt) {}

    record PaymentCompletedEvent(UUID paymentId, UUID correlationId,
                                 Money sourceAmount, Money targetAmount,
                                 FxRateInfo fxRate, Instant completedAt) {}

    record PaymentFailedEvent(UUID paymentId, UUID correlationId,
                              String reason, Instant failedAt) {}

    record FiatCollectedEvent(UUID eventId, UUID paymentId,
                              BigDecimal settledAmount, String currency) {}

    record ChainTransferSubmittedEvent(UUID eventId, UUID paymentId, UUID correlationId,
                                       String stablecoin, String amount, Instant submittedAt) {}

    record ChainTransferConfirmedEvent(UUID eventId, UUID paymentId, UUID correlationId) {}

    record StablecoinRedeemedEvent(UUID eventId, UUID paymentId, UUID correlationId,
                                   String stablecoin, BigDecimal redeemedAmount) {}

    record FiatPayoutCompletedEvent(UUID eventId, UUID paymentId, UUID correlationId,
                                    BigDecimal fiatAmount, String targetCurrency) {}

    record FxRateLockedEvent(UUID lockId, UUID paymentId, UUID correlationId,
                             BigDecimal sourceAmount, Integer feeBps,
                             String fromCurrency) {}

    record Money(BigDecimal amount, String currency) {}

    record FxRateInfo(UUID quoteId, String from, String to, BigDecimal rate,
                      Instant lockedAt, Instant expiresAt, String provider) {}

    // --- Helpers ---

    private <T> T parseEvent(String message, Class<T> type) {
        try {
            return JSON_MAPPER.readValue(message, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse " + type.getSimpleName() + ": " + message, e);
        }
    }

    static UUID deriveSourceEventId(UUID paymentId, String eventType) {
        return UUID.nameUUIDFromBytes((paymentId.toString() + ":" + eventType).getBytes());
    }

    static BigDecimal calculateFee(BigDecimal sourceAmount, Integer feeBps) {
        if (feeBps == null || feeBps <= 0) {
            return BigDecimal.ZERO;
        }
        return sourceAmount.multiply(BigDecimal.valueOf(feeBps))
                .divide(BigDecimal.valueOf(BPS_DIVISOR), FEE_SCALE, RoundingMode.HALF_UP);
    }
}
