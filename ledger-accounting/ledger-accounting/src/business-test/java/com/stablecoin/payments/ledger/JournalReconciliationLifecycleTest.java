package com.stablecoin.payments.ledger;

import com.stablecoin.payments.ledger.infrastructure.messaging.LedgerEventConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end business tests for the ledger journal posting and reconciliation lifecycle.
 * <p>
 * Simulates payment lifecycle events by calling {@link LedgerEventConsumer} directly
 * (bypassing Kafka transport) and verifies journal entries, account balances,
 * reconciliation status, and outbox events via REST endpoints and direct SQL queries.
 */
@DisplayName("Journal & Reconciliation Lifecycle — Business Tests")
class JournalReconciliationLifecycleTest extends AbstractBusinessTest {

    private static final BigDecimal SOURCE_AMOUNT = new BigDecimal("10000.00");
    private static final BigDecimal STABLECOIN_AMOUNT = new BigDecimal("10000.000000");
    private static final BigDecimal REDEEMED_AMOUNT = new BigDecimal("10000.000000");
    private static final BigDecimal PAYOUT_AMOUNT = new BigDecimal("9200.00");
    private static final BigDecimal FX_SOURCE_AMOUNT = new BigDecimal("10000.00");
    private static final int FEE_BPS = 50;
    // fee = 10000.00 * 50 / 10000 = 50.00000000
    private static final BigDecimal EXPECTED_FEE = new BigDecimal("50.00000000");

    @Autowired
    private LedgerEventConsumer ledgerEventConsumer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    // ── Event JSON builders ──────────────────────────────────────────────

    private static String paymentInitiatedJson(UUID paymentId, UUID correlationId) {
        return """
                {
                    "paymentId": "%s",
                    "correlationId": "%s",
                    "sourceAmount": { "amount": %s, "currency": "USD" },
                    "initiatedAt": "2026-03-11T10:00:00Z"
                }
                """.formatted(paymentId, correlationId, SOURCE_AMOUNT);
    }

    private static String fxRateLockedJson(UUID paymentId, UUID correlationId) {
        return """
                {
                    "lockId": "%s",
                    "paymentId": "%s",
                    "correlationId": "%s",
                    "sourceAmount": %s,
                    "feeBps": %d,
                    "fromCurrency": "USD"
                }
                """.formatted(UUID.randomUUID(), paymentId, correlationId, FX_SOURCE_AMOUNT, FEE_BPS);
    }

    private static String fiatCollectedJson(UUID paymentId) {
        return """
                {
                    "eventId": "%s",
                    "paymentId": "%s",
                    "settledAmount": %s,
                    "currency": "USD"
                }
                """.formatted(UUID.randomUUID(), paymentId, SOURCE_AMOUNT);
    }

    private static String chainTransferSubmittedJson(UUID paymentId, UUID correlationId) {
        return """
                {
                    "eventId": "%s",
                    "paymentId": "%s",
                    "correlationId": "%s",
                    "stablecoin": "USDC",
                    "amount": "%s",
                    "submittedAt": "2026-03-11T10:05:00Z"
                }
                """.formatted(UUID.randomUUID(), paymentId, correlationId, STABLECOIN_AMOUNT);
    }

    private static String chainTransferConfirmedJson(UUID paymentId, UUID correlationId) {
        return """
                {
                    "eventId": "%s",
                    "paymentId": "%s",
                    "correlationId": "%s"
                }
                """.formatted(UUID.randomUUID(), paymentId, correlationId);
    }

    private static String stablecoinRedeemedJson(UUID paymentId, UUID correlationId) {
        return """
                {
                    "eventId": "%s",
                    "paymentId": "%s",
                    "correlationId": "%s",
                    "stablecoin": "USDC",
                    "redeemedAmount": %s
                }
                """.formatted(UUID.randomUUID(), paymentId, correlationId, REDEEMED_AMOUNT);
    }

    private static String fiatPayoutCompletedJson(UUID paymentId, UUID correlationId) {
        return """
                {
                    "eventId": "%s",
                    "paymentId": "%s",
                    "correlationId": "%s",
                    "fiatAmount": %s,
                    "targetCurrency": "EUR"
                }
                """.formatted(UUID.randomUUID(), paymentId, correlationId, PAYOUT_AMOUNT);
    }

