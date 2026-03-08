package com.stablecoin.payments.onramp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.stablecoin.payments.onramp.domain.model.AccountType.ACH_ROUTING;
import static com.stablecoin.payments.onramp.domain.model.AccountType.IBAN;
import static com.stablecoin.payments.onramp.fixtures.CollectionOrderFixtures.aBankAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BankAccount")
class BankAccountTest {

    @Nested
    @DisplayName("Valid Construction")
    class ValidConstruction {

        @Test
        @DisplayName("creates BankAccount with all required fields")
        void createsBankAccountWithAllFields() {
            var bankAccount = new BankAccount("sha256_abc123", "DEUTDEFF", IBAN, "DE");

            var expected = new BankAccount("sha256_abc123", "DEUTDEFF", IBAN, "DE");

            assertThat(bankAccount)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("creates BankAccount with ACH_ROUTING type")
        void createsBankAccountWithAchRouting() {
            var bankAccount = new BankAccount("sha256_xyz789", "021000021", ACH_ROUTING, "US");

            assertThat(bankAccount.accountType()).isEqualTo(ACH_ROUTING);
        }

        @Test
        @DisplayName("fixture creates valid BankAccount")
        void fixtureCreatesValidBankAccount() {
            var bankAccount = aBankAccount();

            assertThat(bankAccount.accountNumberHash()).isNotBlank();
            assertThat(bankAccount.bankCode()).isNotBlank();
            assertThat(bankAccount.accountType()).isNotNull();
            assertThat(bankAccount.country()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("Invalid Construction")
    class InvalidConstruction {

        @Test
        @DisplayName("rejects null accountNumberHash")
        void rejectsNullAccountNumberHash() {
            assertThatThrownBy(() -> new BankAccount(null, "DEUTDEFF", IBAN, "DE"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Account number hash is required");
        }

        @Test
        @DisplayName("rejects blank accountNumberHash")
        void rejectsBlankAccountNumberHash() {
            assertThatThrownBy(() -> new BankAccount("   ", "DEUTDEFF", IBAN, "DE"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Account number hash is required");
        }

        @Test
        @DisplayName("rejects null bankCode")
        void rejectsNullBankCode() {
            assertThatThrownBy(() -> new BankAccount("sha256_abc123", null, IBAN, "DE"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Bank code is required");
        }

        @Test
        @DisplayName("rejects blank bankCode")
        void rejectsBlankBankCode() {
            assertThatThrownBy(() -> new BankAccount("sha256_abc123", "   ", IBAN, "DE"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Bank code is required");
        }

        @Test
        @DisplayName("rejects null accountType")
        void rejectsNullAccountType() {
            assertThatThrownBy(() -> new BankAccount("sha256_abc123", "DEUTDEFF", null, "DE"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Account type is required");
        }

        @Test
        @DisplayName("rejects null country")
        void rejectsNullCountry() {
            assertThatThrownBy(() -> new BankAccount("sha256_abc123", "DEUTDEFF", IBAN, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Country is required");
        }

        @Test
        @DisplayName("rejects blank country")
        void rejectsBlankCountry() {
            assertThatThrownBy(() -> new BankAccount("sha256_abc123", "DEUTDEFF", IBAN, "   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Country is required");
        }
    }
}
