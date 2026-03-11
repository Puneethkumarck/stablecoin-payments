package com.stablecoin.payments.ledger.fixtures;

import com.stablecoin.payments.ledger.domain.model.Account;
import com.stablecoin.payments.ledger.domain.model.AccountBalance;
import com.stablecoin.payments.ledger.domain.model.AccountType;
import com.stablecoin.payments.ledger.domain.model.AuditEvent;
import com.stablecoin.payments.ledger.domain.model.Currency;
import com.stablecoin.payments.ledger.domain.model.EntryType;
import com.stablecoin.payments.ledger.domain.model.JournalEntry;
import com.stablecoin.payments.ledger.domain.model.LedgerTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LedgerFixtures {

    public static final UUID PAYMENT_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    public static final UUID CORRELATION_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    public static final UUID SOURCE_EVENT_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    public static final UUID TRANSACTION_ID = UUID.fromString("d4e5f6a7-b8c9-0123-defa-234567890123");
    public static final UUID ENTRY_ID_1 = UUID.fromString("e5f6a7b8-c9d0-1234-efab-345678901234");
    public static final UUID ENTRY_ID_2 = UUID.fromString("f6a7b8c9-d0e1-2345-fabc-456789012345");
    public static final UUID AUDIT_ID = UUID.fromString("a7b8c9d0-e1f2-3456-abcd-567890123456");

    public static final String FIAT_RECEIVABLE = "1000";
    public static final String FIAT_CASH = "1001";
    public static final String STABLECOIN_INVENTORY = "1010";
    public static final String OFF_RAMP_RECEIVABLE = "1020";
    public static final String STABLECOIN_REDEEMED_ACCT = "1030";
    public static final String FIAT_PAYABLE = "2000";
    public static final String CLIENT_FUNDS_HELD = "2010";
    public static final String FX_SPREAD_REVENUE = "4000";
    public static final String IN_TRANSIT_CLEARING = "9000";

    public static final Instant NOW = Instant.parse("2026-03-11T10:00:00Z");

    private LedgerFixtures() {
    }

    public static JournalEntry aDebitEntry(String accountCode, BigDecimal amount, String currency) {
        return new JournalEntry(
                UUID.randomUUID(),
                TRANSACTION_ID,
                PAYMENT_ID,
                CORRELATION_ID,
                1,
                EntryType.DEBIT,
                accountCode,
                amount,
                currency,
                amount,
                1L,
                "payment.initiated",
                SOURCE_EVENT_ID,
                NOW
        );
    }

    public static JournalEntry aCreditEntry(String accountCode, BigDecimal amount, String currency) {
        return new JournalEntry(
                UUID.randomUUID(),
                TRANSACTION_ID,
                PAYMENT_ID,
                CORRELATION_ID,
                2,
                EntryType.CREDIT,
                accountCode,
                amount,
                currency,
                BigDecimal.ZERO,
                1L,
                "payment.initiated",
                SOURCE_EVENT_ID,
                NOW
        );
    }

    public static List<JournalEntry> aBalancedEntryPair(BigDecimal amount, String currency) {
        return List.of(
                aDebitEntry(FIAT_RECEIVABLE, amount, currency),
                aCreditEntry(CLIENT_FUNDS_HELD, amount, currency)
        );
    }

    public static List<JournalEntry> aBalancedEntryPair(UUID transactionId, UUID sourceEventId, BigDecimal amount, String currency) {
        return List.of(
                new JournalEntry(UUID.randomUUID(), transactionId, PAYMENT_ID, CORRELATION_ID,
                        1, EntryType.DEBIT, FIAT_RECEIVABLE, amount, currency, amount, 1L,
                        "payment.initiated", sourceEventId, NOW),
                new JournalEntry(UUID.randomUUID(), transactionId, PAYMENT_ID, CORRELATION_ID,
                        2, EntryType.CREDIT, CLIENT_FUNDS_HELD, amount, currency, BigDecimal.ZERO, 1L,
                        "payment.initiated", sourceEventId, NOW)
        );
    }

    public static LedgerTransaction aBalancedTransaction() {
        BigDecimal amount = new BigDecimal("10000.00");
        return new LedgerTransaction(
                TRANSACTION_ID,
                PAYMENT_ID,
                CORRELATION_ID,
                "payment.initiated",
                SOURCE_EVENT_ID,
                "Payment initiated, receivable recognized",
                aBalancedEntryPair(amount, "USD"),
                NOW
        );
    }

    public static LedgerTransaction aTransaction(UUID paymentId, UUID sourceEventId, Instant createdAt) {
        var transactionId = UUID.randomUUID();
        return new LedgerTransaction(
                transactionId,
                paymentId,
                CORRELATION_ID,
                "payment.initiated",
                sourceEventId,
                "Test transaction",
                aBalancedEntryPair(transactionId, sourceEventId, new BigDecimal("10000.00"), "USD"),
                createdAt
        );
    }

    public static LedgerTransaction aTransaction(UUID paymentId, UUID sourceEventId) {
        return aTransaction(paymentId, sourceEventId, Instant.now());
    }

    public static AccountBalance aZeroBalance(String accountCode, String currency) {
        return new AccountBalance(
                accountCode,
                currency,
                BigDecimal.ZERO,
                0L,
                null,
                NOW
        );
    }

    public static AccountBalance aBalance(String accountCode, String currency, BigDecimal balance, long version) {
        return new AccountBalance(
                accountCode,
                currency,
                balance,
                version,
                ENTRY_ID_1,
                NOW
        );
    }

    public static Account anAssetAccount() {
        return new Account(FIAT_RECEIVABLE, "Fiat Receivable", AccountType.ASSET, EntryType.DEBIT, true, NOW);
    }

    public static Account aLiabilityAccount() {
        return new Account(CLIENT_FUNDS_HELD, "Client Funds Held", AccountType.LIABILITY, EntryType.CREDIT, true, NOW);
    }

    public static Account aRevenueAccount() {
        return new Account(FX_SPREAD_REVENUE, "FX Spread Revenue", AccountType.REVENUE, EntryType.CREDIT, true, NOW);
    }

    public static Account aClearingAccount() {
        return new Account(IN_TRANSIT_CLEARING, "In-Transit Clearing", AccountType.CLEARING, EntryType.DEBIT, true, NOW);
    }

    public static Currency usd() {
        return new Currency("USD", 2, true, NOW);
    }

    public static Currency eur() {
        return new Currency("EUR", 2, true, NOW);
    }

    public static Currency usdc() {
        return new Currency("USDC", 6, true, NOW);
    }

    public static Currency dai() {
        return new Currency("DAI", 18, true, NOW);
    }

    public static Currency jpy() {
        return new Currency("JPY", 0, true, NOW);
    }

    public static Currency bhd() {
        return new Currency("BHD", 3, true, NOW);
    }

    public static AuditEvent anAuditEvent() {
        return new AuditEvent(
                AUDIT_ID,
                CORRELATION_ID,
                PAYMENT_ID,
                "ledger-accounting",
                "journal.posted",
                "{\"transactionId\": \"" + TRANSACTION_ID + "\"}",
                "system",
                NOW,
                NOW
        );
    }
}
