package com.stablecoin.payments.custody.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ChainConfig")
class ChainConfigTest {

    private static final ChainId BASE = new ChainId("base");
    private static final List<String> RPC_ENDPOINTS = List.of("https://rpc.base.org", "https://rpc2.base.org");

    @Nested
    @DisplayName("Valid Construction")
    class ValidConstruction {

        @Test
        @DisplayName("creates chain config with all fields")
        void createsChainConfig() {
            var result = new ChainConfig(
                    BASE, 12, 2, "ETH", RPC_ENDPOINTS, "https://basescan.org"
            );

            var expected = new ChainConfig(
                    BASE, 12, 2, "ETH", RPC_ENDPOINTS, "https://basescan.org"
            );
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("accepts zero minConfirmations")
        void acceptsZeroConfirmations() {
            var config = new ChainConfig(
                    BASE, 0, 2, "ETH", RPC_ENDPOINTS, "https://basescan.org"
            );

            assertThat(config.minConfirmations()).isZero();
        }

        @Test
        @DisplayName("accepts zero avgFinalitySeconds")
        void acceptsZeroFinality() {
            var config = new ChainConfig(
                    BASE, 12, 0, "ETH", RPC_ENDPOINTS, "https://basescan.org"
            );

            assertThat(config.avgFinalitySeconds()).isZero();
        }

        @Test
        @DisplayName("accepts null explorerUrl")
        void acceptsNullExplorerUrl() {
            var config = new ChainConfig(
                    BASE, 12, 2, "ETH", RPC_ENDPOINTS, null
            );

            assertThat(config.explorerUrl()).isNull();
        }

        @Test
        @DisplayName("accepts single RPC endpoint")
        void acceptsSingleEndpoint() {
            var config = new ChainConfig(
                    BASE, 12, 2, "ETH", List.of("https://rpc.base.org"), null
            );

            assertThat(config.rpcEndpoints()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("rejects null chainId")
        void rejectsNullChainId() {
            assertThatThrownBy(() -> new ChainConfig(
                    null, 12, 2, "ETH", RPC_ENDPOINTS, null
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Chain ID is required");
        }

        @Test
        @DisplayName("rejects negative minConfirmations")
        void rejectsNegativeConfirmations() {
            assertThatThrownBy(() -> new ChainConfig(
                    BASE, -1, 2, "ETH", RPC_ENDPOINTS, null
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Min confirmations must be non-negative");
        }

        @Test
        @DisplayName("rejects negative avgFinalitySeconds")
        void rejectsNegativeFinality() {
            assertThatThrownBy(() -> new ChainConfig(
                    BASE, 12, -1, "ETH", RPC_ENDPOINTS, null
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Avg finality seconds must be non-negative");
        }

        @Test
        @DisplayName("rejects null nativeToken")
        void rejectsNullNativeToken() {
            assertThatThrownBy(() -> new ChainConfig(
                    BASE, 12, 2, null, RPC_ENDPOINTS, null
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Native token is required");
        }

        @Test
        @DisplayName("rejects blank nativeToken")
        void rejectsBlankNativeToken() {
            assertThatThrownBy(() -> new ChainConfig(
                    BASE, 12, 2, "  ", RPC_ENDPOINTS, null
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Native token is required");
        }

        @Test
        @DisplayName("rejects null rpcEndpoints")
        void rejectsNullEndpoints() {
            assertThatThrownBy(() -> new ChainConfig(
                    BASE, 12, 2, "ETH", null, null
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("At least one RPC endpoint is required");
        }

        @Test
        @DisplayName("rejects empty rpcEndpoints")
        void rejectsEmptyEndpoints() {
            assertThatThrownBy(() -> new ChainConfig(
                    BASE, 12, 2, "ETH", List.of(), null
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("At least one RPC endpoint is required");
        }
    }

    @Nested
    @DisplayName("Defensive Copy")
    class DefensiveCopy {

        @Test
        @DisplayName("rpcEndpoints are defensively copied")
        void endpointsDefensivelyCopied() {
            var mutableList = new ArrayList<>(List.of("https://rpc.base.org"));
            var config = new ChainConfig(BASE, 12, 2, "ETH", mutableList, null);

            assertThatThrownBy(() -> config.rpcEndpoints().add("https://evil.com"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
