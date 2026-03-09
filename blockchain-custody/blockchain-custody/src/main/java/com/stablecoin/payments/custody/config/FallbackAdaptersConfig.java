package com.stablecoin.payments.custody.config;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.StablecoinTicker;
import com.stablecoin.payments.custody.domain.port.ChainFeeProvider;
import com.stablecoin.payments.custody.domain.port.ChainHealthProvider;
import com.stablecoin.payments.custody.domain.port.ChainRpcProvider;
import com.stablecoin.payments.custody.domain.port.CustodyEngine;
import com.stablecoin.payments.custody.domain.port.NonceRepository;
import com.stablecoin.payments.custody.domain.port.SignRequest;
import com.stablecoin.payments.custody.domain.port.SignResult;
import com.stablecoin.payments.custody.domain.port.TransactionReceipt;
import com.stablecoin.payments.custody.domain.port.TransactionStatus;
import com.stablecoin.payments.custody.domain.port.TransferEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provides fallback (dev/test) implementations for external provider ports.
 * Each bean uses {@code @ConditionalOnMissingBean} so that production adapters
 * (activated via {@code @ConditionalOnProperty}) take precedence.
 */
@Slf4j
@Configuration
public class FallbackAdaptersConfig {

    private static final Map<String, Double> DEFAULT_FEES = Map.of(
            "base", 0.01,
            "ethereum", 2.50,
            "solana", 0.005
    );

    /**
     * Fallback health provider that returns 1.0 (healthy) for all chains.
     */
    @Bean
    @ConditionalOnMissingBean
    public ChainHealthProvider fallbackChainHealthProvider() {
        log.info("Using fallback ChainHealthProvider (all chains healthy)");
        return (ChainId chainId) -> 1.0;
    }

    /**
     * Fallback fee provider with realistic defaults:
     * Base=0.01, Ethereum=2.50, Solana=0.005 USD.
     */
    @Bean
    @ConditionalOnMissingBean
    public ChainFeeProvider fallbackChainFeeProvider() {
        log.info("Using fallback ChainFeeProvider (static fee estimates)");
        return (ChainId chainId, StablecoinTicker stablecoin) ->
                DEFAULT_FEES.getOrDefault(chainId.value(), 1.0);
    }

    /**
     * Fallback in-memory nonce repository for dev/test environments without PostgreSQL.
     * Uses a simple ConcurrentHashMap — no advisory locks (not needed without concurrency).
     */
    @Bean
    @ConditionalOnMissingBean
    public NonceRepository fallbackNonceRepository() {
        log.info("Using fallback NonceRepository (in-memory, no advisory locks)");
        return new InMemoryNonceRepository();
    }

    /**
     * Fallback custody engine for dev/test environments without Fireblocks.
     * Returns deterministic dev results.
     */
    @Bean
    @ConditionalOnMissingBean
    public CustodyEngine fallbackCustodyEngine() {
        log.info("Using fallback CustodyEngine (dev mode)");
        return new CustodyEngine() {
            @Override
            public SignResult signAndSubmit(SignRequest request) {
                log.warn("[FALLBACK-CUSTODY] Dev custody for transferId={}", request.transferId());
                return new SignResult(
                        "0x" + UUID.randomUUID().toString().replace("-", ""),
                        "dev-tx-" + UUID.randomUUID()
                );
            }

            @Override
            public TransactionStatus getTransactionStatus(String txId) {
                log.warn("[FALLBACK-CUSTODY] Dev status for txId={}", txId);
                return new TransactionStatus(
                        "COMPLETED",
                        "0x" + UUID.randomUUID().toString().replace("-", ""),
                        10
                );
            }
        };
    }

    /**
     * Fallback chain RPC provider for dev/test environments without EVM RPC nodes.
     * Returns deterministic mock data.
     */
    @Bean
    @ConditionalOnMissingBean
    public ChainRpcProvider fallbackChainRpcProvider() {
        log.info("Using fallback ChainRpcProvider (returns mock receipts)");
        return new ChainRpcProvider() {
            @Override
            public TransactionReceipt getTransactionReceipt(ChainId chainId, String txHash) {
                log.warn("[FALLBACK-RPC] Dev receipt for txHash={}", txHash);
                return new TransactionReceipt(
                        txHash, 100L, true,
                        BigDecimal.valueOf(21000), BigDecimal.valueOf(20), 10);
            }

            @Override
            public long getLatestBlockNumber(ChainId chainId) {
                log.warn("[FALLBACK-RPC] Dev block number for chain={}", chainId.value());
                return 1000L;
            }

            @Override
            public BigDecimal getTokenBalance(ChainId chainId, String address, String tokenContract) {
                log.warn("[FALLBACK-RPC] Dev balance for address={}", address);
                return BigDecimal.valueOf(500000);
            }
        };
    }

    /**
     * Fallback event publisher for dev/test environments without Kafka outbox.
     * Logs the event instead of publishing.
     * Gated by property to prevent silent event loss in production.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "app.transfer.event-publisher.fallback-enabled",
            havingValue = "true", matchIfMissing = true)
    public TransferEventPublisher fallbackTransferEventPublisher() {
        log.info("Using fallback TransferEventPublisher (log only)");
        return event -> log.warn("[FALLBACK-EVENT] Published event: {}", event);
    }

    static class InMemoryNonceRepository implements NonceRepository {

        private final ConcurrentHashMap<String, AtomicLong> nonces = new ConcurrentHashMap<>();

        @Override
        public Optional<Long> getCurrentNonce(UUID walletId, ChainId chainId) {
            var key = walletId + ":" + chainId.value();
            var counter = nonces.get(key);
            return counter == null ? Optional.empty() : Optional.of(counter.get());
        }

        @Override
        public long assignNextNonce(UUID walletId, ChainId chainId) {
            var key = walletId + ":" + chainId.value();
            var counter = nonces.computeIfAbsent(key, k -> new AtomicLong(0));
            return counter.getAndIncrement();
        }
    }
}
