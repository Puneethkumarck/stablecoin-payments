package com.stablecoin.payments.custody.infrastructure.persistence;

import com.stablecoin.payments.custody.AbstractIntegrationTest;
import com.stablecoin.payments.custody.domain.model.StablecoinTicker;
import com.stablecoin.payments.custody.domain.model.Wallet;
import com.stablecoin.payments.custody.domain.model.WalletPurpose;
import com.stablecoin.payments.custody.domain.model.WalletTier;
import com.stablecoin.payments.custody.domain.port.WalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static com.stablecoin.payments.custody.fixtures.WalletFixtures.ADDRESS;
import static com.stablecoin.payments.custody.fixtures.WalletFixtures.CHAIN_BASE;
import static com.stablecoin.payments.custody.fixtures.WalletFixtures.PURPOSE_ON_RAMP;
import static com.stablecoin.payments.custody.fixtures.WalletFixtures.anActiveWallet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("WalletPersistenceAdapter IT")
class WalletPersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private WalletRepository adapter;

    // ── Save & Retrieve ──────────────────────────────────────────────────

    @Test
    @DisplayName("should save and retrieve active wallet")
    void shouldSaveAndRetrieveActiveWallet() {
        var wallet = anActiveWallet();
        var saved = adapter.save(wallet);

        assertThat(adapter.findById(saved.walletId())).isPresent().get()
                .usingRecursiveComparison()
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should find by address")
    void shouldFindByAddress() {
        var saved = adapter.save(anActiveWallet());

        assertThat(adapter.findByAddress(ADDRESS)).isPresent().get()
                .usingRecursiveComparison()
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(saved);
    }

    @Test
    @DisplayName("should return empty when id not found")
    void shouldReturnEmptyWhenIdNotFound() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("should return empty when address not found")
    void shouldReturnEmptyWhenAddressNotFound() {
        assertThat(adapter.findByAddress("0xNonExistent")).isEmpty();
    }

    // ── Query by Chain & Purpose ─────────────────────────────────────────

    @Test
    @DisplayName("should find by chain id and purpose only active wallets")
    void shouldFindByChainIdAndPurposeOnlyActiveWallets() {
        var active = adapter.save(anActiveWallet());
        var deactivated = Wallet.create(
                CHAIN_BASE, "0xDeactivatedWallet999", "0xDeactivatedWallet999",
                WalletTier.HOT, WalletPurpose.ON_RAMP, "fireblocks", "vault-002",
                StablecoinTicker.of("USDC"));
        var savedDeactivated = adapter.save(deactivated);
        adapter.save(savedDeactivated.deactivate());

        var results = adapter.findByChainIdAndPurpose(CHAIN_BASE, PURPOSE_ON_RAMP);
        assertThat(results).hasSize(1);
    }

    // ── Update (Deactivate) ──────────────────────────────────────────────

    @Test
    @DisplayName("should update wallet to deactivated")
    void shouldUpdateWalletToDeactivated() {
        var saved = adapter.save(anActiveWallet());
        var expected = adapter.save(saved.deactivate());

        assertThat(adapter.findById(saved.walletId())).isPresent().get()
                .usingRecursiveComparison()
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    // ── Unique Constraints ───────────────────────────────────────────────

    @Test
    @DisplayName("should enforce unique address and chain constraint")
    void shouldEnforceUniqueAddressAndChainConstraint() {
        adapter.save(anActiveWallet());

        var duplicate = Wallet.create(
                CHAIN_BASE, ADDRESS, "0xDifferentChecksum",
                WalletTier.WARM, WalletPurpose.OFF_RAMP, "fireblocks", "vault-003",
                StablecoinTicker.of("USDT"));

        assertThatThrownBy(() -> adapter.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
