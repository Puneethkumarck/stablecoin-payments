package com.stablecoin.payments.ledger.domain.service;

import com.stablecoin.payments.ledger.domain.exception.AccountNotFoundException;
import com.stablecoin.payments.ledger.domain.model.Account;
import com.stablecoin.payments.ledger.domain.model.AccountType;
import com.stablecoin.payments.ledger.domain.port.AccountBalanceRepository;
import com.stablecoin.payments.ledger.domain.port.AccountRepository;
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

import static com.stablecoin.payments.ledger.domain.model.EntryType.CREDIT;
import static com.stablecoin.payments.ledger.domain.model.EntryType.DEBIT;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.CLIENT_FUNDS_HELD;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.FIAT_CASH;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.FIAT_PAYABLE;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.FIAT_RECEIVABLE;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.FX_SPREAD_REVENUE;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.IN_TRANSIT_CLEARING;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.NOW;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.STABLECOIN_REDEEMED_ACCT;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aBalance;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aZeroBalance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("BalanceCalculator")
class BalanceCalculatorTest {

    private static final String STABLECOIN_INVENTORY = "1010";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountBalanceRepository balanceRepository;

    @InjectMocks
    private BalanceCalculator balanceCalculator;

    // Reusable account definitions
    private static final Account ASSET_FIAT_RECEIVABLE = new Account(
            FIAT_RECEIVABLE, "Fiat Receivable", AccountType.ASSET, DEBIT, true, NOW);
    private static final Account ASSET_FIAT_CASH = new Account(
            FIAT_CASH, "Fiat Cash", AccountType.ASSET, DEBIT, true, NOW);
    private static final Account ASSET_STABLECOIN_INVENTORY = new Account(
            STABLECOIN_INVENTORY, "Stablecoin Inventory", AccountType.ASSET, DEBIT, true, NOW);
    private static final Account ASSET_STABLECOIN_REDEEMED = new Account(
            STABLECOIN_REDEEMED_ACCT, "Stablecoin Redeemed", AccountType.ASSET, DEBIT, true, NOW);
    private static final Account LIABILITY_CLIENT_FUNDS = new Account(
            CLIENT_FUNDS_HELD, "Client Funds Held", AccountType.LIABILITY, CREDIT, true, NOW);
    private static final Account LIABILITY_FIAT_PAYABLE = new Account(
            FIAT_PAYABLE, "Fiat Payable", AccountType.LIABILITY, CREDIT, true, NOW);
    private static final Account REVENUE_FX_SPREAD = new Account(
            FX_SPREAD_REVENUE, "FX Spread Revenue", AccountType.REVENUE, CREDIT, true, NOW);
    private static final Account CLEARING_IN_TRANSIT = new Account(
            IN_TRANSIT_CLEARING, "In-Transit Clearing", AccountType.CLEARING, DEBIT, true, NOW);

    private void stubAccount(Account account, String currency) {
        given(accountRepository.findByAccountCode(account.accountCode()))
                .willReturn(Optional.of(account));
        given(balanceRepository.findForUpdate(account.accountCode(), currency))
                .willReturn(Optional.of(aZeroBalance(account.accountCode(), currency)));
    }

    private void stubAccountWithBalance(Account account, String currency, BigDecimal balance, long version) {
        given(accountRepository.findByAccountCode(account.accountCode()))
                .willReturn(Optional.of(account));
        given(balanceRepository.findForUpdate(account.accountCode(), currency))
                .willReturn(Optional.of(aBalance(account.accountCode(), currency, balance, version)));
    }

    @Nested
    @DisplayName("ASSET account (normal balance = DEBIT)")
    class AssetAccount {

