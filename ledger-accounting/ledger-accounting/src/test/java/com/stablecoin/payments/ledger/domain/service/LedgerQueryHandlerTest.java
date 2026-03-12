package com.stablecoin.payments.ledger.domain.service;

import com.stablecoin.payments.ledger.domain.exception.AccountNotFoundException;
import com.stablecoin.payments.ledger.domain.exception.JournalNotFoundException;
import com.stablecoin.payments.ledger.domain.exception.ReconciliationNotFoundException;
import com.stablecoin.payments.ledger.domain.model.LedgerTransaction;
import com.stablecoin.payments.ledger.domain.port.AccountBalanceRepository;
import com.stablecoin.payments.ledger.domain.port.AccountRepository;
import com.stablecoin.payments.ledger.domain.port.JournalEntryRepository;
import com.stablecoin.payments.ledger.domain.port.LedgerTransactionRepository;
import com.stablecoin.payments.ledger.domain.port.ReconciliationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.FIAT_RECEIVABLE;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.PAYMENT_ID;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aBalance;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aBalancedTransaction;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aClearingAccount;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aCreditEntry;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aDebitEntry;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aLiabilityAccount;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aRevenueAccount;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.anAssetAccount;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aRecordWithAllRequiredLegs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("LedgerQueryHandler")
class LedgerQueryHandlerTest {

    @Mock
    private LedgerTransactionRepository transactionRepository;
    @Mock
    private JournalEntryRepository entryRepository;
    @Mock
    private AccountBalanceRepository balanceRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private ReconciliationRepository reconciliationRepository;

    @InjectMocks
    private LedgerQueryHandler handler;

    @Nested
    @DisplayName("getPaymentJournal")
    class GetPaymentJournal {

