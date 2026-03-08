package com.stablecoin.payments.custody.fixtures;

import com.stablecoin.payments.custody.domain.model.ChainConfig;
import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.ChainSelectionWeights;
import com.stablecoin.payments.custody.domain.model.StablecoinTicker;
import com.stablecoin.payments.custody.domain.service.ChainSelectionEngine.ChainSelectionRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Test fixtures for chain selection engine tests.
 */
public final class ChainSelectionFixtures {

    private ChainSelectionFixtures() {}

    public static final UUID TRANSFER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    public static final StablecoinTicker USDC = StablecoinTicker.of("USDC");
    public static final BigDecimal AMOUNT = new BigDecimal("1000.00");

    public static final ChainId CHAIN_BASE = new ChainId("base");
    public static final ChainId CHAIN_ETHEREUM = new ChainId("ethereum");
    public static final ChainId CHAIN_SOLANA = new ChainId("solana");

    /**
     * Default weights (cost=0.4, speed=0.35, reliability=0.25).
     */
    public static ChainSelectionWeights defaultWeights() {
        return ChainSelectionWeights.defaults();
    }

    /**
     * Base chain configuration: fast and cheap (L2).
     */
    public static ChainConfig baseConfig() {
        return new ChainConfig(
                CHAIN_BASE, 1, 12, "ETH",
                List.of("https://base-rpc.example.com"), "https://basescan.org");
    }

    /**
     * Ethereum chain configuration: slow and expensive (L1).
     */
    public static ChainConfig ethereumConfig() {
        return new ChainConfig(
                CHAIN_ETHEREUM, 32, 300, "ETH",
                List.of("https://eth-rpc.example.com"), "https://etherscan.io");
    }

    /**
     * Solana chain configuration: fastest and cheapest.
     */
    public static ChainConfig solanaConfig() {
        return new ChainConfig(
                CHAIN_SOLANA, 1, 5, "SOL",
                List.of("https://sol-rpc.example.com"), "https://explorer.solana.com");
    }

    /**
     * A selection request with default values (no preferred chain).
     */
    public static ChainSelectionRequest aSelectionRequest() {
        return new ChainSelectionRequest(TRANSFER_ID, USDC, AMOUNT, null);
    }

    /**
     * A selection request with a preferred chain.
     */
    public static ChainSelectionRequest aSelectionRequestWithPreferredChain(String preferredChain) {
        return new ChainSelectionRequest(TRANSFER_ID, USDC, AMOUNT, preferredChain);
    }

    /**
     * A selection request with a specific amount.
     */
    public static ChainSelectionRequest aSelectionRequestWithAmount(BigDecimal amount) {
        return new ChainSelectionRequest(TRANSFER_ID, USDC, amount, null);
    }
}