        @Test
        @DisplayName("DEBIT increases balance")
        void debitIncreasesBalance() {
            stubAccount(ASSET_FIAT_RECEIVABLE, "USD");
            stubAccount(LIABILITY_CLIENT_FUNDS, "USD");

            var result = balanceCalculator.computeBalances(List.of(
                    new JournalEntryRequest(DEBIT, FIAT_RECEIVABLE, new BigDecimal("10000.00"), "USD"),
                    new JournalEntryRequest(CREDIT, CLIENT_FUNDS_HELD, new BigDecimal("10000.00"), "USD")
            ));

            var expected = new BalanceUpdate(new BigDecimal("10000.00"), 1L);
            assertThat(result.get(BalanceCalculator.balanceKey(FIAT_RECEIVABLE, "USD")))
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("CREDIT decreases balance")
        void creditDecreasesBalance() {
            stubAccountWithBalance(ASSET_FIAT_RECEIVABLE, "USD", new BigDecimal("10000.00"), 1L);
            stubAccount(ASSET_FIAT_CASH, "USD");

            var result = balanceCalculator.computeBalances(List.of(
                    new JournalEntryRequest(DEBIT, FIAT_CASH, new BigDecimal("10000.00"), "USD"),
                    new JournalEntryRequest(CREDIT, FIAT_RECEIVABLE, new BigDecimal("10000.00"), "USD")
            ));

            var expected = new BalanceUpdate(BigDecimal.ZERO, 2L);
            assertThat(result.get(BalanceCalculator.balanceKey(FIAT_RECEIVABLE, "USD")))
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("LIABILITY account (normal balance = CREDIT)")
    class LiabilityAccount {

        @Test
        @DisplayName("CREDIT increases balance")
        void creditIncreasesBalance() {
            stubAccount(ASSET_FIAT_RECEIVABLE, "USD");
            stubAccount(LIABILITY_CLIENT_FUNDS, "USD");

            var result = balanceCalculator.computeBalances(List.of(
                    new JournalEntryRequest(DEBIT, FIAT_RECEIVABLE, new BigDecimal("10000.00"), "USD"),
                    new JournalEntryRequest(CREDIT, CLIENT_FUNDS_HELD, new BigDecimal("10000.00"), "USD")
            ));

            var expected = new BalanceUpdate(new BigDecimal("10000.00"), 1L);
            assertThat(result.get(BalanceCalculator.balanceKey(CLIENT_FUNDS_HELD, "USD")))
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("DEBIT decreases balance")
        void debitDecreasesBalance() {
            stubAccountWithBalance(LIABILITY_CLIENT_FUNDS, "USD", new BigDecimal("10000.00"), 3L);
            stubAccount(LIABILITY_FIAT_PAYABLE, "USD");

            var result = balanceCalculator.computeBalances(List.of(
                    new JournalEntryRequest(DEBIT, CLIENT_FUNDS_HELD, new BigDecimal("5000.00"), "USD"),
                    new JournalEntryRequest(CREDIT, FIAT_PAYABLE, new BigDecimal("5000.00"), "USD")
            ));

            var expected = new BalanceUpdate(new BigDecimal("5000.00"), 4L);
            assertThat(result.get(BalanceCalculator.balanceKey(CLIENT_FUNDS_HELD, "USD")))
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("REVENUE account (normal balance = CREDIT)")
    class RevenueAccount {

        @Test
        @DisplayName("CREDIT increases balance")
        void creditIncreasesBalance() {
            stubAccount(LIABILITY_CLIENT_FUNDS, "USD");
            stubAccount(REVENUE_FX_SPREAD, "USD");

            var result = balanceCalculator.computeBalances(List.of(
                    new JournalEntryRequest(DEBIT, CLIENT_FUNDS_HELD, new BigDecimal("30.00"), "USD"),
                    new JournalEntryRequest(CREDIT, FX_SPREAD_REVENUE, new BigDecimal("30.00"), "USD")
            ));

            var expected = new BalanceUpdate(new BigDecimal("30.00"), 1L);
            assertThat(result.get(BalanceCalculator.balanceKey(FX_SPREAD_REVENUE, "USD")))
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("CLEARING account (normal balance = DEBIT)")
    class ClearingAccount {

        @Test
        @DisplayName("CREDIT decreases balance (chain.transfer.submitted)")
        void creditDecreasesBalance() {
            stubAccount(ASSET_STABLECOIN_INVENTORY, "USDC");
            stubAccount(CLEARING_IN_TRANSIT, "USDC");

            var result = balanceCalculator.computeBalances(List.of(
                    new JournalEntryRequest(DEBIT, STABLECOIN_INVENTORY, new BigDecimal("10000.000000"), "USDC"),
                    new JournalEntryRequest(CREDIT, IN_TRANSIT_CLEARING, new BigDecimal("10000.000000"), "USDC")
            ));

            // Clearing (normal=DEBIT): CREDIT decreases
            var expected = new BalanceUpdate(new BigDecimal("-10000.000000"), 1L);
            assertThat(result.get(BalanceCalculator.balanceKey(IN_TRANSIT_CLEARING, "USDC")))
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("DEBIT increases balance (payment.completed clearing)")
        void debitIncreasesBalance() {
            stubAccountWithBalance(CLEARING_IN_TRANSIT, "USDC", new BigDecimal("-10000.000000"), 1L);
            stubAccount(ASSET_STABLECOIN_REDEEMED, "USDC");

            var result = balanceCalculator.computeBalances(List.of(
                    new JournalEntryRequest(DEBIT, IN_TRANSIT_CLEARING, new BigDecimal("10000.000000"), "USDC"),
                    new JournalEntryRequest(CREDIT, STABLECOIN_REDEEMED_ACCT, new BigDecimal("10000.000000"), "USDC")
            ));

            // Clearing (normal=DEBIT): DEBIT increases (-10000 + 10000 = 0)
            var expected = new BalanceUpdate(BigDecimal.ZERO, 2L);
            assertThat(result.get(BalanceCalculator.balanceKey(IN_TRANSIT_CLEARING, "USDC")))
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("balance initialization")
    class BalanceInitialization {

        @Test
        @DisplayName("creates zero balance when account balance not found")
        void createsZeroBalanceWhenNotFound() {
            given(accountRepository.findByAccountCode(FIAT_RECEIVABLE))
                    .willReturn(Optional.of(ASSET_FIAT_RECEIVABLE));
            given(balanceRepository.findForUpdate(FIAT_RECEIVABLE, "USD"))
                    .willReturn(Optional.empty());
            stubAccount(LIABILITY_CLIENT_FUNDS, "USD");

            var result = balanceCalculator.computeBalances(List.of(
                    new JournalEntryRequest(DEBIT, FIAT_RECEIVABLE, new BigDecimal("5000.00"), "USD"),
                    new JournalEntryRequest(CREDIT, CLIENT_FUNDS_HELD, new BigDecimal("5000.00"), "USD")
            ));

            // Zero balance + DEBIT 5000 = 5000 (version 0 + 1 = 1)
            var expected = new BalanceUpdate(new BigDecimal("5000.00"), 1L);
            assertThat(result.get(BalanceCalculator.balanceKey(FIAT_RECEIVABLE, "USD")))
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("version tracking")
    class VersionTracking {

        @Test
        @DisplayName("increments version from current balance version")
        void incrementsVersionFromCurrent() {
            stubAccountWithBalance(ASSET_FIAT_RECEIVABLE, "USD", new BigDecimal("50000.00"), 5L);
            stubAccount(LIABILITY_CLIENT_FUNDS, "USD");

            var result = balanceCalculator.computeBalances(List.of(
                    new JournalEntryRequest(DEBIT, FIAT_RECEIVABLE, new BigDecimal("1000.00"), "USD"),
                    new JournalEntryRequest(CREDIT, CLIENT_FUNDS_HELD, new BigDecimal("1000.00"), "USD")
            ));

            var expected = new BalanceUpdate(new BigDecimal("51000.00"), 6L);
            assertThat(result.get(BalanceCalculator.balanceKey(FIAT_RECEIVABLE, "USD")))
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("throws AccountNotFoundException when account not found")
        void throwsWhenAccountNotFound() {
            // "2010" < "9999" in sort order → "2010" processed first (stubbed), "9999" second (not found)
            stubAccount(LIABILITY_CLIENT_FUNDS, "USD");
            given(accountRepository.findByAccountCode("9999")).willReturn(Optional.empty());

            assertThatThrownBy(() -> balanceCalculator.computeBalances(List.of(
                    new JournalEntryRequest(CREDIT, CLIENT_FUNDS_HELD, new BigDecimal("100.00"), "USD"),
                    new JournalEntryRequest(DEBIT, "9999", new BigDecimal("100.00"), "USD")
            )))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("9999");
        }
    }

    @Nested
    @DisplayName("deterministic lock ordering")
    class DeterministicLockOrdering {

        @Test
        @DisplayName("processes entries in ascending account_code order regardless of input order")
        void processesInAscendingOrder() {
            stubAccount(ASSET_FIAT_RECEIVABLE, "USD");
            stubAccount(LIABILITY_CLIENT_FUNDS, "USD");

            // Pass entries in reverse order (2010 before 1000)
            var result = balanceCalculator.computeBalances(List.of(
                    new JournalEntryRequest(CREDIT, CLIENT_FUNDS_HELD, new BigDecimal("10000.00"), "USD"),
                    new JournalEntryRequest(DEBIT, FIAT_RECEIVABLE, new BigDecimal("10000.00"), "USD")
            ));

            // Both accounts computed correctly regardless of input order
            assertThat(result).containsKeys(
                    BalanceCalculator.balanceKey(FIAT_RECEIVABLE, "USD"),
                    BalanceCalculator.balanceKey(CLIENT_FUNDS_HELD, "USD")
            );
        }
    }

    @Nested
    @DisplayName("computeNewBalance static method")
    class ComputeNewBalance {

        @Test
        @DisplayName("adds when entry type matches normal balance")
        void addsWhenMatchingNormalBalance() {
            BigDecimal result = BalanceCalculator.computeNewBalance(
                    new BigDecimal("100.00"), DEBIT, DEBIT, new BigDecimal("50.00"));

            assertThat(result).isEqualByComparingTo(new BigDecimal("150.00"));
        }

        @Test
        @DisplayName("subtracts when entry type opposes normal balance")
        void subtractsWhenOpposingNormalBalance() {
            BigDecimal result = BalanceCalculator.computeNewBalance(
                    new BigDecimal("100.00"), CREDIT, DEBIT, new BigDecimal("30.00"));

            assertThat(result).isEqualByComparingTo(new BigDecimal("70.00"));
        }

        @Test
        @DisplayName("allows negative balance (overdraft)")
        void allowsNegativeBalance() {
            BigDecimal result = BalanceCalculator.computeNewBalance(
                    new BigDecimal("50.00"), CREDIT, DEBIT, new BigDecimal("100.00"));

            assertThat(result).isEqualByComparingTo(new BigDecimal("-50.00"));
        }
    }
}