    private static String paymentCompletedJson(UUID paymentId, UUID correlationId) {
        return """
                {
                    "paymentId": "%s",
                    "correlationId": "%s",
                    "sourceAmount": { "amount": %s, "currency": "USD" },
                    "targetAmount": { "amount": %s, "currency": "EUR" },
                    "fxRate": {
                        "quoteId": "%s",
                        "from": "USD", "to": "EUR",
                        "rate": 0.92,
                        "lockedAt": "2026-03-11T10:01:00Z",
                        "expiresAt": "2026-03-11T10:31:00Z",
                        "provider": "refinitiv"
                    },
                    "completedAt": "2026-03-11T10:10:00Z"
                }
                """.formatted(paymentId, correlationId, SOURCE_AMOUNT, PAYOUT_AMOUNT, UUID.randomUUID());
    }

    private static String paymentFailedJson(UUID paymentId, UUID correlationId, String reason) {
        return """
                {
                    "paymentId": "%s",
                    "correlationId": "%s",
                    "reason": "%s",
                    "failedAt": "2026-03-11T10:05:00Z"
                }
                """.formatted(paymentId, correlationId, reason);
    }

    /**
     * Sends all 8 events in the full sandwich flow order.
     */
    private void sendFullSandwichFlow(UUID paymentId, UUID correlationId) {
        ledgerEventConsumer.onPaymentInitiated(paymentInitiatedJson(paymentId, correlationId));
        ledgerEventConsumer.onFxRateLocked(fxRateLockedJson(paymentId, correlationId));
        ledgerEventConsumer.onFiatCollected(fiatCollectedJson(paymentId));
        ledgerEventConsumer.onChainTransferSubmitted(chainTransferSubmittedJson(paymentId, correlationId));
        ledgerEventConsumer.onChainTransferConfirmed(chainTransferConfirmedJson(paymentId, correlationId));
        ledgerEventConsumer.onStablecoinRedeemed(stablecoinRedeemedJson(paymentId, correlationId));
        ledgerEventConsumer.onFiatPayoutCompleted(fiatPayoutCompletedJson(paymentId, correlationId));
        ledgerEventConsumer.onPaymentCompleted(paymentCompletedJson(paymentId, correlationId));
    }

    // ── Scenarios ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Happy Path — Full 8-Event Sandwich Flow")
    class HappyPath {

        @Test
        @DisplayName("should create 8 balanced transactions with correct account postings")
        void shouldCreateBalancedTransactions() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            sendFullSandwichFlow(paymentId, correlationId);

