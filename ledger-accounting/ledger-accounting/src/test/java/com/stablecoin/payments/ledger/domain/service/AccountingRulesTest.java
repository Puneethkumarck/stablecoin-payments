package com.stablecoin.payments.ledger.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.stablecoin.payments.ledger.domain.model.EntryType.CREDIT;
import static com.stablecoin.payments.ledger.domain.model.EntryType.DEBIT;
import static com.stablecoin.payments.ledger.domain.service.AccountingRules.CLIENT_FUNDS_HELD;
import static com.stablecoin.payments.ledger.domain.service.AccountingRules.FIAT_CASH;
import static com.stablecoin.payments.ledger.domain.service.AccountingRules.FIAT_PAYABLE;
import static com.stablecoin.payments.ledger.domain.service.AccountingRules.FIAT_RECEIVABLE;
import static com.stablecoin.payments.ledger.domain.service.AccountingRules.FX_SPREAD_REVENUE;
import static com.stablecoin.payments.ledger.domain.service.AccountingRules.IN_TRANSIT_CLEARING;
import static com.stablecoin.payments.ledger.domain.service.AccountingRules.OFF_RAMP_RECEIVABLE;
import static com.stablecoin.payments.ledger.domain.service.AccountingRules.STABLECOIN_INVENTORY;
import static com.stablecoin.payments.ledger.domain.service.AccountingRules.STABLECOIN_REDEEMED;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.CORRELATION_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.PAYMENT_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.SOURCE_EVENT_ID;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AccountingRules")
class AccountingRulesTest {

    private static final BigDecimal USD_AMOUNT = new BigDecimal("10000.00");
    private static final BigDecimal USDC_AMOUNT = new BigDecimal("10000.000000");
    private static final BigDecimal EUR_AMOUNT = new BigDecimal("9200.00");
    private static final BigDecimal FEE_AMOUNT = new BigDecimal("30.00");

    @Nested
    @DisplayName("payment.initiated")
    class PaymentInitiated {

        @Test
        @DisplayName("DEBIT 1000 Fiat Receivable / CREDIT 2010 Client Funds Held")
        void mapsCorrectAccounts() {
            var request = AccountingRules.paymentInitiated(
                    PAYMENT_ID, CORRELATION_ID, SOURCE_EVENT_ID, USD_AMOUNT, "USD"
            );

            assertThat(request.entries()).hasSize(2);
            assertThat(request.entries().getFirst().entryType()).isEqualTo(DEBIT);
            assertThat(request.entries().getFirst().accountCode()).isEqualTo(FIAT_RECEIVABLE);
            assertThat(request.entries().getLast().entryType()).isEqualTo(CREDIT);
            assertThat(request.entries().getLast().accountCode()).isEqualTo(CLIENT_FUNDS_HELD);
        }

        @Test
        @DisplayName("source event is payment.initiated")
        void setsSourceEvent() {
            var request = AccountingRules.paymentInitiated(
                    PAYMENT_ID, CORRELATION_ID, SOURCE_EVENT_ID, USD_AMOUNT, "USD"
            );

            assertThat(request.sourceEvent()).isEqualTo("payment.initiated");
        }

        @Test
        @DisplayName("amounts match for both entries")
        void amountsMatch() {
            var request = AccountingRules.paymentInitiated(
                    PAYMENT_ID, CORRELATION_ID, SOURCE_EVENT_ID, USD_AMOUNT, "USD"
            );

            assertThat(request.entries().getFirst().amount()).isEqualByComparingTo(USD_AMOUNT);
            assertThat(request.entries().getLast().amount()).isEqualByComparingTo(USD_AMOUNT);
        }
    }

    @Nested
    @DisplayName("fiat.collected")
    class FiatCollected {

        @Test
        @DisplayName("DEBIT 1001 Fiat Cash / CREDIT 1000 Fiat Receivable")
        void mapsCorrectAccounts() {
            var request = AccountingRules.fiatCollected(
                    PAYMENT_ID, CORRELATION_ID, SOURCE_EVENT_ID, USD_AMOUNT, "USD"
            );

            assertThat(request.entries().getFirst().entryType()).isEqualTo(DEBIT);
            assertThat(request.entries().getFirst().accountCode()).isEqualTo(FIAT_CASH);
            assertThat(request.entries().getLast().entryType()).isEqualTo(CREDIT);
            assertThat(request.entries().getLast().accountCode()).isEqualTo(FIAT_RECEIVABLE);
        }
    }

    @Nested
    @DisplayName("chain.transfer.submitted")
    class ChainTransferSubmitted {

        @Test
        @DisplayName("DEBIT 1010 Stablecoin Inventory / CREDIT 9000 In-Transit Clearing")
        void mapsCorrectAccounts() {
            var request = AccountingRules.chainTransferSubmitted(
                    PAYMENT_ID, CORRELATION_ID, SOURCE_EVENT_ID, USDC_AMOUNT, "USDC"
            );

            assertThat(request.entries().getFirst().entryType()).isEqualTo(DEBIT);
            assertThat(request.entries().getFirst().accountCode()).isEqualTo(STABLECOIN_INVENTORY);
            assertThat(request.entries().getLast().entryType()).isEqualTo(CREDIT);
            assertThat(request.entries().getLast().accountCode()).isEqualTo(IN_TRANSIT_CLEARING);
        }

