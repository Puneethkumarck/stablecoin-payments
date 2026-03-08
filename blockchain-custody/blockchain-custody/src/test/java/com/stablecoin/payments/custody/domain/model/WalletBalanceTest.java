package com.stablecoin.payments.custody.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static com.stablecoin.payments.custody.fixtures.WalletBalanceFixtures.CHAIN_BASE;
import static com.stablecoin.payments.custody.fixtures.WalletBalanceFixtures.USDC;
import static com.stablecoin.payments.custody.fixtures.WalletBalanceFixtures.WALLET_ID;
import static com.stablecoin.payments.custody.fixtures.WalletBalanceFixtures.aBalanceWith;
import static com.stablecoin.payments.custody.fixtures.WalletBalanceFixtures.aZeroBalance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("WalletBalance")
class WalletBalanceTest {

    @Nested
    @DisplayName("Factory Method — initialize()")
    class Initialize {

        @Test
        @DisplayName("creates balance with all zeros")
        void createsZeroBalance() {
            var result = aZeroBalance();

            assertThat(result.balanceId()).isNotNull();
            assertThat(result.walletId()).isEqualTo(WALLET_ID);
            assertThat(result.chainId()).isEqualTo(CHAIN_BASE);
            assertThat(result.stablecoin()).isEqualTo(USDC);
            assertThat(result.availableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.reservedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.blockchainBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.lastIndexedBlock()).isZero();
            assertThat(result.version()).isZero();
            assertThat(result.updatedAt()).isNotNull();
        }

