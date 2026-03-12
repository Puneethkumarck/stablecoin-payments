package com.stablecoin.payments.ledger.application.controller;

import com.stablecoin.payments.ledger.AbstractIntegrationTest;
import com.stablecoin.payments.ledger.domain.model.EntryType;
import com.stablecoin.payments.ledger.domain.model.JournalEntry;
import com.stablecoin.payments.ledger.domain.model.LedgerTransaction;
import com.stablecoin.payments.ledger.domain.model.ReconciliationLegType;
import com.stablecoin.payments.ledger.domain.model.ReconciliationRecord;
import com.stablecoin.payments.ledger.domain.port.LedgerTransactionRepository;
import com.stablecoin.payments.ledger.domain.port.ReconciliationLegRepository;
import com.stablecoin.payments.ledger.domain.port.ReconciliationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.CLIENT_FUNDS_HELD;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.FIAT_RECEIVABLE;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.DEFAULT_TOLERANCE;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("JournalController IT")
class JournalControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LedgerTransactionRepository transactionRepository;

    @Autowired
    private ReconciliationRepository reconciliationRepository;

    @Autowired
    private ReconciliationLegRepository reconciliationLegRepository;

    @Nested
    @DisplayName("GET /v1/ledger/payments/{paymentId}/journal")
    class GetPaymentJournal {

        @Test
        @DisplayName("should return 200 with transactions and entries")
        void shouldReturn200WithTransactions() throws Exception {
            var paymentId = UUID.randomUUID();
            var transaction = saveBalancedTransaction(paymentId);

            mockMvc.perform(get("/v1/ledger/payments/{paymentId}/journal", paymentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId", is(paymentId.toString())))
                    .andExpect(jsonPath("$.status", is("IN_PROGRESS")))
                    .andExpect(jsonPath("$.transactions", hasSize(1)))
                    .andExpect(jsonPath("$.transactions[0].transactionId",
                            is(transaction.transactionId().toString())))
                    .andExpect(jsonPath("$.transactions[0].sourceEvent", is("payment.initiated")))
                    .andExpect(jsonPath("$.transactions[0].entries", hasSize(2)))
                    .andExpect(jsonPath("$.transactions[0].entries[0].entryType", is("DEBIT")))
                    .andExpect(jsonPath("$.transactions[0].entries[0].accountCode", is(FIAT_RECEIVABLE)))
                    .andExpect(jsonPath("$.transactions[0].entries[1].entryType", is("CREDIT")))
                    .andExpect(jsonPath("$.transactions[0].entries[1].accountCode", is(CLIENT_FUNDS_HELD)))
                    .andExpect(jsonPath("$.reconciliation", nullValue()));
        }

        @Test
        @DisplayName("should return 200 with reconciliation summary when reconciliation exists")
        void shouldReturn200WithReconciliationSummary() throws Exception {
            var paymentId = UUID.randomUUID();
            saveBalancedTransaction(paymentId);

            var record = ReconciliationRecord.create(paymentId, DEFAULT_TOLERANCE);
            var savedRecord = reconciliationRepository.save(record);
            var fiatInLeg = aFiatInLeg(savedRecord.recId());
            reconciliationLegRepository.save(fiatInLeg);

            mockMvc.perform(get("/v1/ledger/payments/{paymentId}/journal", paymentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId", is(paymentId.toString())))
                    .andExpect(jsonPath("$.reconciliation", notNullValue()))
                    .andExpect(jsonPath("$.reconciliation.status", is("PENDING")))
                    .andExpect(jsonPath("$.reconciliation.legs", hasSize(1)))
                    .andExpect(jsonPath("$.reconciliation.legs[0].legType", is("FIAT_IN")));
        }

        @Test
        @DisplayName("should return 404 when no journal exists for payment")
        void shouldReturn404WhenJournalNotFound() throws Exception {
            var paymentId = UUID.randomUUID();

            mockMvc.perform(get("/v1/ledger/payments/{paymentId}/journal", paymentId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("LD-1001")));
        }

        @Test
        @DisplayName("should return 400 for invalid UUID format")
        void shouldReturn400ForInvalidUuid() throws Exception {
            mockMvc.perform(get("/v1/ledger/payments/{paymentId}/journal", "not-a-uuid"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /v1/ledger/accounts/{accountCode}/history")
    class GetAccountHistory {

        @Test
        @DisplayName("should return 200 with paginated entries")
        void shouldReturn200WithPaginatedEntries() throws Exception {
            var paymentId = UUID.randomUUID();
            saveBalancedTransaction(paymentId);

            mockMvc.perform(get("/v1/ledger/accounts/{accountCode}/history", FIAT_RECEIVABLE)
                            .param("currency", "USD")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountCode", is(FIAT_RECEIVABLE)))
                    .andExpect(jsonPath("$.currency", is("USD")))
                    .andExpect(jsonPath("$.entries", hasSize(1)))
                    .andExpect(jsonPath("$.entries[0].entryType", is("DEBIT")))
                    .andExpect(jsonPath("$.entries[0].paymentId", is(paymentId.toString())))
                    .andExpect(jsonPath("$.page", is(0)))
                    .andExpect(jsonPath("$.size", is(20)))
                    .andExpect(jsonPath("$.totalElements", is(1)));
        }

        @Test
        @DisplayName("should return 200 with empty entries when no matching entries")
        void shouldReturn200WithEmptyEntries() throws Exception {
            mockMvc.perform(get("/v1/ledger/accounts/{accountCode}/history", FIAT_RECEIVABLE)
                            .param("currency", "EUR"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.entries", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements", is(0)));
        }

        @Test
        @DisplayName("should return 404 for non-existent account code")
        void shouldReturn404ForNonExistentAccount() throws Exception {
            mockMvc.perform(get("/v1/ledger/accounts/{accountCode}/history", "9999")
                            .param("currency", "USD"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("LD-1002")));
        }
    }

    private LedgerTransaction saveBalancedTransaction(UUID paymentId) {
        var transactionId = UUID.randomUUID();
        var sourceEventId = UUID.randomUUID();
        var correlationId = UUID.randomUUID();
        var now = Instant.now();
        var amount = new BigDecimal("10000.00");

        var entries = List.of(
                new JournalEntry(UUID.randomUUID(), transactionId, paymentId, correlationId,
                        1, EntryType.DEBIT, FIAT_RECEIVABLE, amount, "USD", amount, 1L,
                        "payment.initiated", sourceEventId, now),
                new JournalEntry(UUID.randomUUID(), transactionId, paymentId, correlationId,
                        2, EntryType.CREDIT, CLIENT_FUNDS_HELD, amount, "USD", BigDecimal.ZERO, 1L,
                        "payment.initiated", sourceEventId, now)
        );

        var transaction = new LedgerTransaction(
                transactionId, paymentId, correlationId, "payment.initiated",
                sourceEventId, "Payment initiated, receivable recognized", entries, now
        );

        return transactionRepository.save(transaction);
    }

    private com.stablecoin.payments.ledger.domain.model.ReconciliationLeg aFiatInLeg(UUID recId) {
        return new com.stablecoin.payments.ledger.domain.model.ReconciliationLeg(
                UUID.randomUUID(), recId, ReconciliationLegType.FIAT_IN,
                new BigDecimal("10000.00"), "USD", UUID.randomUUID(), Instant.now()
        );
    }
}
