package com.stablecoin.payments.ledger.domain.service;


import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.stablecoin.payments.ledger.domain.model.EntryType.CREDIT;
import static com.stablecoin.payments.ledger.domain.model.EntryType.DEBIT;

/**
 * Maps payment lifecycle events to balanced journal entry templates.
 * Each method returns a {@link TransactionRequest} with the correct debit/credit pairs
 * according to the chart of accounts.
 *
 * <p>This is a pure domain service — no dependencies on Spring or infrastructure.
 */
public final class AccountingRules {

    // Chart of accounts codes
    public static final String FIAT_RECEIVABLE = "1000";
    public static final String FIAT_CASH = "1001";
    public static final String STABLECOIN_INVENTORY = "1010";
    public static final String OFF_RAMP_RECEIVABLE = "1020";
    public static final String STABLECOIN_REDEEMED = "1030";
    public static final String FIAT_PAYABLE = "2000";
    public static final String CLIENT_FUNDS_HELD = "2010";
    public static final String FX_SPREAD_REVENUE = "4000";
    public static final String TRANSACTION_FEE_REVENUE = "4001";
    public static final String IN_TRANSIT_CLEARING = "9000";

    private AccountingRules() {
    }

    /**
     * payment.initiated → DEBIT 1000 Fiat Receivable / CREDIT 2010 Client Funds Held
     */
    public static TransactionRequest paymentInitiated(
            UUID paymentId, UUID correlationId, UUID sourceEventId,
            BigDecimal amount, String currency
    ) {
        return new TransactionRequest(
                paymentId, correlationId, "payment.initiated", sourceEventId,
                "Payment initiated, receivable recognized",
                List.of(
                        new JournalEntryRequest(DEBIT, FIAT_RECEIVABLE, amount, currency),
                        new JournalEntryRequest(CREDIT, CLIENT_FUNDS_HELD, amount, currency)
                )
        );
    }

    /**
     * fiat.collected → DEBIT 1001 Fiat Cash / CREDIT 1000 Fiat Receivable
     */
    public static TransactionRequest fiatCollected(
            UUID paymentId, UUID correlationId, UUID sourceEventId,
            BigDecimal amount, String currency
    ) {
        return new TransactionRequest(
                paymentId, correlationId, "fiat.collected", sourceEventId,
                "Sender funds collected via payment rail",
                List.of(
                        new JournalEntryRequest(DEBIT, FIAT_CASH, amount, currency),
                        new JournalEntryRequest(CREDIT, FIAT_RECEIVABLE, amount, currency)
                )
        );
    }

    /**
     * chain.transfer.submitted → DEBIT 1010 Stablecoin Inventory / CREDIT 9000 In-Transit Clearing
     */
    public static TransactionRequest chainTransferSubmitted(
            UUID paymentId, UUID correlationId, UUID sourceEventId,
            BigDecimal amount, String stablecoin
    ) {
        return new TransactionRequest(
                paymentId, correlationId, "chain.transfer.submitted", sourceEventId,
                "Stablecoin transfer submitted on-chain",
                List.of(
                        new JournalEntryRequest(DEBIT, STABLECOIN_INVENTORY, amount, stablecoin),
                        new JournalEntryRequest(CREDIT, IN_TRANSIT_CLEARING, amount, stablecoin)
                )
        );
    }

    /**
     * chain.transfer.confirmed → DEBIT 1020 Off-Ramp Receivable / CREDIT 1010 Stablecoin Inventory
     */
    public static TransactionRequest chainTransferConfirmed(
            UUID paymentId, UUID correlationId, UUID sourceEventId,
            BigDecimal amount, String stablecoin
    ) {
        return new TransactionRequest(
                paymentId, correlationId, "chain.transfer.confirmed", sourceEventId,
                "On-chain transfer confirmed",
                List.of(
                        new JournalEntryRequest(DEBIT, OFF_RAMP_RECEIVABLE, amount, stablecoin),
                        new JournalEntryRequest(CREDIT, STABLECOIN_INVENTORY, amount, stablecoin)
                )
        );
    }

    /**
     * stablecoin.redeemed → DEBIT 1030 Stablecoin Redeemed / CREDIT 1020 Off-Ramp Receivable
     */
    public static TransactionRequest stablecoinRedeemed(
            UUID paymentId, UUID correlationId, UUID sourceEventId,
            BigDecimal amount, String stablecoin
    ) {
        return new TransactionRequest(
                paymentId, correlationId, "stablecoin.redeemed", sourceEventId,
                "Stablecoin redemption confirmed",
                List.of(
                        new JournalEntryRequest(DEBIT, STABLECOIN_REDEEMED, amount, stablecoin),
                        new JournalEntryRequest(CREDIT, OFF_RAMP_RECEIVABLE, amount, stablecoin)
                )
        );
    }

    /**
     * fiat.payout.completed → DEBIT 2010 Client Funds Held / CREDIT 2000 Fiat Payable
     */
    public static TransactionRequest fiatPayoutCompleted(
            UUID paymentId, UUID correlationId, UUID sourceEventId,
            BigDecimal amount, String currency
    ) {
        return new TransactionRequest(
                paymentId, correlationId, "fiat.payout.completed", sourceEventId,
                "SEPA payout sent to recipient",
                List.of(
                        new JournalEntryRequest(DEBIT, CLIENT_FUNDS_HELD, amount, currency),
                        new JournalEntryRequest(CREDIT, FIAT_PAYABLE, amount, currency)
                )
        );
    }

    /**
     * payment.completed (clearing) → DEBIT 9000 In-Transit Clearing / CREDIT 1030 Stablecoin Redeemed
     */
    public static TransactionRequest paymentCompletedClearing(
            UUID paymentId, UUID correlationId, UUID sourceEventId,
            BigDecimal stablecoinAmount, String stablecoin
    ) {
        return new TransactionRequest(
                paymentId, correlationId, "payment.completed", sourceEventId,
                "Clearing leg closed",
                List.of(
                        new JournalEntryRequest(DEBIT, IN_TRANSIT_CLEARING, stablecoinAmount, stablecoin),
                        new JournalEntryRequest(CREDIT, STABLECOIN_REDEEMED, stablecoinAmount, stablecoin)
                )
        );
    }

    /**
     * payment.completed (revenue) → DEBIT 2010 Client Funds Held / CREDIT 4000 FX Spread Revenue
     */
    public static TransactionRequest paymentCompletedRevenue(
            UUID paymentId, UUID correlationId, UUID sourceEventId,
            BigDecimal feeAmount, String currency
    ) {
        return new TransactionRequest(
                paymentId, correlationId, "payment.completed.revenue", sourceEventId,
                "FX spread revenue recognized",
                List.of(
                        new JournalEntryRequest(DEBIT, CLIENT_FUNDS_HELD, feeAmount, currency),
                        new JournalEntryRequest(CREDIT, FX_SPREAD_REVENUE, feeAmount, currency)
                )
        );
    }

    /**
     * payment.failed (reversal) — creates reversal entries for all existing entries.
     * Swaps DEBIT ↔ CREDIT with same amount/currency/account.
     */
    public static List<JournalEntryRequest> reversalEntries(List<JournalEntryRequest> originalEntries) {
        return originalEntries.stream()
                .map(e -> new JournalEntryRequest(
                        e.entryType().opposite(),
                        e.accountCode(),
                        e.amount(),
                        e.currency()
                ))
                .toList();
    }
}
