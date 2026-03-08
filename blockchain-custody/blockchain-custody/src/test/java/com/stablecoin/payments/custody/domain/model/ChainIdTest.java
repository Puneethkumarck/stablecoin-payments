package com.stablecoin.payments.custody.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ChainId")
class ChainIdTest {

    @Nested
    @DisplayName("Valid Chains")
    class ValidChains {

        @Test
        @DisplayName("accepts 'ethereum'")
        void acceptsEthereum() {
            var chainId = new ChainId("ethereum");
            assertThat(chainId.value()).isEqualTo("ethereum");
        }

        @Test
        @DisplayName("accepts 'solana'")
        void acceptsSolana() {
            var chainId = new ChainId("solana");
            assertThat(chainId.value()).isEqualTo("solana");
        }

        @Test
        @DisplayName("accepts 'stellar'")
        void acceptsStellar() {
            var chainId = new ChainId("stellar");
            assertThat(chainId.value()).isEqualTo("stellar");
        }

        @Test
        @DisplayName("accepts 'tron'")
        void acceptsTron() {
            var chainId = new ChainId("tron");
            assertThat(chainId.value()).isEqualTo("tron");
        }

        @Test
        @DisplayName("accepts 'base'")
        void acceptsBase() {
            var chainId = new ChainId("base");
            assertThat(chainId.value()).isEqualTo("base");
        }

        @Test
        @DisplayName("accepts 'polygon'")
        void acceptsPolygon() {
            var chainId = new ChainId("polygon");
            assertThat(chainId.value()).isEqualTo("polygon");
        }

        @Test
        @DisplayName("accepts 'avalanche'")
        void acceptsAvalanche() {
            var chainId = new ChainId("avalanche");
            assertThat(chainId.value()).isEqualTo("avalanche");
        }
    }

    @Nested
    @DisplayName("Invalid Values")
    class InvalidValues {

        @Test
        @DisplayName("rejects null value")
        void rejectsNull() {
            assertThatThrownBy(() -> new ChainId(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Chain ID value is required");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  ", "\t"})
        @DisplayName("rejects blank values")
        void rejectsBlank(String value) {
            assertThatThrownBy(() -> new ChainId(value))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Chain ID value is required");
        }

        @ParameterizedTest
        @ValueSource(strings = {"bitcoin", "cardano", "ripple", "ETHEREUM", "Base", "SOLANA"})
        @DisplayName("rejects unsupported chains")
        void rejectsUnsupported(String value) {
            assertThatThrownBy(() -> new ChainId(value))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported chain");
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("two ChainId with same value are equal")
        void equalWhenSameValue() {
            var a = new ChainId("base");
            var b = new ChainId("base");

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("two ChainId with different values are not equal")
        void notEqualWhenDifferentValue() {
            var a = new ChainId("base");
            var b = new ChainId("ethereum");

            assertThat(a).isNotEqualTo(b);
        }
    }
}