        @Test
        @DisplayName("uses stablecoin currency")
        void usesStablecoinCurrency() {
            var request = AccountingRules.chainTransferSubmitted(
                    PAYMENT_ID, CORRELATION_ID, SOURCE_EVENT_ID, USDC_AMOUNT, "USDC"
            );

            assertThat(request.entries().getFirst().currency()).isEqualTo("USDC");
        }
    }

    @Nested
    @DisplayName("chain.transfer.confirmed")
    class ChainTransferConfirmed {

        @Test
        @DisplayName("DEBIT 1020 Off-Ramp Receivable / CREDIT 1010 Stablecoin Inventory")
        void mapsCorrectAccounts() {
            var request = AccountingRules.chainTransferConfirmed(
                    PAYMENT_ID, CORRELATION_ID, SOURCE_EVENT_ID, USDC_AMOUNT, "USDC"
            );

            assertThat(request.entries().getFirst().entryType()).isEqualTo(DEBIT);
            assertThat(request.entries().getFirst().accountCode()).isEqualTo(OFF_RAMP_RECEIVABLE);
            assertThat(request.entries().getLast().entryType()).isEqualTo(CREDIT);
            assertThat(request.entries().getLast().accountCode()).isEqualTo(STABLECOIN_INVENTORY);
        }
    }

    @Nested
    @DisplayName("stablecoin.redeemed")
    class StablecoinRedeemed {

        @Test
        @DisplayName("DEBIT 1030 Stablecoin Redeemed / CREDIT 1020 Off-Ramp Receivable")
        void mapsCorrectAccounts() {
            var request = AccountingRules.stablecoinRedeemed(
                    PAYMENT_ID, CORRELATION_ID, SOURCE_EVENT_ID, USDC_AMOUNT, "USDC"
            );

            assertThat(request.entries().getFirst().entryType()).isEqualTo(DEBIT);
            assertThat(request.entries().getFirst().accountCode()).isEqualTo(STABLECOIN_REDEEMED);
            assertThat(request.entries().getLast().entryType()).isEqualTo(CREDIT);
            assertThat(request.entries().getLast().accountCode()).isEqualTo(OFF_RAMP_RECEIVABLE);
        }
    }

    @Nested
    @DisplayName("fiat.payout.completed")
    class FiatPayoutCompleted {

        @Test
        @DisplayName("DEBIT 2010 Client Funds Held / CREDIT 2000 Fiat Payable")
        void mapsCorrectAccounts() {
            var request = AccountingRules.fiatPayoutCompleted(
                    PAYMENT_ID, CORRELATION_ID, SOURCE_EVENT_ID, EUR_AMOUNT, "EUR"
            );

            assertThat(request.entries().getFirst().entryType()).isEqualTo(DEBIT);
            assertThat(request.entries().getFirst().accountCode()).isEqualTo(CLIENT_FUNDS_HELD);
            assertThat(request.entries().getLast().entryType()).isEqualTo(CREDIT);
            assertThat(request.entries().getLast().accountCode()).isEqualTo(FIAT_PAYABLE);
        }

        @Test
        @DisplayName("uses target currency")
        void usesTargetCurrency() {
            var request = AccountingRules.fiatPayoutCompleted(
                    PAYMENT_ID, CORRELATION_ID, SOURCE_EVENT_ID, EUR_AMOUNT, "EUR"
            );

            assertThat(request.entries().getFirst().currency()).isEqualTo("EUR");
        }
    }

    @Nested
    @DisplayName("payment.completed — clearing")
    class PaymentCompletedClearing {

        @Test
        @DisplayName("DEBIT 9000 In-Transit Clearing / CREDIT 1030 Stablecoin Redeemed")
        void mapsCorrectAccounts() {
            var request = AccountingRules.paymentCompletedClearing(
                    PAYMENT_ID, CORRELATION_ID, SOURCE_EVENT_ID, USDC_AMOUNT, "USDC"
            );

            assertThat(request.entries().getFirst().entryType()).isEqualTo(DEBIT);
            assertThat(request.entries().getFirst().accountCode()).isEqualTo(IN_TRANSIT_CLEARING);
            assertThat(request.entries().getLast().entryType()).isEqualTo(CREDIT);
            assertThat(request.entries().getLast().accountCode()).isEqualTo(STABLECOIN_REDEEMED);
        }
    }

    @Nested
    @DisplayName("payment.completed — revenue")
    class PaymentCompletedRevenue {

        @Test
        @DisplayName("DEBIT 2010 Client Funds Held / CREDIT 4000 FX Spread Revenue")
        void mapsCorrectAccounts() {
            var request = AccountingRules.paymentCompletedRevenue(
                    PAYMENT_ID, CORRELATION_ID, SOURCE_EVENT_ID, FEE_AMOUNT, "USD"
            );

            assertThat(request.entries().getFirst().entryType()).isEqualTo(DEBIT);
            assertThat(request.entries().getFirst().accountCode()).isEqualTo(CLIENT_FUNDS_HELD);
            assertThat(request.entries().getLast().entryType()).isEqualTo(CREDIT);
            assertThat(request.entries().getLast().accountCode()).isEqualTo(FX_SPREAD_REVENUE);
        }

