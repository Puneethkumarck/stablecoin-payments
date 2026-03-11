package com.stablecoin.payments.ledger.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.stablecoin.payments.ledger.domain.model.EntryType.CREDIT;
import static com.stablecoin.payments.ledger.domain.model.EntryType.DEBIT;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aBalance;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aCreditEntry;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aDebitEntry;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aZeroBalance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AccountBalance")
class AccountBalanceTest {

    @Nested
    @DisplayName("zero()")
    class Zero {

        @Test
        @DisplayName("creates zero balance with version 0")
        void createsZeroBalance() {
            var balance = AccountBalance.zero("1000", "USD");

            assertThat(balance.balance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(balance.version()).isEqualTo(0L);
        }

        @Test
        @DisplayName("zero balance has null lastEntryId")
        void zeroBalanceHasNullLastEntryId() {
            var balance = AccountBalance.zero("1000", "USD");

            assertThat(balance.lastEntryId()).isNull();
        }
    }

    @Nested
    @DisplayName("applyEntry() — ASSET account (normal balance = DEBIT)")
    class AssetAccount {

        @Test
        @DisplayName("DEBIT increases asset balance")
        void debitIncreasesAssetBalance() {
            var balance = aZeroBalance("1000", "USD");
            var entry = aDebitEntry("1000", new BigDecimal("10000.00"), "USD");

            var updated = balance.applyEntry(entry, DEBIT);

            assertThat(updated.balance()).isEqualByComparingTo(new BigDecimal("10000.00"));
        }

        @Test
        @DisplayName("CREDIT decreases asset balance")
        void creditDecreasesAssetBalance() {
            var balance = aBalance("1000", "USD", new BigDecimal("10000.00"), 1L);
            var entry = aCreditEntry("1000", new BigDecimal("3000.00"), "USD");

            var updated = balance.applyEntry(entry, DEBIT);

            assertThat(updated.balance()).isEqualByComparingTo(new BigDecimal("7000.00"));
        }

        @Test
        @DisplayName("asset balance can go negative (overdraft)")
        void assetBalanceCanGoNegative() {
            var balance = aBalance("1000", "USD", new BigDecimal("100.00"), 1L);
            var entry = aCreditEntry("1000", new BigDecimal("500.00"), "USD");

            var updated = balance.applyEntry(entry, DEBIT);

            assertThat(updated.balance()).isEqualByComparingTo(new BigDecimal("-400.00"));
        }
    }

    @Nested
    @DisplayName("applyEntry() — LIABILITY account (normal balance = CREDIT)")
    class LiabilityAccount {

        @Test
        @DisplayName("CREDIT increases liability balance")
        void creditIncreasesLiabilityBalance() {
            var balance = aZeroBalance("2010", "USD");
            var entry = aCreditEntry("2010", new BigDecimal("10000.00"), "USD");

            var updated = balance.applyEntry(entry, CREDIT);

            assertThat(updated.balance()).isEqualByComparingTo(new BigDecimal("10000.00"));
        }

        @Test
        @DisplayName("DEBIT decreases liability balance")
        void debitDecreasesLiabilityBalance() {
            var balance = aBalance("2010", "USD", new BigDecimal("10000.00"), 1L);
            var entry = aDebitEntry("2010", new BigDecimal("5000.00"), "USD");

            var updated = balance.applyEntry(entry, CREDIT);

            assertThat(updated.balance()).isEqualByComparingTo(new BigDecimal("5000.00"));
        }
    }

    @Nested
    @DisplayName("applyEntry() — REVENUE account (normal balance = CREDIT)")
    class RevenueAccount {

        @Test
        @DisplayName("CREDIT increases revenue balance")
        void creditIncreasesRevenueBalance() {
            var balance = aZeroBalance("4000", "USD");
            var entry = aCreditEntry("4000", new BigDecimal("30.00"), "USD");

            var updated = balance.applyEntry(entry, CREDIT);

            assertThat(updated.balance()).isEqualByComparingTo(new BigDecimal("30.00"));
        }
    }

