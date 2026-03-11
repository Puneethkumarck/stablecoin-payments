package com.stablecoin.payments.ledger.infrastructure.persistence;

import com.stablecoin.payments.ledger.AbstractIntegrationTest;
import com.stablecoin.payments.ledger.domain.model.AccountBalance;
import com.stablecoin.payments.ledger.domain.port.AccountBalanceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.ENTRY_ID_1;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.FIAT_RECEIVABLE;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AccountBalancePersistenceAdapter IT")
class AccountBalancePersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private AccountBalanceRepository adapter;

    // ── Save & Retrieve ──────────────────────────────────────────────────

    @Test
    @DisplayName("should save new balance and retrieve by account code and currency")
    void shouldSaveAndRetrieveByAccountCodeAndCurrency() {
        var balance = new AccountBalance(FIAT_RECEIVABLE, "USD", new BigDecimal("10000.00"), 0L, ENTRY_ID_1, Instant.now());
        var saved = adapter.save(balance);

        assertThat(adapter.findByAccountCodeAndCurrency(FIAT_RECEIVABLE, "USD")).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should update balance and increment version")
    void shouldUpdateBalanceAndIncrementVersion() {
        var initial = new AccountBalance(FIAT_RECEIVABLE, "USD", BigDecimal.ZERO, 0L, null, Instant.now());
        adapter.save(initial);

        var updated = new AccountBalance(FIAT_RECEIVABLE, "USD", new BigDecimal("5000.00"), 1L, ENTRY_ID_1, Instant.now());
        var saved = adapter.save(updated);

        assertThat(adapter.findByAccountCodeAndCurrency(FIAT_RECEIVABLE, "USD")).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    // ── Pessimistic Lock ─────────────────────────────────────────────────

    @Test
    @Transactional
    @DisplayName("should find for update and return correct balance")
    void shouldFindForUpdateAndReturnCorrectBalance() {
        var balance = new AccountBalance(FIAT_RECEIVABLE, "USD", new BigDecimal("7500.00"), 0L, ENTRY_ID_1, Instant.now());
        var saved = adapter.save(balance);

        assertThat(adapter.findForUpdate(FIAT_RECEIVABLE, "USD")).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    // ── Multi-Currency ───────────────────────────────────────────────────

    @Test
    @DisplayName("should support same account with different currencies")
    void shouldSupportSameAccountWithDifferentCurrencies() {
        adapter.save(new AccountBalance(FIAT_RECEIVABLE, "USD", new BigDecimal("10000.00"), 0L, null, Instant.now()));
        adapter.save(new AccountBalance(FIAT_RECEIVABLE, "EUR", new BigDecimal("9200.00"), 0L, null, Instant.now()));

        assertThat(adapter.findByAccountCode(FIAT_RECEIVABLE)).hasSize(2);
    }

    // ── Find All ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("should find all saved balances")
    void shouldFindAllSavedBalances() {
        adapter.save(new AccountBalance(FIAT_RECEIVABLE, "USD", new BigDecimal("10000.00"), 0L, null, Instant.now()));
        adapter.save(new AccountBalance("2010", "EUR", new BigDecimal("5000.00"), 0L, null, Instant.now()));

        assertThat(adapter.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("should return empty for non-existent account code and currency")
    void shouldReturnEmptyForNonExistentAccountAndCurrency() {
        assertThat(adapter.findByAccountCodeAndCurrency("9999", "XXX")).isEmpty();
    }
}
