package com.stablecoin.payments.custody.domain.service;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.StablecoinTicker;
import com.stablecoin.payments.custody.domain.model.Wallet;
import com.stablecoin.payments.custody.domain.model.WalletBalance;
import com.stablecoin.payments.custody.domain.model.WalletPurpose;
import com.stablecoin.payments.custody.domain.model.WalletTier;
import com.stablecoin.payments.custody.domain.port.ChainRpcProvider;
import com.stablecoin.payments.custody.domain.port.TokenContractResolver;
import com.stablecoin.payments.custody.domain.port.WalletBalanceRepository;
import com.stablecoin.payments.custody.domain.port.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.stablecoin.payments.custody.fixtures.TestUtils.eqIgnoringTimestamps;
import static com.stablecoin.payments.custody.fixtures.TransferMonitorFixtures.USDC_BASE_CONTRACT;
import static com.stablecoin.payments.custody.fixtures.TransferMonitorFixtures.defaultTokenContractResolver;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("BalanceSyncCommandHandler")
class BalanceSyncCommandHandlerTest {

    private static final ChainId CHAIN_BASE = new ChainId("base");
    private static final StablecoinTicker USDC = StablecoinTicker.of("USDC");

    @Mock private WalletBalanceRepository walletBalanceRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private ChainRpcProvider chainRpcProvider;

    private BalanceSyncCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BalanceSyncCommandHandler(
                walletBalanceRepository, walletRepository, chainRpcProvider, defaultTokenContractResolver());
    }

    @Nested
    @DisplayName("syncAllBalances")
    class SyncAllBalances {

        @Test
        @DisplayName("should sync balance from RPC with resolved token contract and save updated balance")
        void shouldSyncBalanceFromRpcAndSave() {
            // given
            var balance = WalletBalance.initialize(
                    java.util.UUID.fromString("55555555-5555-5555-5555-555555555555"),
                    CHAIN_BASE, USDC
            ).syncFromChain(new BigDecimal("3000.00"), 50L);

            var wallet = Wallet.create(
                    CHAIN_BASE, "0xWalletAddr", "0xWalletAddr",
                    WalletTier.HOT, WalletPurpose.ON_RAMP,
                    "fireblocks", "vault-001", USDC
            );

            var onChainBalance = new BigDecimal("5000.00");
            var latestBlock = 200L;

            given(walletBalanceRepository.findAll())
                    .willReturn(List.of(balance));
            given(walletRepository.findById(balance.walletId()))
                    .willReturn(Optional.of(wallet));
            given(chainRpcProvider.getLatestBlockNumber(CHAIN_BASE))
                    .willReturn(latestBlock);
            given(chainRpcProvider.getTokenBalance(CHAIN_BASE, wallet.address(), USDC_BASE_CONTRACT))
                    .willReturn(onChainBalance);

            var expectedUpdated = balance.syncFromChain(onChainBalance, latestBlock);

            // when
            handler.syncAllBalances();

            // then
            then(walletBalanceRepository).should().save(eqIgnoringTimestamps(expectedUpdated));
        }

        @Test
        @DisplayName("should skip balance when wallet not found")
        void shouldSkipWhenWalletNotFound() {
            // given
            var balance = WalletBalance.initialize(
                    java.util.UUID.fromString("55555555-5555-5555-5555-555555555555"),
                    CHAIN_BASE, USDC
            );

            given(walletBalanceRepository.findAll())
                    .willReturn(List.of(balance));
            given(walletRepository.findById(balance.walletId()))
                    .willReturn(Optional.empty());

            // when
            handler.syncAllBalances();

            // then
            then(chainRpcProvider).shouldHaveNoInteractions();
            then(walletBalanceRepository).should(never()).save(eqIgnoringTimestamps(balance));
        }

        @Test
        @DisplayName("should skip balance when no new blocks since last indexed")
        void shouldSkipWhenNoNewBlocks() {
            // given
            var balance = WalletBalance.initialize(
                    java.util.UUID.fromString("55555555-5555-5555-5555-555555555555"),
                    CHAIN_BASE, USDC
            ).syncFromChain(new BigDecimal("3000.00"), 200L);

            var wallet = Wallet.create(
                    CHAIN_BASE, "0xWalletAddr", "0xWalletAddr",
                    WalletTier.HOT, WalletPurpose.ON_RAMP,
                    "fireblocks", "vault-001", USDC
            );

            given(walletBalanceRepository.findAll())
                    .willReturn(List.of(balance));
            given(walletRepository.findById(balance.walletId()))
                    .willReturn(Optional.of(wallet));
            given(chainRpcProvider.getLatestBlockNumber(CHAIN_BASE))
                    .willReturn(200L); // same as lastIndexedBlock

            // when
            handler.syncAllBalances();

            // then — no getTokenBalance called, no save
            then(walletBalanceRepository).should(never()).save(eqIgnoringTimestamps(balance));
        }

        @Test
        @DisplayName("should continue processing remaining balances when one RPC call fails")
        void shouldContinueWhenRpcFails() {
            // given
            var balance1 = WalletBalance.initialize(
                    java.util.UUID.fromString("55555555-5555-5555-5555-555555555555"),
                    CHAIN_BASE, USDC
            ).syncFromChain(new BigDecimal("1000.00"), 50L);

            var balance2 = WalletBalance.initialize(
                    java.util.UUID.fromString("66666666-6666-6666-6666-666666666666"),
                    CHAIN_BASE, USDC
            ).syncFromChain(new BigDecimal("2000.00"), 50L);

            var wallet1 = Wallet.create(
                    CHAIN_BASE, "0xAddr1", "0xAddr1",
                    WalletTier.HOT, WalletPurpose.ON_RAMP,
                    "fireblocks", "vault-001", USDC
            );
            var wallet2 = Wallet.create(
                    CHAIN_BASE, "0xAddr2", "0xAddr2",
                    WalletTier.HOT, WalletPurpose.ON_RAMP,
                    "fireblocks", "vault-002", USDC
            );

            var latestBlock = 200L;
            var onChainBalance2 = new BigDecimal("3000.00");

            given(walletBalanceRepository.findAll())
                    .willReturn(List.of(balance1, balance2));
            given(walletRepository.findById(balance1.walletId()))
                    .willReturn(Optional.of(wallet1));
            given(walletRepository.findById(balance2.walletId()))
                    .willReturn(Optional.of(wallet2));
            given(chainRpcProvider.getLatestBlockNumber(CHAIN_BASE))
                    .willReturn(latestBlock);
            // First balance RPC fails
            given(chainRpcProvider.getTokenBalance(CHAIN_BASE, wallet1.address(), USDC_BASE_CONTRACT))
                    .willThrow(new RuntimeException("RPC timeout"));
            // Second balance succeeds
            given(chainRpcProvider.getTokenBalance(CHAIN_BASE, wallet2.address(), USDC_BASE_CONTRACT))
                    .willReturn(onChainBalance2);

            var expectedUpdated2 = balance2.syncFromChain(onChainBalance2, latestBlock);

            // when
            handler.syncAllBalances();

            // then — second balance should still be saved despite first failure
            then(walletBalanceRepository).should().save(eqIgnoringTimestamps(expectedUpdated2));
        }

        @Test
        @DisplayName("should do nothing when no balances exist")
        void shouldDoNothingWhenNoBalances() {
            // given
            given(walletBalanceRepository.findAll())
                    .willReturn(List.of());

            // when
            handler.syncAllBalances();

            // then
            then(walletRepository).shouldHaveNoInteractions();
            then(chainRpcProvider).shouldHaveNoInteractions();
        }
    }
}
