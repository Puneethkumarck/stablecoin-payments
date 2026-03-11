package com.stablecoin.payments.ledger.infrastructure.persistence;

import com.stablecoin.payments.ledger.AbstractIntegrationTest;
import com.stablecoin.payments.ledger.domain.model.Account;
import com.stablecoin.payments.ledger.domain.model.AccountType;
import com.stablecoin.payments.ledger.domain.model.EntryType;
import com.stablecoin.payments.ledger.domain.port.AccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AccountPersistenceAdapter IT")
class AccountPersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private AccountRepository adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ── Seeded Accounts ──────────────────────────────────────────────────

    @Test
    @DisplayName("should load 10 seeded accounts")
    void shouldLoad10SeededAccounts() {
        assertThat(adapter.findAll()).hasSize(10);
    }

    @Test
    @DisplayName("should return Fiat Receivable as ASSET with DEBIT normal balance")
    void shouldReturnFiatReceivableAsAssetDebit() {
        var expected = new Account("1000", "Fiat Receivable", AccountType.ASSET, EntryType.DEBIT, true, null);

        assertThat(adapter.findByAccountCode("1000")).isPresent().get()
                .usingRecursiveComparison()
                .ignoringFields("createdAt")
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("should return Client Funds Held as LIABILITY with CREDIT normal balance")
    void shouldReturnClientFundsHeldAsLiabilityCredit() {
        var expected = new Account("2010", "Client Funds Held", AccountType.LIABILITY, EntryType.CREDIT, true, null);

        assertThat(adapter.findByAccountCode("2010")).isPresent().get()
                .usingRecursiveComparison()
                .ignoringFields("createdAt")
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("should return all active accounts")
    void shouldReturnAllActiveAccounts() {
        assertThat(adapter.findByIsActive(true)).hasSize(10);
    }

    @Test
    @DisplayName("should return empty for non-existent account code")
    void shouldReturnEmptyForNonExistentAccountCode() {
        assertThat(adapter.findByAccountCode("9999")).isEmpty();
    }

    // ── Seeded Currencies ────────────────────────────────────────────────

    @Test
    @DisplayName("should load 8 seeded currencies with correct decimal precision")
    void shouldLoad8SeededCurrenciesWithCorrectPrecision() {
        var count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM currencies", Integer.class);
        assertThat(count).isEqualTo(8);
    }

    @Test
    @DisplayName("should have USD with decimal precision 2")
    void shouldHaveUsdWithPrecision2() {
        var precision = jdbcTemplate.queryForObject(
                "SELECT decimal_precision FROM currencies WHERE code = 'USD'", Integer.class);
        assertThat(precision).isEqualTo(2);
    }

    @Test
    @DisplayName("should have USDC with decimal precision 6")
    void shouldHaveUsdcWithPrecision6() {
        var precision = jdbcTemplate.queryForObject(
                "SELECT decimal_precision FROM currencies WHERE code = 'USDC'", Integer.class);
        assertThat(precision).isEqualTo(6);
    }

    @Test
    @DisplayName("should have DAI with decimal precision 18")
    void shouldHaveDaiWithPrecision18() {
        var precision = jdbcTemplate.queryForObject(
                "SELECT decimal_precision FROM currencies WHERE code = 'DAI'", Integer.class);
        assertThat(precision).isEqualTo(18);
    }
}
