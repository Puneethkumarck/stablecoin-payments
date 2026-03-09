package com.stablecoin.payments.offramp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MobileMoneyAccountTest {

    @Test
    @DisplayName("creates valid mobile money account")
    void createsValidAccount() {
        var account = new MobileMoneyAccount(MobileMoneyProvider.M_PESA, "+254712345678", "KE");

        var expected = new MobileMoneyAccount(MobileMoneyProvider.M_PESA, "+254712345678", "KE");
        assertThat(account).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    @DisplayName("throws when provider is null")
    void throwsWhenProviderNull() {
        assertThatThrownBy(() -> new MobileMoneyAccount(null, "+254712345678", "KE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Provider");
    }

    @Test
    @DisplayName("throws when phoneNumber is null")
    void throwsWhenPhoneNumberNull() {
        assertThatThrownBy(() -> new MobileMoneyAccount(MobileMoneyProvider.M_PESA, null, "KE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Phone number");
    }

    @Test
    @DisplayName("throws when phoneNumber is blank")
    void throwsWhenPhoneNumberBlank() {
        assertThatThrownBy(() -> new MobileMoneyAccount(MobileMoneyProvider.M_PESA, "  ", "KE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Phone number");
    }

    @Test
    @DisplayName("throws when country is null")
    void throwsWhenCountryNull() {
        assertThatThrownBy(() -> new MobileMoneyAccount(MobileMoneyProvider.M_PESA, "+254712345678", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Country");
    }

    @Test
    @DisplayName("throws when country is blank")
    void throwsWhenCountryBlank() {
        assertThatThrownBy(() -> new MobileMoneyAccount(MobileMoneyProvider.M_PESA, "+254712345678", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Country");
    }
}