        @Test
        @DisplayName("uses fee amount")
        void usesFeeAmount() {
            var request = AccountingRules.paymentCompletedRevenue(
                    PAYMENT_ID, CORRELATION_ID, SOURCE_EVENT_ID, FEE_AMOUNT, "USD"
            );

            assertThat(request.entries().getFirst().amount()).isEqualByComparingTo(FEE_AMOUNT);
        }
    }

    @Nested
    @DisplayName("payment.failed — reversal entries")
    class ReversalEntries {

        @Test
        @DisplayName("swaps DEBIT to CREDIT and vice versa")
        void swapsEntryTypes() {
            var originals = List.of(
                    new JournalEntryRequest(DEBIT, FIAT_RECEIVABLE, USD_AMOUNT, "USD"),
                    new JournalEntryRequest(CREDIT, CLIENT_FUNDS_HELD, USD_AMOUNT, "USD")
            );

            var reversals = AccountingRules.reversalEntries(originals);

            assertThat(reversals.getFirst().entryType()).isEqualTo(CREDIT);
            assertThat(reversals.getLast().entryType()).isEqualTo(DEBIT);
        }

        @Test
        @DisplayName("preserves account codes")
        void preservesAccountCodes() {
            var originals = List.of(
                    new JournalEntryRequest(DEBIT, FIAT_RECEIVABLE, USD_AMOUNT, "USD"),
                    new JournalEntryRequest(CREDIT, CLIENT_FUNDS_HELD, USD_AMOUNT, "USD")
            );

            var reversals = AccountingRules.reversalEntries(originals);

            assertThat(reversals.getFirst().accountCode()).isEqualTo(FIAT_RECEIVABLE);
            assertThat(reversals.getLast().accountCode()).isEqualTo(CLIENT_FUNDS_HELD);
        }

        @Test
        @DisplayName("preserves amounts")
        void preservesAmounts() {
            var originals = List.of(
                    new JournalEntryRequest(DEBIT, FIAT_RECEIVABLE, USD_AMOUNT, "USD"),
                    new JournalEntryRequest(CREDIT, CLIENT_FUNDS_HELD, USD_AMOUNT, "USD")
            );

            var reversals = AccountingRules.reversalEntries(originals);

            assertThat(reversals.getFirst().amount()).isEqualByComparingTo(USD_AMOUNT);
        }

        @Test
        @DisplayName("preserves currency")
        void preservesCurrency() {
            var originals = List.of(
                    new JournalEntryRequest(DEBIT, STABLECOIN_INVENTORY, USDC_AMOUNT, "USDC"),
                    new JournalEntryRequest(CREDIT, IN_TRANSIT_CLEARING, USDC_AMOUNT, "USDC")
            );

            var reversals = AccountingRules.reversalEntries(originals);

            assertThat(reversals.getFirst().currency()).isEqualTo("USDC");
        }

        @Test
        @DisplayName("handles multi-entry reversal")
        void handlesMultiEntryReversal() {
            var originals = List.of(
                    new JournalEntryRequest(DEBIT, FIAT_RECEIVABLE, USD_AMOUNT, "USD"),
                    new JournalEntryRequest(CREDIT, CLIENT_FUNDS_HELD, USD_AMOUNT, "USD"),
                    new JournalEntryRequest(DEBIT, FIAT_CASH, USD_AMOUNT, "USD"),
                    new JournalEntryRequest(CREDIT, FIAT_RECEIVABLE, USD_AMOUNT, "USD")
            );

            var reversals = AccountingRules.reversalEntries(originals);

            assertThat(reversals).hasSize(4);
            assertThat(reversals.get(0).entryType()).isEqualTo(CREDIT);
            assertThat(reversals.get(1).entryType()).isEqualTo(DEBIT);
            assertThat(reversals.get(2).entryType()).isEqualTo(CREDIT);
            assertThat(reversals.get(3).entryType()).isEqualTo(DEBIT);
        }
    }

    @Nested
    @DisplayName("chart of accounts constants")
    class ChartOfAccountsConstants {

        @Test
        @DisplayName("all account codes are defined")
        void allAccountCodesDefined() {
            assertThat(FIAT_RECEIVABLE).isEqualTo("1000");
            assertThat(FIAT_CASH).isEqualTo("1001");
            assertThat(STABLECOIN_INVENTORY).isEqualTo("1010");
            assertThat(OFF_RAMP_RECEIVABLE).isEqualTo("1020");
            assertThat(STABLECOIN_REDEEMED).isEqualTo("1030");
            assertThat(FIAT_PAYABLE).isEqualTo("2000");
            assertThat(CLIENT_FUNDS_HELD).isEqualTo("2010");
            assertThat(FX_SPREAD_REVENUE).isEqualTo("4000");
            assertThat(IN_TRANSIT_CLEARING).isEqualTo("9000");
        }
    }
}
