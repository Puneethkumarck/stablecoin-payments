package com.stablecoin.payments.orchestrator.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ChainId value object")
class ChainIdTest {

    @ParameterizedTest(name = "accepts supported chain: {0}")
    @ValueSource(strings = {"ethereum", "solana", "stellar", "tron", "base", "polygon"})
    @DisplayName("accepts all supported chains")
    void acceptsSupportedChains(String chain) {
        var chainId = new ChainId(chain);

        assertThat(chainId.value()).isEqualTo(chain);
    }

    @Test
    @DisplayName("rejects null value")
    void rejectsNullValue() {
        assertThatThrownBy(() -> new ChainId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Chain ID value is required");
    }

    @Test
    @DisplayName("rejects blank value")
    void rejectsBlankValue() {
        assertThatThrownBy(() -> new ChainId("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Chain ID value is required");
    }

    @Test
    @DisplayName("rejects empty value")
    void rejectsEmptyValue() {
        assertThatThrownBy(() -> new ChainId(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Chain ID value is required");
    }

    @Test
    @DisplayName("rejects unsupported chain")
    void rejectsUnsupportedChain() {
        assertThatThrownBy(() -> new ChainId("avalanche"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported chain: avalanche");
    }

    @Test
    @DisplayName("rejects uppercase chain name")
    void rejectsUppercaseChainName() {
        assertThatThrownBy(() -> new ChainId("ETHEREUM"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported chain: ETHEREUM");
    }

    @Test
    @DisplayName("rejects mixed case chain name")
    void rejectsMixedCaseChainName() {
        assertThatThrownBy(() -> new ChainId("Ethereum"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported chain: Ethereum");
    }
}
