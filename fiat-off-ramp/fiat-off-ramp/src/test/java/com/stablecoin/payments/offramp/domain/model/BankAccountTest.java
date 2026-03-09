package com.stablecoin.payments.offramp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BankAccountTest {

    @Test
    @DisplayName("creates valid bank account")
    void createsValidBankAccount() {
        var account = new BankAccount("DE89370400440532013000", "DEUTDEFF", AccountType.IBAN, "DE");

        var expected = new BankAccount("DE89370400440532013000", "DEUTDEFF", AccountType.IBAN, "DE");
        assertThat(account).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    @DisplayName("throws when accountNumber is null")
    void throwsWhenAccountNumberNull() {
        assertThatThrownBy(() -> new BankAccount(null, "DEUTDEFF", AccountType.IBAN, "DE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account number");
    }

    @Test
    @DisplayName("throws when accountNumber is blank")
    void throwsWhenAccountNumberBlank() {
        assertThatThrownBy(() -> new BankAccount("  ", "DEUTDEFF", AccountType.IBAN, "DE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account number");
    }

    @Test
    @DisplayName("throws when bankCode is null")
    void throwsWhenBankCodeNull() {
        assertThatThrownBy(() -> new BankAccount("DE89370400440532013000", null, AccountType.IBAN, "DE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bank code");
    }

    @Test
    @DisplayName("throws when bankCode is blank")
    void throwsWhenBankCodeBlank() {
        assertThatThrownBy(() -> new BankAccount("DE89370400440532013000", "  ", AccountType.IBAN, "DE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bank code");
    }

    @Test
    @DisplayName("throws when accountType is null")
    void throwsWhenAccountTypeNull() {
        assertThatThrownBy(() -> new BankAccount("DE89370400440532013000", "DEUTDEFF", null, "DE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account type");
    }

    @Test
    @DisplayName("throws when country is null")
    void throwsWhenCountryNull() {
        assertThatThrownBy(() -> new BankAccount("DE89370400440532013000", "DEUTDEFF", AccountType.IBAN, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Country");
    }

    @Test
    @DisplayName("throws when country is blank")
    void throwsWhenCountryBlank() {
        assertThatThrownBy(() -> new BankAccount("DE89370400440532013000", "DEUTDEFF", AccountType.IBAN, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Country");
    }
}