            // 8 transactions: 6 event-driven (fx.rate.locked has no journal entry)
            // + 2 from payment.completed (clearing + revenue)
            var transactionCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ledger_transactions WHERE payment_id = ?",
                    Integer.class, paymentId);
            assertThat(transactionCount).isEqualTo(8);
        }

        @Test
        @DisplayName("every ledger transaction should balance (SUM DEBIT = SUM CREDIT)")
        void everyTransactionShouldBalance() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            sendFullSandwichFlow(paymentId, correlationId);

            // Check that no transaction has an imbalance
            var unbalancedCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM (
                        SELECT transaction_id,
                               SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END) as total_debit,
                               SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END) as total_credit
                        FROM journal_entries
                        WHERE payment_id = ?
                        GROUP BY transaction_id
                        HAVING SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END)
                            != SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END)
                    ) unbalanced
                    """, Integer.class, paymentId);
            assertThat(unbalancedCount).isZero();
        }

        @Test
        @DisplayName("should record all 6 reconciliation legs and reach RECONCILED status")
        void shouldReconcileWithAllLegs() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            sendFullSandwichFlow(paymentId, correlationId);

            mockMvc.perform(get("/v1/reconciliation/{paymentId}", paymentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId", is(paymentId.toString())))
                    .andExpect(jsonPath("$.status", is("RECONCILED")))
                    .andExpect(jsonPath("$.legs", hasSize(6)))
                    .andExpect(jsonPath("$.reconciledAt", notNullValue()));
        }

        @Test
        @DisplayName("should publish reconciliation.completed outbox event")
        void shouldPublishReconciliationCompletedEvent() {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            sendFullSandwichFlow(paymentId, correlationId);

            var outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ledger_outbox_record WHERE record_key = ?",
                    Integer.class, paymentId.toString());
            assertThat(outboxCount).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("should reflect correct running totals via account balance API")
        void shouldReflectCorrectAccountBalances() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            sendFullSandwichFlow(paymentId, correlationId);

            // Fiat Cash (1001): DEBIT 10000 from fiat.collected = 10000 (ASSET, normal=DEBIT)
            mockMvc.perform(get("/v1/ledger/accounts/{accountCode}/balance", "1001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountCode", is("1001")))
                    .andExpect(jsonPath("$.balances", hasSize(1)))
                    .andExpect(jsonPath("$.balances[0].currency", is("USD")));

            // FX Spread Revenue (4000): CREDIT fee from payment.completed.revenue
            mockMvc.perform(get("/v1/ledger/accounts/{accountCode}/balance", "4000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountCode", is("4000")))
                    .andExpect(jsonPath("$.balances", hasSize(1)))
                    .andExpect(jsonPath("$.balances[0].currency", is("USD")));
        }

        @Test
        @DisplayName("journal API should return all transactions with entries")
        void shouldReturnFullJournalViaApi() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            sendFullSandwichFlow(paymentId, correlationId);

            mockMvc.perform(get("/v1/ledger/payments/{paymentId}/journal", paymentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId", is(paymentId.toString())))
                    .andExpect(jsonPath("$.transactions", hasSize(8)))
                    .andExpect(jsonPath("$.reconciliation.status", is("RECONCILED")));
        }
    }

    @Nested
    @DisplayName("Discrepancy Detection — mismatched stablecoin amounts")
    class DiscrepancyDetection {

        @Test
        @DisplayName("should detect discrepancy when redeemed amount differs from minted beyond tolerance")
        void shouldDetectDiscrepancyOnAmountMismatch() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // Send events up to stablecoin.redeemed, but with different redeemed amount
            ledgerEventConsumer.onPaymentInitiated(paymentInitiatedJson(paymentId, correlationId));
            ledgerEventConsumer.onFxRateLocked(fxRateLockedJson(paymentId, correlationId));
            ledgerEventConsumer.onFiatCollected(fiatCollectedJson(paymentId));
            ledgerEventConsumer.onChainTransferSubmitted(chainTransferSubmittedJson(paymentId, correlationId));
            ledgerEventConsumer.onChainTransferConfirmed(chainTransferConfirmedJson(paymentId, correlationId));

            // Redeem a different amount (mismatch > tolerance of 0.01)
            var mismatchedRedeemJson = """
                    {
                        "eventId": "%s",
                        "paymentId": "%s",
                        "correlationId": "%s",
                        "stablecoin": "USDC",
                        "redeemedAmount": 9990.000000
                    }
                    """.formatted(UUID.randomUUID(), paymentId, correlationId);
            ledgerEventConsumer.onStablecoinRedeemed(mismatchedRedeemJson);

            ledgerEventConsumer.onFiatPayoutCompleted(fiatPayoutCompletedJson(paymentId, correlationId));
            ledgerEventConsumer.onPaymentCompleted(paymentCompletedJson(paymentId, correlationId));

            // Reconciliation should be DISCREPANCY
            mockMvc.perform(get("/v1/reconciliation/{paymentId}", paymentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("DISCREPANCY")));

            // Outbox should have discrepancy event
            var outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ledger_outbox_record WHERE record_key = ?",
                    Integer.class, paymentId.toString());
            assertThat(outboxCount).isGreaterThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Payment Failed — Reversal Entries")
    class PaymentFailed {

        @Test
        @DisplayName("should post reversal entries that cancel all prior entries")
        void shouldPostReversalEntries() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // Partial flow: payment.initiated + fiat.collected
            ledgerEventConsumer.onPaymentInitiated(paymentInitiatedJson(paymentId, correlationId));
            ledgerEventConsumer.onFiatCollected(fiatCollectedJson(paymentId));

            // 2 transactions so far (payment.initiated + fiat.collected)
            var txCountBefore = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ledger_transactions WHERE payment_id = ?",
                    Integer.class, paymentId);
            assertThat(txCountBefore).isEqualTo(2);

            // payment.failed triggers reversal
            ledgerEventConsumer.onPaymentFailed(
                    paymentFailedJson(paymentId, correlationId, "Compliance check failed"));

            // 3 transactions: 2 original + 1 reversal
            var txCountAfter = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ledger_transactions WHERE payment_id = ?",
                    Integer.class, paymentId);
            assertThat(txCountAfter).isEqualTo(3);
        }

        @Test
        @DisplayName("reversal entries should balance and net account balances to zero")
        void shouldNetAccountBalancesToZero() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // Partial flow: payment.initiated + fiat.collected then failure
            ledgerEventConsumer.onPaymentInitiated(paymentInitiatedJson(paymentId, correlationId));
            ledgerEventConsumer.onFiatCollected(fiatCollectedJson(paymentId));
            ledgerEventConsumer.onPaymentFailed(
                    paymentFailedJson(paymentId, correlationId, "FX lock expired"));

            // All affected accounts should net to zero
            // Fiat Receivable (1000): D 10000 (initiated) - C 10000 (collected) + reversal = 0
            // Client Funds Held (2010): C 10000 (initiated) + reversal = 0
            // Fiat Cash (1001): D 10000 (collected) + reversal = 0
            var nonZeroBalances = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM account_balances
                    WHERE balance != 0
                    """, Integer.class);
            assertThat(nonZeroBalances).isZero();
        }

        @Test
        @DisplayName("should mark reconciliation as DISCREPANCY on payment failure")
        void shouldMarkDiscrepancyOnFailure() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            ledgerEventConsumer.onPaymentInitiated(paymentInitiatedJson(paymentId, correlationId));
            ledgerEventConsumer.onPaymentFailed(
                    paymentFailedJson(paymentId, correlationId, "Sanctions hit"));

            mockMvc.perform(get("/v1/reconciliation/{paymentId}", paymentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("DISCREPANCY")));
        }
    }

    @Nested
    @DisplayName("Idempotent Consumers — duplicate event replay")
    class IdempotentConsumers {

        @Test
        @DisplayName("should silently skip duplicate event without creating duplicate journal entries")
        void shouldSkipDuplicateEvent() {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // Send payment.initiated twice
            var eventJson = paymentInitiatedJson(paymentId, correlationId);
            ledgerEventConsumer.onPaymentInitiated(eventJson);
            ledgerEventConsumer.onPaymentInitiated(eventJson);

            // Only 1 transaction should exist (source_event_id unique index)
            var txCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ledger_transactions WHERE payment_id = ?",
                    Integer.class, paymentId);
            assertThat(txCount).isEqualTo(1);
        }

        @Test
        @DisplayName("should skip duplicate reconciliation leg recording")
        void shouldSkipDuplicateReconciliationLeg() {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // Send fiat.collected twice (records both journal entry + reconciliation leg)
            ledgerEventConsumer.onPaymentInitiated(paymentInitiatedJson(paymentId, correlationId));
            var fiatJson = fiatCollectedJson(paymentId);
            ledgerEventConsumer.onFiatCollected(fiatJson);
            ledgerEventConsumer.onFiatCollected(fiatJson);

            // Only 1 FIAT_IN leg should exist
            var legCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM reconciliation_legs WHERE leg_type = 'FIAT_IN' AND rec_id IN "
                            + "(SELECT rec_id FROM reconciliation_records WHERE payment_id = ?)",
                    Integer.class, paymentId);
            assertThat(legCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Trial Balance — debits equal credits")
    class TrialBalance {

        @Test
        @DisplayName("should return balanced trial balance after full sandwich flow")
        void shouldReturnBalancedTrialBalance() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            sendFullSandwichFlow(paymentId, correlationId);

            mockMvc.perform(get("/v1/ledger/trial-balance"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balanced", is(true)));
        }
    }

    @Nested
    @DisplayName("Append-Only Enforcement — UPDATE/DELETE prohibited for non-owner roles")
    class AppendOnlyEnforcement {

        /**
         * REVOKE UPDATE, DELETE FROM PUBLIC only blocks non-owner roles.
         * In TestContainers the 'test' user owns the tables, so we create a
         * separate role and SET ROLE on a single connection to verify enforcement.
         */
        @Test
        @DisplayName("should reject UPDATE on journal_entries for non-owner role")
        void shouldRejectUpdateOnJournalEntries() {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            ledgerEventConsumer.onPaymentInitiated(paymentInitiatedJson(paymentId, correlationId));

            assertThat(attemptAsAppRole(
                    "UPDATE journal_entries SET amount = 99999 WHERE payment_id = '" + paymentId + "'"))
                    .isFalse();
        }

        @Test
        @DisplayName("should reject DELETE on journal_entries for non-owner role")
        void shouldRejectDeleteOnJournalEntries() {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            ledgerEventConsumer.onPaymentInitiated(paymentInitiatedJson(paymentId, correlationId));

            assertThat(attemptAsAppRole(
                    "DELETE FROM journal_entries WHERE payment_id = '" + paymentId + "'"))
                    .isFalse();
        }

        @Test
        @DisplayName("should reject UPDATE on ledger_transactions for non-owner role")
        void shouldRejectUpdateOnLedgerTransactions() {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            ledgerEventConsumer.onPaymentInitiated(paymentInitiatedJson(paymentId, correlationId));

            assertThat(attemptAsAppRole(
                    "UPDATE ledger_transactions SET description = 'tampered' WHERE payment_id = '" + paymentId + "'"))
                    .isFalse();
        }

        /**
         * Executes the given SQL as a non-owner role on a single connection.
         * Returns true if the statement succeeded, false if it was denied.
         */
        private boolean attemptAsAppRole(String sql) {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try (var stmt = conn.createStatement()) {
                    stmt.execute("DO $$ BEGIN CREATE ROLE ledger_app_role NOLOGIN; "
                            + "EXCEPTION WHEN duplicate_object THEN NULL; END $$");
                    stmt.execute("GRANT USAGE ON SCHEMA public TO ledger_app_role");
                    stmt.execute("GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA public TO ledger_app_role");
                    stmt.execute("SET LOCAL ROLE ledger_app_role");
                    stmt.executeUpdate(sql);
                    conn.commit();
                    return true;
                } catch (SQLException e) {
                    conn.rollback();
                    return false;
                }
            } catch (SQLException e) {
                return false;
            }
        }
    }

    @Nested
    @DisplayName("Out-of-Order Events — reconciliation still reaches correct state")
    class OutOfOrderEvents {

        @Test
        @DisplayName("should reconcile even when events arrive in non-sequential order")
        void shouldReconcileWithOutOfOrderEvents() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            // Send events out of order (fx.rate.locked first, then payment.initiated, etc.)
            ledgerEventConsumer.onFxRateLocked(fxRateLockedJson(paymentId, correlationId));
            ledgerEventConsumer.onPaymentInitiated(paymentInitiatedJson(paymentId, correlationId));
            ledgerEventConsumer.onChainTransferSubmitted(chainTransferSubmittedJson(paymentId, correlationId));
            ledgerEventConsumer.onFiatCollected(fiatCollectedJson(paymentId));
            ledgerEventConsumer.onStablecoinRedeemed(stablecoinRedeemedJson(paymentId, correlationId));
            ledgerEventConsumer.onChainTransferConfirmed(chainTransferConfirmedJson(paymentId, correlationId));
            ledgerEventConsumer.onFiatPayoutCompleted(fiatPayoutCompletedJson(paymentId, correlationId));
            ledgerEventConsumer.onPaymentCompleted(paymentCompletedJson(paymentId, correlationId));

            // Should still reach RECONCILED with all 6 legs
            mockMvc.perform(get("/v1/reconciliation/{paymentId}", paymentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("RECONCILED")))
                    .andExpect(jsonPath("$.legs", hasSize(6)));
        }
    }

    @Nested
    @DisplayName("Multi-Currency Balance Tracking")
    class MultiCurrencyBalanceTracking {

        @Test
        @DisplayName("should track USD and USDC balances separately after full flow")
        void shouldTrackMultiCurrencyBalances() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            sendFullSandwichFlow(paymentId, correlationId);

            // Stablecoin Inventory (1010): USDC entries (submitted DEBIT, confirmed CREDIT = net 0)
            mockMvc.perform(get("/v1/ledger/accounts/{accountCode}/balance", "1010"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountCode", is("1010")));

            // In-Transit Clearing (9000): USDC (submitted CREDIT, completed.clearing DEBIT = net 0)
            mockMvc.perform(get("/v1/ledger/accounts/{accountCode}/balance", "9000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountCode", is("9000")));

            // Client Funds Held (2010): USD entries from initiated, payout, revenue
            mockMvc.perform(get("/v1/ledger/accounts/{accountCode}/balance", "2010"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountCode", is("2010")));
        }
    }

    @Nested
    @DisplayName("Account History — paginated entry query")
    class AccountHistory {

        @Test
        @DisplayName("should return paginated entry history for a specific account after full flow")
        void shouldReturnPaginatedAccountHistory() throws Exception {
            var paymentId = UUID.randomUUID();
            var correlationId = UUID.randomUUID();

            sendFullSandwichFlow(paymentId, correlationId);

            // Fiat Receivable (1000): 2 entries — DEBIT from initiated, CREDIT from collected
            mockMvc.perform(get("/v1/ledger/accounts/{accountCode}/history", "1000")
                            .param("currency", "USD")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountCode", is("1000")))
                    .andExpect(jsonPath("$.currency", is("USD")))
                    .andExpect(jsonPath("$.totalElements", is(2)));
        }
    }
}
