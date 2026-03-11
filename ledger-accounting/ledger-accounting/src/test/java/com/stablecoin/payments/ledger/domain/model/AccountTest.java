package com.stablecoin.payments.ledger.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.NOW;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aClearingAccount;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aLiabilityAccount;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.aRevenueAccount;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.anAssetAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Account")
class AccountTest {

    @Nested
    @DisplayName("creation")
    class Creation {

        @Test
        @DisplayName("creates asset account with debit normal balance")
        void createsAssetAccount() {
            var account = anAssetAccount();

            assertThat(account.accountType()).isEqualTo(AccountType.ASSET);
            assertThat(account.normalBalance()).isEqualTo(EntryType.DEBIT);
        }

        @Test
        @DisplayName("creates liability account with credit normal balance")
        void createsLiabilityAccount() {
            var account = aLiabilityAccount();

            assertThat(account.accountType()).isEqualTo(AccountType.LIABILITY);
            assertThat(account.normalBalance()).isEqualTo(EntryType.CREDIT);
        }

        @Test
        @DisplayName("creates revenue account with credit normal balance")
        void createsRevenueAccount() {
            var account = aRevenueAccount();

            assertThat(account.accountType()).isEqualTo(AccountType.REVENUE);
            assertThat(account.normalBalance()).isEqualTo(EntryType.CREDIT);
        }

        @Test
        @DisplayName("creates clearing account with debit normal balance")
        void createsClearingAccount() {
            var account = aClearingAccount();

            assertThat(account.accountType()).isEqualTo(AccountType.CLEARING);
            assertThat(account.normalBalance()).isEqualTo(EntryType.DEBIT);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("rejects null accountCode")
        void rejectsNullAccountCode() {
            assertThatThrownBy(() -> new Account(null, "Test", AccountType.ASSET, EntryType.DEBIT, true, NOW))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("accountCode");
        }

        @Test
        @DisplayName("rejects null accountName")
        void rejectsNullAccountName() {
            assertThatThrownBy(() -> new Account("1000", null, AccountType.ASSET, EntryType.DEBIT, true, NOW))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("accountName");
        }

        @Test
        @DisplayName("rejects null accountType")
        void rejectsNullAccountType() {
            assertThatThrownBy(() -> new Account("1000", "Test", null, EntryType.DEBIT, true, NOW))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("accountType");
        }

        @Test
        @DisplayName("rejects null normalBalance")
        void rejectsNullNormalBalance() {
            assertThatThrownBy(() -> new Account("1000", "Test", AccountType.ASSET, null, true, NOW))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("normalBalance");
        }
    }
}
