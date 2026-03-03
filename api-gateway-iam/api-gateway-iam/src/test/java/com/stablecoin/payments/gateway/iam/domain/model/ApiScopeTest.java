package com.stablecoin.payments.gateway.iam.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiScopeTest {

    @ParameterizedTest
    @ValueSource(strings = {"payments:read", "payments:write", "merchants.manage", "fx-rates:read"})
    void shouldAcceptValidScopes(String value) {
        var scope = new ApiScope(value);

        assertThat(scope.value()).isEqualTo(value);
    }

    @Test
    void shouldRejectNullValue() {
        assertThatThrownBy(() -> new ApiScope(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectBlankValue() {
        assertThatThrownBy(() -> new ApiScope("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAYMENTS", "123abc", "pay ments", "pay@ments"})
    void shouldRejectInvalidFormat(String value) {
        assertThatThrownBy(() -> new ApiScope(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid scope format");
    }

    @Test
    void shouldSupportEquality() {
        var a = new ApiScope("payments:read");
        var b = new ApiScope("payments:read");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