    @Nested
    @DisplayName("applyEntry() — CLEARING account (normal balance = DEBIT)")
    class ClearingAccount {

        @Test
        @DisplayName("DEBIT increases clearing balance")
        void debitIncreasesClearingBalance() {
            var balance = aZeroBalance("9000", "USDC");
            var entry = aDebitEntry("9000", new BigDecimal("10000.000000"), "USDC");

            var updated = balance.applyEntry(entry, DEBIT);

            assertThat(updated.balance()).isEqualByComparingTo(new BigDecimal("10000.000000"));
        }

        @Test
        @DisplayName("CREDIT decreases clearing balance")
        void creditDecreasesClearingBalance() {
            var balance = aBalance("9000", "USDC", new BigDecimal("10000.000000"), 1L);
            var entry = aCreditEntry("9000", new BigDecimal("10000.000000"), "USDC");

            var updated = balance.applyEntry(entry, DEBIT);

            assertThat(updated.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("version tracking")
    class VersionTracking {

        @Test
        @DisplayName("version increments on each applyEntry")
        void versionIncrementsOnApply() {
            var balance = aZeroBalance("1000", "USD");
            var entry = aDebitEntry("1000", new BigDecimal("100.00"), "USD");

            var updated = balance.applyEntry(entry, DEBIT);

            assertThat(updated.version()).isEqualTo(1L);
        }

        @Test
        @DisplayName("version increments sequentially")
        void versionIncrementsSequentially() {
            var balance = aZeroBalance("1000", "USD");
            var entry1 = aDebitEntry("1000", new BigDecimal("100.00"), "USD");
            var entry2 = aDebitEntry("1000", new BigDecimal("200.00"), "USD");

            var after1 = balance.applyEntry(entry1, DEBIT);
            var after2 = after1.applyEntry(entry2, DEBIT);

            assertThat(after2.version()).isEqualTo(2L);
        }

        @Test
        @DisplayName("lastEntryId is updated to the applied entry")
        void lastEntryIdUpdated() {
            var balance = aZeroBalance("1000", "USD");
            var entry = aDebitEntry("1000", new BigDecimal("100.00"), "USD");

            var updated = balance.applyEntry(entry, DEBIT);

            assertThat(updated.lastEntryId()).isEqualTo(entry.entryId());
        }
    }

    @Nested
    @DisplayName("multi-currency")
    class MultiCurrency {

        @Test
        @DisplayName("same account supports different currencies independently")
        void samAccountDifferentCurrencies() {
            var usdBalance = aZeroBalance("1000", "USD");
            var eurBalance = aZeroBalance("1000", "EUR");
            var usdEntry = aDebitEntry("1000", new BigDecimal("10000.00"), "USD");
            var eurEntry = aDebitEntry("1000", new BigDecimal("9200.00"), "EUR");

            var updatedUsd = usdBalance.applyEntry(usdEntry, DEBIT);
            var updatedEur = eurBalance.applyEntry(eurEntry, DEBIT);

            assertThat(updatedUsd.balance()).isEqualByComparingTo(new BigDecimal("10000.00"));
            assertThat(updatedEur.balance()).isEqualByComparingTo(new BigDecimal("9200.00"));
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("rejects null accountCode")
        void rejectsNullAccountCode() {
            assertThatThrownBy(() -> new AccountBalance(null, "USD", BigDecimal.ZERO, 0L, null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("accountCode");
        }

        @Test
        @DisplayName("rejects null currency")
        void rejectsNullCurrency() {
            assertThatThrownBy(() -> new AccountBalance("1000", null, BigDecimal.ZERO, 0L, null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("currency");
        }

        @Test
        @DisplayName("rejects null balance")
        void rejectsNullBalance() {
            assertThatThrownBy(() -> new AccountBalance("1000", "USD", null, 0L, null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("balance");
        }
    }
}
