package com.stablecoin.payments.ledger.infrastructure.messaging;

import com.stablecoin.payments.ledger.domain.model.EntryType;
import com.stablecoin.payments.ledger.domain.model.JournalEntry;
import com.stablecoin.payments.ledger.domain.model.LedgerTransaction;
import com.stablecoin.payments.ledger.domain.model.ReconciliationLeg;
import com.stablecoin.payments.ledger.domain.model.ReconciliationLegType;
import com.stablecoin.payments.ledger.domain.port.LedgerTransactionRepository;
import com.stablecoin.payments.ledger.domain.service.AccountingRules;
import com.stablecoin.payments.ledger.domain.service.JournalCommandHandler;
import com.stablecoin.payments.ledger.domain.service.JournalEntryRequest;
import com.stablecoin.payments.ledger.domain.service.ReconciliationCommandHandler;
import com.stablecoin.payments.ledger.domain.service.TransactionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.stablecoin.payments.ledger.infrastructure.messaging.LedgerEventConsumer.calculateFee;
import static com.stablecoin.payments.ledger.infrastructure.messaging.LedgerEventConsumer.deriveSourceEventId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class LedgerEventConsumerTest {

    private static final UUID PAYMENT_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID CORRELATION_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID EVENT_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID LOCK_ID = UUID.fromString("d4e5f6a7-b8c9-0123-defa-234567890123");
    private static final BigDecimal AMOUNT = new BigDecimal("10000.00");
    private static final BigDecimal USDC_AMOUNT = new BigDecimal("10000.000000");
    private static final Instant NOW = Instant.parse("2026-03-11T10:00:00Z");

    private JournalCommandHandler journalCommandHandler;
    private ReconciliationCommandHandler reconciliationCommandHandler;
    private LedgerTransactionRepository transactionRepository;
    private LedgerEventConsumer consumer;

    @BeforeEach
    void setUp() {
        journalCommandHandler = mock(JournalCommandHandler.class);
        reconciliationCommandHandler = mock(ReconciliationCommandHandler.class);
        transactionRepository = mock(LedgerTransactionRepository.class);

        consumer = new LedgerEventConsumer(
                journalCommandHandler, reconciliationCommandHandler, transactionRepository);
    }

    @Nested
    @DisplayName("payment.initiated")
    class PaymentInitiated {

        @Test
        @DisplayName("should post journal entries and create reconciliation record")
        void postsEntriesAndCreatesRecord() {
            var sourceEventId = deriveSourceEventId(PAYMENT_ID, "payment.initiated");
            var expectedRequest = AccountingRules.paymentInitiated(
                    PAYMENT_ID, CORRELATION_ID, sourceEventId, AMOUNT, "USD");

            consumer.onPaymentInitiated(paymentInitiatedEvent());

            then(journalCommandHandler).should().postTransaction(expectedRequest);
            then(reconciliationCommandHandler).should().createRecord(PAYMENT_ID);
        }
    }

    @Nested
    @DisplayName("fx.rate.locked")
    class FxRateLocked {

        @Test
        @DisplayName("should not post journal entries — record FX_RATE reconciliation leg only")
        void noJournalEntriesRecordsFxLeg() {
            var expectedFee = calculateFee(AMOUNT, 30);

            consumer.onFxRateLocked(fxRateLockedEvent());

            then(journalCommandHandler).shouldHaveNoInteractions();
            then(reconciliationCommandHandler).should().recordLeg(
                    PAYMENT_ID, ReconciliationLegType.FX_RATE,
                    expectedFee, "USD", LOCK_ID);
        }
    }

    @Nested
    @DisplayName("fiat.collected")
    class FiatCollected {

        @Test
        @DisplayName("should post journal entries and record FIAT_IN leg")
        void postsEntriesAndRecordsFiatInLeg() {
            var expectedRequest = AccountingRules.fiatCollected(
                    PAYMENT_ID, PAYMENT_ID, EVENT_ID, AMOUNT, "USD");

            consumer.onFiatCollected(fiatCollectedEvent());

            then(journalCommandHandler).should().postTransaction(expectedRequest);
            then(reconciliationCommandHandler).should().recordLeg(
                    PAYMENT_ID, ReconciliationLegType.FIAT_IN,
                    AMOUNT, "USD", EVENT_ID);
        }
    }

    @Nested
    @DisplayName("chain.transfer.submitted")
    class ChainTransferSubmitted {

        @Test
        @DisplayName("should post journal entries and record STABLECOIN_MINTED leg")
        void postsEntriesAndRecordsMintedLeg() {
            var expectedRequest = AccountingRules.chainTransferSubmitted(
                    PAYMENT_ID, CORRELATION_ID, EVENT_ID, USDC_AMOUNT, "USDC");

            consumer.onChainTransferSubmitted(chainTransferSubmittedEvent());

            then(journalCommandHandler).should().postTransaction(expectedRequest);
            then(reconciliationCommandHandler).should().recordLeg(
                    PAYMENT_ID, ReconciliationLegType.STABLECOIN_MINTED,
                    USDC_AMOUNT, "USDC", EVENT_ID);
        }
    }

    @Nested
    @DisplayName("chain.transfer.confirmed")
    class ChainTransferConfirmed {

        @Test
        @DisplayName("should look up amount from prior submitted and post entries")
        void looksUpAmountAndPostsEntries() {
            stubPriorSubmittedTransaction();

            var expectedRequest = AccountingRules.chainTransferConfirmed(
                    PAYMENT_ID, CORRELATION_ID, EVENT_ID, USDC_AMOUNT, "USDC");

            consumer.onChainTransferConfirmed(chainTransferConfirmedEvent());

            then(journalCommandHandler).should().postTransaction(expectedRequest);
            then(reconciliationCommandHandler).should().recordLeg(
                    PAYMENT_ID, ReconciliationLegType.CHAIN_TRANSFERRED,
                    USDC_AMOUNT, "USDC", EVENT_ID);
        }

        @Test
        @DisplayName("should throw when no prior submitted transaction exists")
        void throwsWhenNoPriorSubmitted() {
            given(transactionRepository.findByPaymentId(PAYMENT_ID)).willReturn(List.of());

            assertThatThrownBy(() -> consumer.onChainTransferConfirmed(chainTransferConfirmedEvent()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No chain.transfer.submitted found");
        }
    }

    @Nested
    @DisplayName("stablecoin.redeemed")
    class StablecoinRedeemed {

        @Test
        @DisplayName("should post journal entries and record STABLECOIN_REDEEMED leg")
        void postsEntriesAndRecordsRedeemedLeg() {
            var expectedRequest = AccountingRules.stablecoinRedeemed(
                    PAYMENT_ID, CORRELATION_ID, EVENT_ID, USDC_AMOUNT, "USDC");

            consumer.onStablecoinRedeemed(stablecoinRedeemedEvent());

            then(journalCommandHandler).should().postTransaction(expectedRequest);
            then(reconciliationCommandHandler).should().recordLeg(
                    PAYMENT_ID, ReconciliationLegType.STABLECOIN_REDEEMED,
                    USDC_AMOUNT, "USDC", EVENT_ID);
        }
    }

    @Nested
    @DisplayName("fiat.payout.completed")
    class FiatPayoutCompleted {

        @Test
        @DisplayName("should post journal entries and record FIAT_OUT leg")
        void postsEntriesAndRecordsFiatOutLeg() {
            var expectedRequest = AccountingRules.fiatPayoutCompleted(
                    PAYMENT_ID, CORRELATION_ID, EVENT_ID,
                    new BigDecimal("9200.00"), "EUR");

            consumer.onFiatPayoutCompleted(fiatPayoutCompletedEvent());

            then(journalCommandHandler).should().postTransaction(expectedRequest);
            then(reconciliationCommandHandler).should().recordLeg(
                    PAYMENT_ID, ReconciliationLegType.FIAT_OUT,
                    new BigDecimal("9200.00"), "EUR", EVENT_ID);
        }
    }

    @Nested
    @DisplayName("payment.completed")
    class PaymentCompleted {

        @Test
        @DisplayName("should post clearing transaction with stablecoin amount from prior submitted")
        void postsClearingTransaction() {
            stubPriorSubmittedTransaction();

            var clearingEventId = deriveSourceEventId(PAYMENT_ID, "payment.completed.clearing");
            var expectedClearing = AccountingRules.paymentCompletedClearing(
                    PAYMENT_ID, CORRELATION_ID, clearingEventId, USDC_AMOUNT, "USDC");

            given(reconciliationCommandHandler.findLeg(PAYMENT_ID, ReconciliationLegType.FX_RATE))
                    .willReturn(Optional.empty());

            consumer.onPaymentCompleted(paymentCompletedEvent());

            then(journalCommandHandler).should().postTransaction(expectedClearing);
        }

        @Test
        @DisplayName("should post revenue transaction when FX_RATE leg has fee")
        void postsRevenueTransaction() {
            stubPriorSubmittedTransaction();

            var feeAmount = new BigDecimal("30.00000000");
            var fxLeg = new ReconciliationLeg(UUID.randomUUID(), UUID.randomUUID(),
                    ReconciliationLegType.FX_RATE, feeAmount, "USD", LOCK_ID, NOW);
            given(reconciliationCommandHandler.findLeg(PAYMENT_ID, ReconciliationLegType.FX_RATE))
                    .willReturn(Optional.of(fxLeg));

            var revenueEventId = deriveSourceEventId(PAYMENT_ID, "payment.completed.revenue");
            var expectedRevenue = AccountingRules.paymentCompletedRevenue(
                    PAYMENT_ID, CORRELATION_ID, revenueEventId, feeAmount, "USD");

            consumer.onPaymentCompleted(paymentCompletedEvent());

            then(journalCommandHandler).should().postTransaction(expectedRevenue);
        }

        @Test
        @DisplayName("should skip revenue when FX_RATE leg has zero fee")
        void skipsRevenueWhenZeroFee() {
            stubPriorSubmittedTransaction();

            var zeroFeeLeg = new ReconciliationLeg(UUID.randomUUID(), UUID.randomUUID(),
                    ReconciliationLegType.FX_RATE, BigDecimal.ZERO, "USD", LOCK_ID, NOW);
            given(reconciliationCommandHandler.findLeg(PAYMENT_ID, ReconciliationLegType.FX_RATE))
                    .willReturn(Optional.of(zeroFeeLeg));

            consumer.onPaymentCompleted(paymentCompletedEvent());

            // Only clearing posted, not revenue
            var clearingEventId = deriveSourceEventId(PAYMENT_ID, "payment.completed.clearing");
            var expectedClearing = AccountingRules.paymentCompletedClearing(
                    PAYMENT_ID, CORRELATION_ID, clearingEventId, USDC_AMOUNT, "USDC");
            then(journalCommandHandler).should().postTransaction(expectedClearing);
        }
    }

    @Nested
    @DisplayName("payment.failed")
    class PaymentFailed {

        @Test
        @DisplayName("should post reversal entries for all prior transactions")
        void postsReversalEntries() {
            var priorTx = aBalancedTransaction("payment.initiated");
            given(transactionRepository.findByPaymentId(PAYMENT_ID)).willReturn(List.of(priorTx));

            var originalEntries = priorTx.entries().stream()
                    .map(e -> new JournalEntryRequest(e.entryType(), e.accountCode(), e.amount(), e.currency()))
                    .toList();
            var reversalEntries = AccountingRules.reversalEntries(originalEntries);
            var sourceEventId = deriveSourceEventId(PAYMENT_ID, "payment.failed");
            var expectedRequest = new TransactionRequest(
                    PAYMENT_ID, CORRELATION_ID, "payment.failed", sourceEventId,
                    "Reversal: Compliance check failed", reversalEntries);

            consumer.onPaymentFailed(paymentFailedEvent());

            then(journalCommandHandler).should().postTransaction(expectedRequest);
            then(reconciliationCommandHandler).should().markDiscrepancy(PAYMENT_ID);
        }

        @Test
        @DisplayName("should skip reversal when no prior transactions exist")
        void skipsWhenNoPriorTransactions() {
            given(transactionRepository.findByPaymentId(PAYMENT_ID)).willReturn(List.of());

            consumer.onPaymentFailed(paymentFailedEvent());

            then(journalCommandHandler).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("helpers")
    class Helpers {

        @Test
        @DisplayName("deriveSourceEventId should be deterministic")
        void deriveSourceEventIdIsDeterministic() {
            var first = deriveSourceEventId(PAYMENT_ID, "payment.initiated");
            var second = deriveSourceEventId(PAYMENT_ID, "payment.initiated");

            assertThat(first).isEqualTo(second);
        }

        @Test
        @DisplayName("deriveSourceEventId should differ by event type")
        void deriveSourceEventIdDiffersByType() {
            var initiated = deriveSourceEventId(PAYMENT_ID, "payment.initiated");
            var completed = deriveSourceEventId(PAYMENT_ID, "payment.completed.clearing");

            assertThat(initiated).isNotEqualTo(completed);
        }

        @Test
        @DisplayName("calculateFee computes bps correctly")
        void calculateFeeComputation() {
            var fee = calculateFee(new BigDecimal("10000.00"), 30);

            var expected = new BigDecimal("10000.00")
                    .multiply(BigDecimal.valueOf(30))
                    .divide(BigDecimal.valueOf(10000), 8, RoundingMode.HALF_UP);
            assertThat(fee).isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("calculateFee returns zero when feeBps is null")
        void calculateFeeNullBps() {
            var fee = calculateFee(new BigDecimal("10000.00"), null);

            assertThat(fee).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("parseEvent should throw on invalid JSON")
        void parseErrorThrows() {
            assertThatThrownBy(() -> consumer.onPaymentInitiated("not valid json"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Failed to parse");
        }
    }

    // --- Event JSON builders ---

    private static String paymentInitiatedEvent() {
        return """
                {"paymentId":"%s","correlationId":"%s","sourceAmount":{"amount":10000.00,"currency":"USD"},"targetCurrency":"EUR","initiatedAt":"%s"}
                """.formatted(PAYMENT_ID, CORRELATION_ID, NOW).trim();
    }

    private static String fxRateLockedEvent() {
        return """
                {"lockId":"%s","paymentId":"%s","correlationId":"%s","sourceAmount":10000.00,"feeBps":30,"fromCurrency":"USD","toCurrency":"EUR","lockedRate":0.92}
                """.formatted(LOCK_ID, PAYMENT_ID, CORRELATION_ID).trim();
    }

    private static String fiatCollectedEvent() {
        return """
                {"eventId":"%s","paymentId":"%s","settledAmount":10000.00,"currency":"USD","schemaVersion":1}
                """.formatted(EVENT_ID, PAYMENT_ID).trim();
    }

    private static String chainTransferSubmittedEvent() {
        return """
                {"eventId":"%s","paymentId":"%s","correlationId":"%s","stablecoin":"USDC","amount":"10000.000000","submittedAt":"%s"}
                """.formatted(EVENT_ID, PAYMENT_ID, CORRELATION_ID, NOW).trim();
    }

    private static String chainTransferConfirmedEvent() {
        return """
                {"eventId":"%s","paymentId":"%s","correlationId":"%s","confirmedAt":"%s"}
                """.formatted(EVENT_ID, PAYMENT_ID, CORRELATION_ID, NOW).trim();
    }

    private static String stablecoinRedeemedEvent() {
        return """
                {"eventId":"%s","paymentId":"%s","correlationId":"%s","stablecoin":"USDC","redeemedAmount":10000.000000}
                """.formatted(EVENT_ID, PAYMENT_ID, CORRELATION_ID).trim();
    }

    private static String fiatPayoutCompletedEvent() {
        return """
                {"eventId":"%s","paymentId":"%s","correlationId":"%s","fiatAmount":9200.00,"targetCurrency":"EUR"}
                """.formatted(EVENT_ID, PAYMENT_ID, CORRELATION_ID).trim();
    }

    private static String paymentCompletedEvent() {
        return """
                {"paymentId":"%s","correlationId":"%s","sourceAmount":{"amount":10000.00,"currency":"USD"},"targetAmount":{"amount":9200.00,"currency":"EUR"},"fxRate":{"quoteId":"%s","from":"USD","to":"EUR","rate":0.92,"lockedAt":"%s","expiresAt":"%s","provider":"refinitiv"},"completedAt":"%s"}
                """.formatted(PAYMENT_ID, CORRELATION_ID, UUID.randomUUID(), NOW, NOW.plusSeconds(30), NOW).trim();
    }

    private static String paymentFailedEvent() {
        return """
                {"paymentId":"%s","correlationId":"%s","reason":"Compliance check failed","failedAt":"%s"}
                """.formatted(PAYMENT_ID, CORRELATION_ID, NOW).trim();
    }

    // --- Helpers ---

    private void stubPriorSubmittedTransaction() {
        var submittedTx = aBalancedTransaction("chain.transfer.submitted");
        given(transactionRepository.findByPaymentId(PAYMENT_ID)).willReturn(List.of(submittedTx));
    }

    private static LedgerTransaction aBalancedTransaction(String sourceEvent) {
        var txId = UUID.randomUUID();
        var debit = new JournalEntry(UUID.randomUUID(), txId, PAYMENT_ID, CORRELATION_ID,
                1, EntryType.DEBIT, "1010", USDC_AMOUNT, "USDC",
                USDC_AMOUNT, 1L, sourceEvent, EVENT_ID, NOW);
        var credit = new JournalEntry(UUID.randomUUID(), txId, PAYMENT_ID, CORRELATION_ID,
                2, EntryType.CREDIT, "9000", USDC_AMOUNT, "USDC",
                BigDecimal.ZERO, 1L, sourceEvent, EVENT_ID, NOW);
        return new LedgerTransaction(txId, PAYMENT_ID, CORRELATION_ID,
                sourceEvent, EVENT_ID, "Test transaction",
                List.of(debit, credit), NOW);
    }
}
