package com.stablecoin.payments.custody.infrastructure.persistence;

import com.stablecoin.payments.custody.AbstractIntegrationTest;
import com.stablecoin.payments.custody.domain.model.Wallet;
import com.stablecoin.payments.custody.domain.model.WalletBalance;
import com.stablecoin.payments.custody.domain.port.WalletBalanceRepository;
import com.stablecoin.payments.custody.domain.port.WalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.custody.fixtures.WalletBalanceFixtures.CHAIN_BASE;
import static com.stablecoin.payments.custody.fixtures.WalletBalanceFixtures.USDC;
import static com.stablecoin.payments.custody.fixtures.WalletFixtures.anActiveWallet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("WalletBalancePersistenceAdapter IT")
class WalletBalancePersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private WalletBalanceRepository adapter;

    @Autowired
    private WalletRepository walletAdapter;

    // ── Save & Retrieve ──────────────────────────────────────────────────

    @Test
    @DisplayName("should save and retrieve zero balance")
    void shouldSaveAndRetrieveZeroBalance() {
        var wallet = saveWallet();
        var balance = WalletBalance.initialize(wallet.walletId(), CHAIN_BASE, USDC);
        var saved = adapter.save(balance);

        assertThat(adapter.findByWalletIdAndStablecoin(wallet.walletId(), USDC)).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .ignoringFields("version")
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should find by wallet id and stablecoin")
    void shouldFindByWalletIdAndStablecoin() {
        var wallet = saveWallet();
        var balance = WalletBalance.initialize(wallet.walletId(), CHAIN_BASE, USDC);
        var saved = adapter.save(balance);

        assertThat(adapter.findByWalletIdAndStablecoin(wallet.walletId(), USDC)).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .ignoringFields("version")
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should find by wallet id")
    void shouldFindByWalletId() {
        var wallet = saveWallet();
        adapter.save(WalletBalance.initialize(wallet.walletId(), CHAIN_BASE, USDC));

        assertThat(adapter.findByWalletId(wallet.walletId())).hasSize(1);
    }

    @Test
    @DisplayName("should return empty when wallet id and stablecoin not found")
    void shouldReturnEmptyWhenNotFound() {
        assertThat(adapter.findByWalletIdAndStablecoin(UUID.randomUUID(), USDC)).isEmpty();
    }

    // ── Balance Operations ───────────────────────────────────────────────

    @Test
    @DisplayName("should update balance after reserve")
    void shouldUpdateBalanceAfterReserve() {
        var wallet = saveWallet();
        var balance = WalletBalance.initialize(wallet.walletId(), CHAIN_BASE, USDC);
        var saved = adapter.save(balance);

        var synced = adapter.save(saved.syncFromChain(new BigDecimal("500000.00"), 100L));
        var expected = adapter.save(synced.reserve(new BigDecimal("1000.00")));

        assertThat(adapter.findByWalletIdAndStablecoin(wallet.walletId(), USDC)).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .ignoringFields("version")
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("should update balance after release and confirm debit")
    void shouldUpdateBalanceAfterReleaseAndConfirmDebit() {
        var wallet = saveWallet();
        var balance = WalletBalance.initialize(wallet.walletId(), CHAIN_BASE, USDC);
        var saved = adapter.save(balance);

        saved = adapter.save(saved.syncFromChain(new BigDecimal("500000.00"), 100L));
        saved = adapter.save(saved.reserve(new BigDecimal("1000.00")));
        saved = adapter.save(saved.release(new BigDecimal("200.00")));
        var expected = adapter.save(saved.confirmDebit(new BigDecimal("800.00")));

        assertThat(adapter.findByWalletIdAndStablecoin(wallet.walletId(), USDC)).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .ignoringFields("version")
                .isEqualTo(expected);
    }

    // ── Unique Constraints ───────────────────────────────────────────────

    @Test
    @DisplayName("should enforce unique wallet stablecoin constraint")
    void shouldEnforceUniqueWalletStablecoinConstraint() {
        var wallet = saveWallet();
        adapter.save(WalletBalance.initialize(wallet.walletId(), CHAIN_BASE, USDC));

        var duplicate = WalletBalance.initialize(wallet.walletId(), CHAIN_BASE, USDC);

        assertThatThrownBy(() -> adapter.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── Decimal Precision ────────────────────────────────────────────────

    @Test
    @DisplayName("should persist decimal precision with 8 decimal places")
    void shouldPersistDecimalPrecision() {
        var wallet = saveWallet();
        var balance = WalletBalance.initialize(wallet.walletId(), CHAIN_BASE, USDC);
        var saved = adapter.save(balance);
        var expected = adapter.save(
                saved.syncFromChain(new BigDecimal("123456.12345678"), 200L));

        assertThat(adapter.findByWalletIdAndStablecoin(wallet.walletId(), USDC)).isPresent().get()
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .ignoringFieldsOfTypes(Instant.class)
                .ignoringFields("version")
                .isEqualTo(expected);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Wallet saveWallet() {
        return walletAdapter.save(anActiveWallet());
    }
}