        @Test
        @DisplayName("should return transactions for payment")
        void shouldReturnTransactions() {
            var transaction = aBalancedTransaction();
            given(transactionRepository.findByPaymentId(PAYMENT_ID))
                    .willReturn(List.of(transaction));

            var result = handler.getPaymentJournal(PAYMENT_ID);

            var expected = List.of(transaction);
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("should throw JournalNotFoundException when no transactions exist")
        void shouldThrowWhenNoTransactions() {
            given(transactionRepository.findByPaymentId(PAYMENT_ID))
                    .willReturn(List.of());

            assertThatThrownBy(() -> handler.getPaymentJournal(PAYMENT_ID))
                    .isInstanceOf(JournalNotFoundException.class);
        }

        @Test
        @DisplayName("should return multiple transactions in order")
        void shouldReturnMultipleTransactions() {
            var tx1 = aBalancedTransaction();
            var tx2Id = UUID.randomUUID();
            var sourceEventId2 = UUID.randomUUID();
            var tx2 = new LedgerTransaction(
                    tx2Id, PAYMENT_ID, UUID.randomUUID(),
                    "collection.completed", sourceEventId2,
                    "Collection completed",
                    List.of(
                            aDebitEntry(FIAT_RECEIVABLE, new BigDecimal("5000.00"), "USD"),
                            aCreditEntry("2010", new BigDecimal("5000.00"), "USD")
                    ),
                    java.time.Instant.now()
            );
            given(transactionRepository.findByPaymentId(PAYMENT_ID))
                    .willReturn(List.of(tx1, tx2));

            var result = handler.getPaymentJournal(PAYMENT_ID);

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("getReconciliation")
    class GetReconciliation {

        @Test
        @DisplayName("should return reconciliation record with all legs")
        void shouldReturnRecord() {
            var record = aRecordWithAllRequiredLegs();
            given(reconciliationRepository.findByPaymentId(PAYMENT_ID))
                    .willReturn(Optional.of(record));

            var result = handler.getReconciliation(PAYMENT_ID);

            assertThat(result).usingRecursiveComparison().isEqualTo(record);
        }

        @Test
        @DisplayName("should throw ReconciliationNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            given(reconciliationRepository.findByPaymentId(PAYMENT_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> handler.getReconciliation(PAYMENT_ID))
                    .isInstanceOf(ReconciliationNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getAccountBalance")
    class GetAccountBalance {

        @Test
        @DisplayName("should return account with multi-currency balances")
        void shouldReturnAccountWithBalances() {
            var account = anAssetAccount();
            var usdBalance = aBalance(FIAT_RECEIVABLE, "USD", new BigDecimal("50000.00"), 5L);
            var eurBalance = aBalance(FIAT_RECEIVABLE, "EUR", new BigDecimal("25000.00"), 3L);
            given(accountRepository.findByAccountCode(FIAT_RECEIVABLE))
                    .willReturn(Optional.of(account));
            given(balanceRepository.findByAccountCode(FIAT_RECEIVABLE))
                    .willReturn(List.of(usdBalance, eurBalance));

            var result = handler.getAccountBalance(FIAT_RECEIVABLE);

            var expected = new LedgerQueryHandler.AccountWithBalances(
                    account, List.of(usdBalance, eurBalance));
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("should throw AccountNotFoundException when account not found")
        void shouldThrowWhenAccountNotFound() {
            given(accountRepository.findByAccountCode("9999"))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> handler.getAccountBalance("9999"))
                    .isInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("should return empty balances for account with no entries")
        void shouldReturnEmptyBalances() {
            var account = anAssetAccount();
            given(accountRepository.findByAccountCode(FIAT_RECEIVABLE))
                    .willReturn(Optional.of(account));
            given(balanceRepository.findByAccountCode(FIAT_RECEIVABLE))
                    .willReturn(List.of());

            var result = handler.getAccountBalance(FIAT_RECEIVABLE);

            assertThat(result.balances()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAccountHistory")
    class GetAccountHistory {

        @Test
        @DisplayName("should return paginated entries")
        void shouldReturnPaginatedEntries() {
            var account = anAssetAccount();
            var entry = aDebitEntry(FIAT_RECEIVABLE, new BigDecimal("10000.00"), "USD");
            given(accountRepository.findByAccountCode(FIAT_RECEIVABLE))
                    .willReturn(Optional.of(account));
            given(entryRepository.findByAccountCodeAndCurrency(FIAT_RECEIVABLE, "USD", 0, 20))
                    .willReturn(List.of(entry));
            given(entryRepository.countByAccountCodeAndCurrency(FIAT_RECEIVABLE, "USD"))
                    .willReturn(1L);

            var result = handler.getAccountHistory(FIAT_RECEIVABLE, "USD", 0, 20);

            var expected = new LedgerQueryHandler.AccountHistory(
                    FIAT_RECEIVABLE, "USD", List.of(entry), 0, 20, 1L);
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("should throw AccountNotFoundException for invalid account")
        void shouldThrowWhenAccountNotFound() {
            given(accountRepository.findByAccountCode("9999"))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> handler.getAccountHistory("9999", "USD", 0, 20))
                    .isInstanceOf(AccountNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getTrialBalance")
    class GetTrialBalance {

        @Test
        @DisplayName("should return balanced trial balance")
        void shouldReturnBalancedTrialBalance() {
            var assetAccount = anAssetAccount();
            var liabilityAccount = aLiabilityAccount();
            given(accountRepository.findByIsActive(true))
                    .willReturn(List.of(assetAccount, liabilityAccount));

            var assetBalance = aBalance(FIAT_RECEIVABLE, "USD", new BigDecimal("50000.00"), 5L);
            var liabilityBalance = aBalance("2010", "USD", new BigDecimal("50000.00"), 5L);
            given(balanceRepository.findAll())
                    .willReturn(List.of(assetBalance, liabilityBalance));

            var result = handler.getTrialBalance();

            assertThat(result.balanced()).isTrue();
            assertThat(result.totalDebits()).isEqualByComparingTo(new BigDecimal("50000.00"));
            assertThat(result.totalCredits()).isEqualByComparingTo(new BigDecimal("50000.00"));
        }

        @Test
        @DisplayName("should return unbalanced trial balance")
        void shouldReturnUnbalancedTrialBalance() {
            var assetAccount = anAssetAccount();
            var liabilityAccount = aLiabilityAccount();
            given(accountRepository.findByIsActive(true))
                    .willReturn(List.of(assetAccount, liabilityAccount));

            var assetBalance = aBalance(FIAT_RECEIVABLE, "USD", new BigDecimal("50000.00"), 5L);
            var liabilityBalance = aBalance("2010", "USD", new BigDecimal("40000.00"), 3L);
            given(balanceRepository.findAll())
                    .willReturn(List.of(assetBalance, liabilityBalance));

            var result = handler.getTrialBalance();

            assertThat(result.balanced()).isFalse();
        }

        @Test
        @DisplayName("should aggregate multi-currency balances per account")
        void shouldAggregateMultiCurrencyBalances() {
            var assetAccount = anAssetAccount();
            given(accountRepository.findByIsActive(true))
                    .willReturn(List.of(assetAccount));

            var usdBalance = aBalance(FIAT_RECEIVABLE, "USD", new BigDecimal("30000.00"), 3L);
            var eurBalance = aBalance(FIAT_RECEIVABLE, "EUR", new BigDecimal("20000.00"), 2L);
            given(balanceRepository.findAll())
                    .willReturn(List.of(usdBalance, eurBalance));

            var result = handler.getTrialBalance();

            assertThat(result.lines()).hasSize(1);
            assertThat(result.totalDebits()).isEqualByComparingTo(new BigDecimal("50000.00"));
        }

        @Test
        @DisplayName("should classify ASSET and CLEARING as debit, LIABILITY and REVENUE as credit")
        void shouldClassifyAccountTypes() {
            var assetAccount = anAssetAccount();
            var liabilityAccount = aLiabilityAccount();
            var revenueAccount = aRevenueAccount();
            var clearingAccount = aClearingAccount();
            given(accountRepository.findByIsActive(true))
                    .willReturn(List.of(assetAccount, liabilityAccount, revenueAccount, clearingAccount));

            var assetBal = aBalance(FIAT_RECEIVABLE, "USD", new BigDecimal("100.00"), 1L);
            var liabilityBal = aBalance("2010", "USD", new BigDecimal("50.00"), 1L);
            var revenueBal = aBalance("4000", "USD", new BigDecimal("30.00"), 1L);
            var clearingBal = aBalance("9000", "USD", new BigDecimal("20.00"), 1L);
            given(balanceRepository.findAll())
                    .willReturn(List.of(assetBal, liabilityBal, revenueBal, clearingBal));

            var result = handler.getTrialBalance();

            assertThat(result.totalDebits()).isEqualByComparingTo(new BigDecimal("120.00"));
            assertThat(result.totalCredits()).isEqualByComparingTo(new BigDecimal("80.00"));
        }

        @Test
        @DisplayName("should return empty trial balance when no accounts exist")
        void shouldReturnEmptyTrialBalance() {
            given(accountRepository.findByIsActive(true))
                    .willReturn(List.of());
            given(balanceRepository.findAll())
                    .willReturn(List.of());

            var result = handler.getTrialBalance();

            assertThat(result.lines()).isEmpty();
            assertThat(result.balanced()).isTrue();
        }
    }
}