        @Test
        @DisplayName("rejects null walletId")
        void rejectsNullWalletId() {
            assertThatThrownBy(() -> WalletBalance.initialize(null, CHAIN_BASE, USDC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("walletId is required");
        }

        @Test
        @DisplayName("rejects null chainId")
        void rejectsNullChainId() {
            assertThatThrownBy(() -> WalletBalance.initialize(WALLET_ID, null, USDC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("chainId is required");
        }

        @Test
        @DisplayName("rejects null stablecoin")
        void rejectsNullStablecoin() {
            assertThatThrownBy(() -> WalletBalance.initialize(WALLET_ID, CHAIN_BASE, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("stablecoin is required");
        }
    }

    @Nested
    @DisplayName("reserve()")
    class Reserve {

        @Test
        @DisplayName("moves amount from available to reserved")
        void movesAvailableToReserved() {
            var balance = aBalanceWith(new BigDecimal("500.00"), BigDecimal.ZERO);

            var result = balance.reserve(new BigDecimal("200.00"));

            assertThat(result.availableBalance()).isEqualByComparingTo(new BigDecimal("300.00"));
            assertThat(result.reservedBalance()).isEqualByComparingTo(new BigDecimal("200.00"));
        }

        @Test
        @DisplayName("reserves entire available balance")
        void reservesEntireAvailableBalance() {
            var balance = aBalanceWith(new BigDecimal("500.00"), BigDecimal.ZERO);

            var result = balance.reserve(new BigDecimal("500.00"));

            assertThat(result.availableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.reservedBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
        }

        @Test
        @DisplayName("adds to existing reserved balance")
        void addsToExistingReserved() {
            var balance = aBalanceWith(new BigDecimal("300.00"), new BigDecimal("200.00"));

            var result = balance.reserve(new BigDecimal("100.00"));

            assertThat(result.availableBalance()).isEqualByComparingTo(new BigDecimal("200.00"));
            assertThat(result.reservedBalance()).isEqualByComparingTo(new BigDecimal("300.00"));
        }

        @Test
        @DisplayName("throws when insufficient available balance")
        void throwsWhenInsufficientAvailable() {
            var balance = aBalanceWith(new BigDecimal("100.00"), BigDecimal.ZERO);

            assertThatThrownBy(() -> balance.reserve(new BigDecimal("200.00")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Insufficient available balance");
        }

        @Test
        @DisplayName("throws when amount is null")
        void throwsWhenAmountNull() {
            var balance = aBalanceWith(new BigDecimal("100.00"), BigDecimal.ZERO);

            assertThatThrownBy(() -> balance.reserve(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Amount must be positive");
        }

        @Test
        @DisplayName("throws when amount is zero")
        void throwsWhenAmountZero() {
            var balance = aBalanceWith(new BigDecimal("100.00"), BigDecimal.ZERO);

            assertThatThrownBy(() -> balance.reserve(BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Amount must be positive");
        }

        @Test
        @DisplayName("throws when amount is negative")
        void throwsWhenAmountNegative() {
            var balance = aBalanceWith(new BigDecimal("100.00"), BigDecimal.ZERO);

            assertThatThrownBy(() -> balance.reserve(new BigDecimal("-10.00")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Amount must be positive");
        }
    }

    @Nested
    @DisplayName("release()")
    class Release {

        @Test
        @DisplayName("moves amount from reserved to available")
        void movesReservedToAvailable() {
            var balance = aBalanceWith(new BigDecimal("300.00"), new BigDecimal("200.00"));

            var result = balance.release(new BigDecimal("100.00"));

            assertThat(result.availableBalance()).isEqualByComparingTo(new BigDecimal("400.00"));
            assertThat(result.reservedBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("releases entire reserved balance")
        void releasesEntireReserved() {
            var balance = aBalanceWith(new BigDecimal("300.00"), new BigDecimal("200.00"));

            var result = balance.release(new BigDecimal("200.00"));

            assertThat(result.availableBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(result.reservedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("throws when insufficient reserved balance")
        void throwsWhenInsufficientReserved() {
            var balance = aBalanceWith(new BigDecimal("300.00"), new BigDecimal("50.00"));

            assertThatThrownBy(() -> balance.release(new BigDecimal("100.00")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Insufficient reserved balance");
        }

        @Test
        @DisplayName("throws when amount is null")
        void throwsWhenAmountNull() {
            var balance = aBalanceWith(new BigDecimal("100.00"), new BigDecimal("50.00"));

            assertThatThrownBy(() -> balance.release(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Amount must be positive");
        }

        @Test
        @DisplayName("throws when amount is zero")
        void throwsWhenAmountZero() {
            var balance = aBalanceWith(new BigDecimal("100.00"), new BigDecimal("50.00"));

            assertThatThrownBy(() -> balance.release(BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Amount must be positive");
        }

        @Test
        @DisplayName("throws when amount is negative")
        void throwsWhenAmountNegative() {
            var balance = aBalanceWith(new BigDecimal("100.00"), new BigDecimal("50.00"));

            assertThatThrownBy(() -> balance.release(new BigDecimal("-5.00")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Amount must be positive");
        }
    }

    @Nested
    @DisplayName("confirmDebit()")
    class ConfirmDebit {

        @Test
        @DisplayName("reduces reserved balance")
        void reducesReserved() {
            var balance = aBalanceWith(new BigDecimal("300.00"), new BigDecimal("200.00"));

            var result = balance.confirmDebit(new BigDecimal("100.00"));

            assertThat(result.reservedBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(result.availableBalance()).isEqualByComparingTo(new BigDecimal("300.00"));
        }

        @Test
        @DisplayName("confirms full reserved amount")
        void confirmsFullReserved() {
            var balance = aBalanceWith(new BigDecimal("300.00"), new BigDecimal("200.00"));

            var result = balance.confirmDebit(new BigDecimal("200.00"));

            assertThat(result.reservedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("throws when insufficient reserved balance")
        void throwsWhenInsufficientReserved() {
            var balance = aBalanceWith(new BigDecimal("300.00"), new BigDecimal("50.00"));

            assertThatThrownBy(() -> balance.confirmDebit(new BigDecimal("100.00")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Insufficient reserved balance for debit");
        }

        @Test
        @DisplayName("throws when amount is null")
        void throwsWhenAmountNull() {
            var balance = aBalanceWith(new BigDecimal("100.00"), new BigDecimal("50.00"));

            assertThatThrownBy(() -> balance.confirmDebit(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Amount must be positive");
        }

        @Test
        @DisplayName("throws when amount is zero")
        void throwsWhenAmountZero() {
            var balance = aBalanceWith(new BigDecimal("100.00"), new BigDecimal("50.00"));

            assertThatThrownBy(() -> balance.confirmDebit(BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Amount must be positive");
        }
    }

    @Nested
    @DisplayName("syncFromChain()")
    class SyncFromChain {

        @Test
        @DisplayName("updates blockchain balance and recalculates available")
        void updatesBlockchainBalance() {
            var balance = aZeroBalance();

            var result = balance.syncFromChain(new BigDecimal("1000.00"), 1L);

            assertThat(result.blockchainBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
            assertThat(result.availableBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
            assertThat(result.lastIndexedBlock()).isEqualTo(1L);
        }

        @Test
        @DisplayName("recalculates available as onChain - reserved")
        void recalculatesAvailableWithReserved() {
            var balance = aBalanceWith(new BigDecimal("300.00"), new BigDecimal("200.00"));

            var result = balance.syncFromChain(new BigDecimal("600.00"), 101L);

            assertThat(result.blockchainBalance()).isEqualByComparingTo(new BigDecimal("600.00"));
            assertThat(result.availableBalance()).isEqualByComparingTo(new BigDecimal("400.00"));
            assertThat(result.reservedBalance()).isEqualByComparingTo(new BigDecimal("200.00"));
        }

        @Test
        @DisplayName("clamps available to zero when onChain < reserved")
        void clampsAvailableToZero() {
            var balance = aBalanceWith(new BigDecimal("300.00"), new BigDecimal("200.00"));

            var result = balance.syncFromChain(new BigDecimal("100.00"), 101L);

            assertThat(result.availableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.blockchainBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(result.reservedBalance()).isEqualByComparingTo(new BigDecimal("200.00"));
        }

        @Test
        @DisplayName("throws when blockNumber is stale (equal)")
        void throwsWhenBlockNumberEqual() {
            var balance = aBalanceWith(new BigDecimal("300.00"), BigDecimal.ZERO);

            assertThatThrownBy(() -> balance.syncFromChain(new BigDecimal("500.00"), 100L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be greater than last indexed block");
        }

        @Test
        @DisplayName("throws when blockNumber is stale (less)")
        void throwsWhenBlockNumberLess() {
            var balance = aBalanceWith(new BigDecimal("300.00"), BigDecimal.ZERO);

            assertThatThrownBy(() -> balance.syncFromChain(new BigDecimal("500.00"), 50L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be greater than last indexed block");
        }

        @Test
        @DisplayName("throws when onChainBalance is null")
        void throwsWhenOnChainNull() {
            var balance = aZeroBalance();

            assertThatThrownBy(() -> balance.syncFromChain(null, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("On-chain balance must be non-negative");
        }

        @Test
        @DisplayName("throws when onChainBalance is negative")
        void throwsWhenOnChainNegative() {
            var balance = aZeroBalance();

            assertThatThrownBy(() -> balance.syncFromChain(new BigDecimal("-1.00"), 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("On-chain balance must be non-negative");
        }

        @Test
        @DisplayName("accepts zero onChainBalance")
        void acceptsZeroOnChain() {
            var balance = aZeroBalance();

            var result = balance.syncFromChain(BigDecimal.ZERO, 1L);

            assertThat(result.blockchainBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.availableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("hasSufficientBalance()")
    class HasSufficientBalance {

        @Test
        @DisplayName("returns true when available >= amount")
        void returnsTrueWhenSufficient() {
            var balance = aBalanceWith(new BigDecimal("500.00"), BigDecimal.ZERO);

            assertThat(balance.hasSufficientBalance(new BigDecimal("500.00"))).isTrue();
        }

        @Test
        @DisplayName("returns true when available > amount")
        void returnsTrueWhenMoreThanSufficient() {
            var balance = aBalanceWith(new BigDecimal("500.00"), BigDecimal.ZERO);

            assertThat(balance.hasSufficientBalance(new BigDecimal("100.00"))).isTrue();
        }

        @Test
        @DisplayName("returns false when available < amount")
        void returnsFalseWhenInsufficient() {
            var balance = aBalanceWith(new BigDecimal("100.00"), BigDecimal.ZERO);

            assertThat(balance.hasSufficientBalance(new BigDecimal("200.00"))).isFalse();
        }

        @Test
        @DisplayName("returns false for null amount")
        void returnsFalseForNull() {
            var balance = aBalanceWith(new BigDecimal("500.00"), BigDecimal.ZERO);

            assertThat(balance.hasSufficientBalance(null)).isFalse();
        }

        @Test
        @DisplayName("returns false for zero amount")
        void returnsFalseForZero() {
            var balance = aBalanceWith(new BigDecimal("500.00"), BigDecimal.ZERO);

            assertThat(balance.hasSufficientBalance(BigDecimal.ZERO)).isFalse();
        }

        @Test
        @DisplayName("returns false for negative amount")
        void returnsFalseForNegative() {
            var balance = aBalanceWith(new BigDecimal("500.00"), BigDecimal.ZERO);

            assertThat(balance.hasSufficientBalance(new BigDecimal("-10.00"))).isFalse();
        }
    }

    @Nested
    @DisplayName("Compact Constructor Validation")
    class CompactConstructor {

        @Test
        @DisplayName("rejects negative available balance")
        void rejectsNegativeAvailable() {
            assertThatThrownBy(() -> aZeroBalance().toBuilder()
                    .availableBalance(new BigDecimal("-1.00"))
                    .build()
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Available balance must be non-negative");
        }

        @Test
        @DisplayName("rejects negative reserved balance")
        void rejectsNegativeReserved() {
            assertThatThrownBy(() -> aZeroBalance().toBuilder()
                    .reservedBalance(new BigDecimal("-1.00"))
                    .build()
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Reserved balance must be non-negative");
        }

        @Test
        @DisplayName("rejects negative blockchain balance")
        void rejectsNegativeBlockchain() {
            assertThatThrownBy(() -> aZeroBalance().toBuilder()
                    .blockchainBalance(new BigDecimal("-1.00"))
                    .build()
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Blockchain balance must be non-negative");
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("reserve returns new instance, original unchanged")
        void reservePreservesOriginal() {
            var original = aBalanceWith(new BigDecimal("500.00"), BigDecimal.ZERO);

            var reserved = original.reserve(new BigDecimal("200.00"));

            assertThat(original.availableBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(reserved.availableBalance()).isEqualByComparingTo(new BigDecimal("300.00"));
        }
    }
}
