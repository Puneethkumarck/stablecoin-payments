package com.stablecoin.payments.custody.config;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.StablecoinTicker;
import com.stablecoin.payments.custody.domain.port.ChainFeeProvider;
import com.stablecoin.payments.custody.domain.port.ChainHealthProvider;
import com.stablecoin.payments.custody.domain.port.NonceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
