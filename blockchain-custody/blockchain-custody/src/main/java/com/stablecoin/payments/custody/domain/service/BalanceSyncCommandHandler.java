package com.stablecoin.payments.custody.domain.service;

import com.stablecoin.payments.custody.domain.port.ChainRpcProvider;
import com.stablecoin.payments.custody.domain.port.TokenContractResolver;
import com.stablecoin.payments.custody.domain.port.WalletBalanceRepository;
import com.stablecoin.payments.custody.domain.port.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Domain service responsible for syncing wallet balances from on-chain data.
 * <p>
 * Queries all wallet balances and updates them with the latest on-chain balance
 * from the RPC provider. No class-level transaction — each balance save runs in
 * its own transaction via the repository adapter, keeping RPC calls outside
 * any database transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceSyncCommandHandler {

    private final WalletBalanceRepository walletBalanceRepository;
    private final WalletRepository walletRepository;
    private final ChainRpcProvider chainRpcProvider;
    private final TokenContractResolver tokenContractResolver;

    /**
     * Syncs all wallet balances from on-chain data.
     * <p>
     * For each balance record, fetches the current on-chain balance and latest block number,
     * then updates via {@code WalletBalance.syncFromChain()}.
     * Failures for individual balances are logged and skipped.
     */
    public void syncAllBalances() {
        var balances = walletBalanceRepository.findAll();

        if (balances.isEmpty()) {
            log.debug("No wallet balances to sync");
            return;
        }

        log.info("Starting balance sync for {} wallet balances", balances.size());

        var successCount = 0;
        var failureCount = 0;

        for (var balance : balances) {
            try {
                var wallet = walletRepository.findById(balance.walletId());

                if (wallet.isEmpty()) {
                    log.warn("Wallet not found for balanceId={} walletId={} — skipping sync",
                            balance.balanceId(), balance.walletId());
                    failureCount++;
                    continue;
                }

                var latestBlock = chainRpcProvider.getLatestBlockNumber(balance.chainId());

                if (latestBlock <= balance.lastIndexedBlock()) {
                    log.debug("No new blocks for balance {} (latest={}, indexed={})",
                            balance.balanceId(), latestBlock, balance.lastIndexedBlock());
                    continue;
                }

                var tokenContract = tokenContractResolver.resolveContract(
                        balance.chainId(), balance.stablecoin());
                var onChainBalance = chainRpcProvider.getTokenBalance(
                        balance.chainId(),
                        wallet.get().address(),
                        tokenContract
                );

                var updated = balance.syncFromChain(onChainBalance, latestBlock);
                walletBalanceRepository.save(updated);
                successCount++;

                log.debug("Synced balance for wallet {} chain {} {} — onChain={} available={} reserved={}",
                        balance.walletId(), balance.chainId().value(), balance.stablecoin().ticker(),
                        updated.blockchainBalance(), updated.availableBalance(), updated.reservedBalance());

            } catch (Exception e) {
                failureCount++;
                log.error("Failed to sync balance for balanceId={} walletId={}: {}",
                        balance.balanceId(), balance.walletId(), e.getMessage(), e);
            }
        }

        log.info("Balance sync completed — total={} synced={} failed={}",
                balances.size(), successCount, failureCount);
    }
}
