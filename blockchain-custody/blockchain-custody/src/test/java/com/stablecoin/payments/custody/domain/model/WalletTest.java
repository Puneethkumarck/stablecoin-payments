package com.stablecoin.payments.custody.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.stablecoin.payments.custody.fixtures.WalletFixtures.ADDRESS;
import static com.stablecoin.payments.custody.fixtures.WalletFixtures.ADDRESS_CHECKSUM;
import static com.stablecoin.payments.custody.fixtures.WalletFixtures.CHAIN_BASE;
import static com.stablecoin.payments.custody.fixtures.WalletFixtures.CUSTODIAN;
import static com.stablecoin.payments.custody.fixtures.WalletFixtures.PURPOSE_ON_RAMP;
import static com.stablecoin.payments.custody.fixtures.WalletFixtures.TIER_HOT;
import static com.stablecoin.payments.custody.fixtures.WalletFixtures.USDC;
import static com.stablecoin.payments.custody.fixtures.WalletFixtures.VAULT_ACCOUNT_ID;
import static com.stablecoin.payments.custody.fixtures.WalletFixtures.aDeactivatedWallet;
import static com.stablecoin.payments.custody.fixtures.WalletFixtures.anActiveWallet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Wallet")
class WalletTest {

    @Nested
    @DisplayName("Factory Method")
    class FactoryMethod {

        @Test
        @DisplayName("creates an active wallet with correct fields")
        void createsActiveWallet() {
            var result = anActiveWallet();

            assertThat(result.walletId()).isNotNull();
            assertThat(result.chainId()).isEqualTo(CHAIN_BASE);
            assertThat(result.address()).isEqualTo(ADDRESS);
            assertThat(result.addressChecksum()).isEqualTo(ADDRESS_CHECKSUM);
            assertThat(result.tier()).isEqualTo(TIER_HOT);
            assertThat(result.purpose()).isEqualTo(PURPOSE_ON_RAMP);
            assertThat(result.custodian()).isEqualTo(CUSTODIAN);
            assertThat(result.vaultAccountId()).isEqualTo(VAULT_ACCOUNT_ID);
            assertThat(result.stablecoin()).isEqualTo(USDC);
            assertThat(result.active()).isTrue();
            assertThat(result.createdAt()).isNotNull();
            assertThat(result.deactivatedAt()).isNull();
        }

