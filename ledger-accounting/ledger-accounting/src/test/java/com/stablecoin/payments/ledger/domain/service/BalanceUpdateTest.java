package com.stablecoin.payments.ledger.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BalanceUpdate")
class BalanceUpdateTest {

    @Test
    @DisplayName("creates valid balance update")
    void createsValid() {
        var update = new BalanceUpdate(new BigDecimal("10000.00"), 1L);

        var expected = new BalanceUpdate(new BigDecimal("10000.00"), 1L);
        assertThat(update)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("rejects null balanceAfter")
    void rejectsNullBalance() {
        assertThatThrownBy(() -> new BalanceUpdate(null, 1L))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects zero accountVersion")
    void rejectsZeroVersion() {
        assertThatThrownBy(() -> new BalanceUpdate(BigDecimal.ZERO, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects negative accountVersion")
    void rejectsNegativeVersion() {
        assertThatThrownBy(() -> new BalanceUpdate(BigDecimal.ZERO, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("allows negative balanceAfter")
    void allowsNegativeBalance() {
        var update = new BalanceUpdate(new BigDecimal("-500.00"), 3L);

        var expected = new BalanceUpdate(new BigDecimal("-500.00"), 3L);
        assertThat(update)
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }
}
