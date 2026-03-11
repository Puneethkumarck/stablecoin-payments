package com.stablecoin.payments.ledger.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.stablecoin.payments.ledger.domain.model.EntryType.DEBIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JournalEntryRequest")
class JournalEntryRequestTest {

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("accepts valid request")
        void acceptsValidRequest() {
            var request = new JournalEntryRequest(DEBIT, "1000", new BigDecimal("100.00"), "USD");

            assertThat(request.amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("rejects zero amount")
        void rejectsZeroAmount() {
            assertThatThrownBy(() -> new JournalEntryRequest(DEBIT, "1000", BigDecimal.ZERO, "USD"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("amount must be positive");
        }

        @Test
        @DisplayName("rejects negative amount")
        void rejectsNegativeAmount() {
            assertThatThrownBy(() -> new JournalEntryRequest(DEBIT, "1000", new BigDecimal("-100"), "USD"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("amount must be positive");
        }

        @Test
        @DisplayName("rejects null entryType")
        void rejectsNullEntryType() {
            assertThatThrownBy(() -> new JournalEntryRequest(null, "1000", new BigDecimal("100"), "USD"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("entryType");
        }

        @Test
        @DisplayName("rejects null accountCode")
        void rejectsNullAccountCode() {
            assertThatThrownBy(() -> new JournalEntryRequest(DEBIT, null, new BigDecimal("100"), "USD"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("accountCode");
        }

        @Test
        @DisplayName("rejects null currency")
        void rejectsNullCurrency() {
            assertThatThrownBy(() -> new JournalEntryRequest(DEBIT, "1000", new BigDecimal("100"), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("currency");
        }
    }
}