        @Test
        @DisplayName("rejects null chainId")
        void rejectsNullChainId() {
            assertThatThrownBy(() -> Wallet.create(
                    null, ADDRESS, ADDRESS_CHECKSUM,
                    TIER_HOT, PURPOSE_ON_RAMP, CUSTODIAN, VAULT_ACCOUNT_ID, USDC
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("chainId is required");
        }

        @Test
        @DisplayName("rejects null address")
        void rejectsNullAddress() {
            assertThatThrownBy(() -> Wallet.create(
                    CHAIN_BASE, null, ADDRESS_CHECKSUM,
                    TIER_HOT, PURPOSE_ON_RAMP, CUSTODIAN, VAULT_ACCOUNT_ID, USDC
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("address is required");
        }

        @Test
        @DisplayName("rejects blank address")
        void rejectsBlankAddress() {
            assertThatThrownBy(() -> Wallet.create(
                    CHAIN_BASE, "  ", ADDRESS_CHECKSUM,
                    TIER_HOT, PURPOSE_ON_RAMP, CUSTODIAN, VAULT_ACCOUNT_ID, USDC
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("address is required");
        }

        @Test
        @DisplayName("rejects null addressChecksum")
        void rejectsNullAddressChecksum() {
            assertThatThrownBy(() -> Wallet.create(
                    CHAIN_BASE, ADDRESS, null,
                    TIER_HOT, PURPOSE_ON_RAMP, CUSTODIAN, VAULT_ACCOUNT_ID, USDC
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("addressChecksum is required");
        }

        @Test
        @DisplayName("rejects blank addressChecksum")
        void rejectsBlankAddressChecksum() {
            assertThatThrownBy(() -> Wallet.create(
                    CHAIN_BASE, ADDRESS, "  ",
                    TIER_HOT, PURPOSE_ON_RAMP, CUSTODIAN, VAULT_ACCOUNT_ID, USDC
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("addressChecksum is required");
        }

        @Test
        @DisplayName("rejects null tier")
        void rejectsNullTier() {
            assertThatThrownBy(() -> Wallet.create(
                    CHAIN_BASE, ADDRESS, ADDRESS_CHECKSUM,
                    null, PURPOSE_ON_RAMP, CUSTODIAN, VAULT_ACCOUNT_ID, USDC
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("tier is required");
        }

        @Test
        @DisplayName("rejects null purpose")
        void rejectsNullPurpose() {
            assertThatThrownBy(() -> Wallet.create(
                    CHAIN_BASE, ADDRESS, ADDRESS_CHECKSUM,
                    TIER_HOT, null, CUSTODIAN, VAULT_ACCOUNT_ID, USDC
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("purpose is required");
        }

        @Test
        @DisplayName("rejects null custodian")
        void rejectsNullCustodian() {
            assertThatThrownBy(() -> Wallet.create(
                    CHAIN_BASE, ADDRESS, ADDRESS_CHECKSUM,
                    TIER_HOT, PURPOSE_ON_RAMP, null, VAULT_ACCOUNT_ID, USDC
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("custodian is required");
        }

        @Test
        @DisplayName("rejects blank custodian")
        void rejectsBlankCustodian() {
            assertThatThrownBy(() -> Wallet.create(
                    CHAIN_BASE, ADDRESS, ADDRESS_CHECKSUM,
                    TIER_HOT, PURPOSE_ON_RAMP, "  ", VAULT_ACCOUNT_ID, USDC
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("custodian is required");
        }

        @Test
        @DisplayName("rejects null vaultAccountId")
        void rejectsNullVaultAccountId() {
            assertThatThrownBy(() -> Wallet.create(
                    CHAIN_BASE, ADDRESS, ADDRESS_CHECKSUM,
                    TIER_HOT, PURPOSE_ON_RAMP, CUSTODIAN, null, USDC
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("vaultAccountId is required");
        }

        @Test
        @DisplayName("rejects blank vaultAccountId")
        void rejectsBlankVaultAccountId() {
            assertThatThrownBy(() -> Wallet.create(
                    CHAIN_BASE, ADDRESS, ADDRESS_CHECKSUM,
                    TIER_HOT, PURPOSE_ON_RAMP, CUSTODIAN, "  ", USDC
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("vaultAccountId is required");
        }

        @Test
        @DisplayName("rejects null stablecoin")
        void rejectsNullStablecoin() {
            assertThatThrownBy(() -> Wallet.create(
                    CHAIN_BASE, ADDRESS, ADDRESS_CHECKSUM,
                    TIER_HOT, PURPOSE_ON_RAMP, CUSTODIAN, VAULT_ACCOUNT_ID, null
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("stablecoin is required");
        }
    }

    @Nested
    @DisplayName("Deactivate")
    class Deactivate {

        @Test
        @DisplayName("sets active to false and deactivatedAt")
        void setsActiveToFalse() {
            var active = anActiveWallet();

            var result = active.deactivate();

            assertThat(result.active()).isFalse();
            assertThat(result.deactivatedAt()).isNotNull();
            assertThat(result.walletId()).isEqualTo(active.walletId());
        }

        @Test
        @DisplayName("throws when already deactivated")
        void throwsWhenAlreadyDeactivated() {
            var deactivated = aDeactivatedWallet();

            assertThatThrownBy(deactivated::deactivate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already deactivated");
        }
    }

    @Nested
    @DisplayName("Query Methods")
    class QueryMethods {

        @Test
        @DisplayName("isActive() returns true for active wallet")
        void isActiveReturnsTrue() {
            assertThat(anActiveWallet().isActive()).isTrue();
        }

        @Test
        @DisplayName("isActive() returns false for deactivated wallet")
        void isActiveReturnsFalse() {
            assertThat(aDeactivatedWallet().isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("deactivate returns new instance, original unchanged")
        void deactivatePreservesOriginal() {
            var original = anActiveWallet();

            var deactivated = original.deactivate();

            assertThat(original.active()).isTrue();
            assertThat(deactivated.active()).isFalse();
        }
    }
}
