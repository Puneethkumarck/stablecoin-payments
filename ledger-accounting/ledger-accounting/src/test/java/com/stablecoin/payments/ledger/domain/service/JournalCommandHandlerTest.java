package com.stablecoin.payments.ledger.domain.service;

import com.stablecoin.payments.ledger.domain.model.AccountBalance;
import com.stablecoin.payments.ledger.domain.model.AuditEvent;
import com.stablecoin.payments.ledger.domain.model.LedgerTransaction;
import com.stablecoin.payments.ledger.domain.port.AccountBalanceRepository;
import com.stablecoin.payments.ledger.domain.port.AuditEventRepository;
import com.stablecoin.payments.ledger.domain.port.JournalEntryRepository;
import com.stablecoin.payments.ledger.domain.port.LedgerTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static com.stablecoin.payments.ledger.domain.model.EntryType.CREDIT;
import static com.stablecoin.payments.ledger.domain.model.EntryType.DEBIT;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.CLIENT_FUNDS_HELD;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.CORRELATION_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.FIAT_RECEIVABLE;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.FX_SPREAD_REVENUE;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.IN_TRANSIT_CLEARING;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.NOW;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.PAYMENT_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.SOURCE_EVENT_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.STABLECOIN_REDEEMED_ACCT;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aBalancedTransaction;
import static com.stablecoin.payments.ledger.fixtures.TestUtils.eqIgnoring;
import static com.stablecoin.payments.ledger.fixtures.TestUtils.eqIgnoringTimestamps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("JournalCommandHandler")
class JournalCommandHandlerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneId.of("UTC"));
    private static final BigDecimal AMOUNT = new BigDecimal("10000.00");
    private static final BigDecimal STABLECOIN_AMOUNT = new BigDecimal("10000.000000");
    private static final BigDecimal FEE_AMOUNT = new BigDecimal("30.00");

    @Mock
    private LedgerTransactionRepository transactionRepository;

    @Mock
    private JournalEntryRepository entryRepository;

    @Mock
    private AccountBalanceRepository balanceRepository;

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private BalanceCalculator balanceCalculator;

    @InjectMocks
    private JournalCommandHandler handler;

    private JournalCommandHandler createHandler() {
        return new JournalCommandHandler(
                transactionRepository, entryRepository, balanceRepository,
                auditEventRepository, balanceCalculator, FIXED_CLOCK
        );
    }

    @Nested
    @DisplayName("postTransaction — payment.initiated")
    class PaymentInitiated {

        private final TransactionRequest request = AccountingRules.paymentInitiated(
                PAYMENT_ID, CORRELATION_ID, SOURCE_EVENT_ID, AMOUNT, "USD");

        private void stubHappyPath() {
            given(transactionRepository.existsBySourceEventId(SOURCE_EVENT_ID)).willReturn(false);
            given(entryRepository.countByPaymentId(PAYMENT_ID)).willReturn(0);
            given(balanceCalculator.computeBalances(request.entries())).willReturn(Map.of(
                    BalanceCalculator.balanceKey(FIAT_RECEIVABLE, "USD"),
                    new BalanceUpdate(AMOUNT, 1L),
                    BalanceCalculator.balanceKey(CLIENT_FUNDS_HELD, "USD"),
                    new BalanceUpdate(AMOUNT, 1L)
            ));
            given(transactionRepository.save(eqIgnoring(
                    aPlaceholderTransaction(request), "transactionId", "entries")))
                    .willAnswer(invocation -> invocation.getArgument(0));
        }

        @Test
        @DisplayName("saves LedgerTransaction with correct metadata")
        void savesTransaction() {
            handler = createHandler();
            stubHappyPath();

            handler.postTransaction(request);

            then(transactionRepository).should().save(eqIgnoring(
                    aPlaceholderTransaction(request), "transactionId", "entries"));
        }

        @Test
        @DisplayName("persists balance updates for both accounts")
        void persistsBalanceUpdates() {
            handler = createHandler();
            stubHappyPath();

            handler.postTransaction(request);

            then(balanceRepository).should().save(eqIgnoring(
                    new AccountBalance(FIAT_RECEIVABLE, "USD", AMOUNT, 1L, null, NOW),
                    "lastEntryId"));
            then(balanceRepository).should().save(eqIgnoring(
                    new AccountBalance(CLIENT_FUNDS_HELD, "USD", AMOUNT, 1L, null, NOW),
                    "lastEntryId"));
        }

        @Test
        @DisplayName("saves audit event")
        void savesAuditEvent() {
            handler = createHandler();
            stubHappyPath();

            handler.postTransaction(request);

            then(auditEventRepository).should().save(eqIgnoring(
                    AuditEvent.create(CORRELATION_ID, PAYMENT_ID,
                            "ledger-accounting", "journal.posted", "{}", "system", NOW),
                    "auditId", "eventPayload", "receivedAt"));
        }

        @Test
        @DisplayName("uses sequence starting at 1 for first transaction")
        void usesSequenceStartingAtOne() {
            handler = createHandler();
            stubHappyPath();

            handler.postTransaction(request);

            ArgumentCaptor<LedgerTransaction> captor = ArgumentCaptor.forClass(LedgerTransaction.class);
            then(transactionRepository).should().save(captor.capture());

            var entries = captor.getValue().entries();
            assertThat(entries).extracting("sequenceNo").containsExactly(1, 2);
        }

        @Test
        @DisplayName("continues sequence from existing entries")
        void continuesSequenceFromExisting() {
            handler = createHandler();
            given(transactionRepository.existsBySourceEventId(SOURCE_EVENT_ID)).willReturn(false);
            given(entryRepository.countByPaymentId(PAYMENT_ID)).willReturn(4);
            given(balanceCalculator.computeBalances(request.entries())).willReturn(Map.of(
                    BalanceCalculator.balanceKey(FIAT_RECEIVABLE, "USD"),
                    new BalanceUpdate(AMOUNT, 1L),
                    BalanceCalculator.balanceKey(CLIENT_FUNDS_HELD, "USD"),
                    new BalanceUpdate(AMOUNT, 1L)
            ));
            given(transactionRepository.save(eqIgnoring(
                    aPlaceholderTransaction(request), "transactionId", "entries")))
                    .willAnswer(invocation -> invocation.getArgument(0));

            handler.postTransaction(request);

            ArgumentCaptor<LedgerTransaction> captor = ArgumentCaptor.forClass(LedgerTransaction.class);
            then(transactionRepository).should().save(captor.capture());

            var entries = captor.getValue().entries();
            assertThat(entries).extracting("sequenceNo").containsExactly(5, 6);
        }

        @Test
        @DisplayName("entries carry correct balance_after and account_version")
        void entriesCarryCorrectBalanceInfo() {
            handler = createHandler();
            stubHappyPath();

            handler.postTransaction(request);

            ArgumentCaptor<LedgerTransaction> captor = ArgumentCaptor.forClass(LedgerTransaction.class);
            then(transactionRepository).should().save(captor.capture());

            var entries = captor.getValue().entries();
            var debitEntry = entries.stream().filter(e -> e.entryType() == DEBIT).findFirst().orElseThrow();
            var creditEntry = entries.stream().filter(e -> e.entryType() == CREDIT).findFirst().orElseThrow();

            assertThat(debitEntry.balanceAfter()).isEqualByComparingTo(AMOUNT);
            assertThat(debitEntry.accountVersion()).isEqualTo(1L);
            assertThat(creditEntry.balanceAfter()).isEqualByComparingTo(AMOUNT);
            assertThat(creditEntry.accountVersion()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("postTransaction — idempotency")
    class Idempotency {

        private final TransactionRequest request = AccountingRules.paymentInitiated(
                PAYMENT_ID, CORRELATION_ID, SOURCE_EVENT_ID, AMOUNT, "USD");

        @Test
        @DisplayName("returns existing transaction when sourceEventId already processed")
        void returnsExistingTransaction() {
            handler = createHandler();
            var existingTx = aBalancedTransaction();

            given(transactionRepository.existsBySourceEventId(SOURCE_EVENT_ID)).willReturn(true);
            given(transactionRepository.findByPaymentId(PAYMENT_ID))
                    .willReturn(List.of(existingTx));

            var result = handler.postTransaction(request);

            assertThat(result.sourceEventId()).isEqualTo(SOURCE_EVENT_ID);
            then(balanceCalculator).should(never()).computeBalances(request.entries());
            then(balanceRepository).should(never()).save(eqIgnoringTimestamps(
                    AccountBalance.zero(FIAT_RECEIVABLE, "USD")));
        }

        @Test
        @DisplayName("does not save new transaction or audit event on replay")
        void doesNotSaveOnReplay() {
            handler = createHandler();
            var existingTx = aBalancedTransaction();

            given(transactionRepository.existsBySourceEventId(SOURCE_EVENT_ID)).willReturn(true);
            given(transactionRepository.findByPaymentId(PAYMENT_ID))
                    .willReturn(List.of(existingTx));

            handler.postTransaction(request);

            then(transactionRepository).should(never()).save(eqIgnoring(
                    aPlaceholderTransaction(request), "transactionId", "entries"));
            then(auditEventRepository).should(never()).save(eqIgnoring(
                    AuditEvent.create(CORRELATION_ID, PAYMENT_ID,
                            "ledger-accounting", "journal.posted", "{}", "system", NOW),
                    "auditId", "eventPayload", "receivedAt"));
        }
    }

    @Nested
    @DisplayName("postTransaction — payment.completed (two transactions)")
    class PaymentCompleted {

        @Test
        @DisplayName("posts clearing transaction")
        void postsClearingTransaction() {
            handler = createHandler();
            var clearingEventId = java.util.UUID.randomUUID();
            var clearingRequest = AccountingRules.paymentCompletedClearing(
                    PAYMENT_ID, CORRELATION_ID, clearingEventId, STABLECOIN_AMOUNT, "USDC");

            given(transactionRepository.existsBySourceEventId(clearingEventId)).willReturn(false);
            given(entryRepository.countByPaymentId(PAYMENT_ID)).willReturn(10);
            given(balanceCalculator.computeBalances(clearingRequest.entries())).willReturn(Map.of(
                    BalanceCalculator.balanceKey(IN_TRANSIT_CLEARING, "USDC"),
                    new BalanceUpdate(BigDecimal.ZERO, 2L),
                    BalanceCalculator.balanceKey(STABLECOIN_REDEEMED_ACCT, "USDC"),
                    new BalanceUpdate(BigDecimal.ZERO, 2L)
            ));
            given(transactionRepository.save(eqIgnoring(
                    aPlaceholderTransaction(clearingRequest), "transactionId", "entries")))
                    .willAnswer(invocation -> invocation.getArgument(0));

            handler.postTransaction(clearingRequest);

            then(transactionRepository).should().save(eqIgnoring(
                    aPlaceholderTransaction(clearingRequest), "transactionId", "entries"));
        }

        @Test
        @DisplayName("posts revenue recognition transaction")
        void postsRevenueTransaction() {
            handler = createHandler();
            var revenueEventId = java.util.UUID.randomUUID();
            var revenueRequest = AccountingRules.paymentCompletedRevenue(
                    PAYMENT_ID, CORRELATION_ID, revenueEventId, FEE_AMOUNT, "USD");

            given(transactionRepository.existsBySourceEventId(revenueEventId)).willReturn(false);
            given(entryRepository.countByPaymentId(PAYMENT_ID)).willReturn(12);
            given(balanceCalculator.computeBalances(revenueRequest.entries())).willReturn(Map.of(
                    BalanceCalculator.balanceKey(CLIENT_FUNDS_HELD, "USD"),
                    new BalanceUpdate(new BigDecimal("-30.00"), 3L),
                    BalanceCalculator.balanceKey(FX_SPREAD_REVENUE, "USD"),
                    new BalanceUpdate(FEE_AMOUNT, 1L)
            ));
            given(transactionRepository.save(eqIgnoring(
                    aPlaceholderTransaction(revenueRequest), "transactionId", "entries")))
                    .willAnswer(invocation -> invocation.getArgument(0));

            handler.postTransaction(revenueRequest);

            then(balanceRepository).should().save(eqIgnoring(
                    new AccountBalance(FX_SPREAD_REVENUE, "USD", FEE_AMOUNT, 1L, null, NOW),
                    "lastEntryId"));
        }
    }

    @Nested
    @DisplayName("postTransaction — all event types produce valid transactions")
    class AllEventTypes {

        @Test
        @DisplayName("fiat.collected saves balanced transaction")
        void fiatCollected() {
            handler = createHandler();
            var request = AccountingRules.fiatCollected(
                    PAYMENT_ID, CORRELATION_ID, SOURCE_EVENT_ID, AMOUNT, "USD");
            stubForRequest(request);

            handler.postTransaction(request);

            then(transactionRepository).should().save(eqIgnoring(
                    aPlaceholderTransaction(request), "transactionId", "entries"));
        }

        @Test
        @DisplayName("chain.transfer.submitted saves balanced transaction")
        void chainTransferSubmitted() {
            handler = createHandler();
            var request = AccountingRules.chainTransferSubmitted(
                    PAYMENT_ID, CORRELATION_ID, SOURCE_EVENT_ID, STABLECOIN_AMOUNT, "USDC");
            stubForRequest(request);

            handler.postTransaction(request);

            then(transactionRepository).should().save(eqIgnoring(
                    aPlaceholderTransaction(request), "transactionId", "entries"));
        }

        @Test
        @DisplayName("chain.transfer.confirmed saves balanced transaction")
        void chainTransferConfirmed() {
            handler = createHandler();
            var request = AccountingRules.chainTransferConfirmed(
                    PAYMENT_ID, CORRELATION_ID, SOURCE_EVENT_ID, STABLECOIN_AMOUNT, "USDC");
            stubForRequest(request);

            handler.postTransaction(request);

            then(transactionRepository).should().save(eqIgnoring(
                    aPlaceholderTransaction(request), "transactionId", "entries"));
        }

        @Test
        @DisplayName("stablecoin.redeemed saves balanced transaction")
        void stablecoinRedeemed() {
            handler = createHandler();
            var request = AccountingRules.stablecoinRedeemed(
                    PAYMENT_ID, CORRELATION_ID, SOURCE_EVENT_ID, STABLECOIN_AMOUNT, "USDC");
            stubForRequest(request);

            handler.postTransaction(request);

            then(transactionRepository).should().save(eqIgnoring(
                    aPlaceholderTransaction(request), "transactionId", "entries"));
        }

        @Test
        @DisplayName("fiat.payout.completed saves balanced transaction")
        void fiatPayoutCompleted() {
            handler = createHandler();
            var request = AccountingRules.fiatPayoutCompleted(
                    PAYMENT_ID, CORRELATION_ID, SOURCE_EVENT_ID, new BigDecimal("9200.00"), "EUR");
            stubForRequest(request);

            handler.postTransaction(request);

            then(transactionRepository).should().save(eqIgnoring(
                    aPlaceholderTransaction(request), "transactionId", "entries"));
        }

        private void stubForRequest(TransactionRequest request) {
            given(transactionRepository.existsBySourceEventId(request.sourceEventId())).willReturn(false);
            given(entryRepository.countByPaymentId(request.paymentId())).willReturn(0);

            Map<String, BalanceUpdate> balanceMap = new java.util.LinkedHashMap<>();
            for (JournalEntryRequest entry : request.entries()) {
                balanceMap.put(
                        BalanceCalculator.balanceKey(entry.accountCode(), entry.currency()),
                        new BalanceUpdate(entry.amount(), 1L)
                );
            }
            given(balanceCalculator.computeBalances(request.entries())).willReturn(balanceMap);
            given(transactionRepository.save(eqIgnoring(
                    aPlaceholderTransaction(request), "transactionId", "entries")))
                    .willAnswer(invocation -> invocation.getArgument(0));
        }
    }

    /**
     * Creates a placeholder LedgerTransaction for eqIgnoring matching.
     * The transactionId and entries are ignored during comparison.
     */
    private static LedgerTransaction aPlaceholderTransaction(TransactionRequest request) {
        var placeholderEntries = List.of(
                new com.stablecoin.payments.ledger.domain.model.JournalEntry(
                        java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                        request.paymentId(), request.correlationId(),
                        1, DEBIT, "1000", BigDecimal.ONE, "USD",
                        BigDecimal.ONE, 1L, request.sourceEvent(),
                        request.sourceEventId(), NOW
                ),
                new com.stablecoin.payments.ledger.domain.model.JournalEntry(
                        java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                        request.paymentId(), request.correlationId(),
                        2, CREDIT, "2010", BigDecimal.ONE, "USD",
                        BigDecimal.ONE, 1L, request.sourceEvent(),
                        request.sourceEventId(), NOW
                )
        );
        return new LedgerTransaction(
                java.util.UUID.randomUUID(), request.paymentId(), request.correlationId(),
                request.sourceEvent(), request.sourceEventId(), request.description(),
                placeholderEntries, NOW
        );
    }
}
