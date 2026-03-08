package com.stablecoin.payments.custody.infrastructure.provider.evm;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.port.TransactionReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class EvmRpcAdapterTest {

    private static final String USDC_CONTRACT = "0x036CbD53842c5426634e7929541eC2318f3dCF7e";
    private static final ChainId BASE_CHAIN = new ChainId("base");
    private static final String TX_HASH = "0xabc123def456789012345678901234567890123456789012345678901234abcd";

    private EvmRpcAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        var properties = new EvmChainProperties(true, Map.of(
                "base", new EvmChainProperties.ChainRpcConfig(
                        wmRuntimeInfo.getHttpBaseUrl(), 84532L,
                        USDC_CONTRACT, 5000, 10000)
        ));
        adapter = new EvmRpcAdapter(properties);
    }

    @Nested
    @DisplayName("getTransactionReceipt")
    class GetTransactionReceipt {

        @Test
        @DisplayName("should return parsed transaction receipt with confirmations")
        void shouldGetTransactionReceiptSuccessfully() {
            // given
            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("eth_getTransactionReceipt")))
                    .willReturn(okJson("""
                            {"jsonrpc":"2.0","id":1,"result":{
                                "blockNumber":"0x64",
                                "status":"0x1",
                                "gasUsed":"0x5208",
                                "effectiveGasPrice":"0x4a817c800",
                                "transactionHash":"%s"
                            }}""".formatted(TX_HASH))));

            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("eth_blockNumber")))
                    .willReturn(okJson("""
                            {"jsonrpc":"2.0","id":1,"result":"0x84"}""")));

            // when
            var result = adapter.getTransactionReceipt(BASE_CHAIN, TX_HASH);

            // then
            var expected = new TransactionReceipt(
                    TX_HASH, 100L, true,
                    new BigDecimal("21000"), new BigDecimal("20000000000"), 32);
            assertThat(result).usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should return null when receipt not found (pending transaction)")
        void shouldReturnNullWhenReceiptNotFound() {
            // given
            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("eth_getTransactionReceipt")))
                    .willReturn(okJson("""
                            {"jsonrpc":"2.0","id":1,"result":null}""")));

            // when
            var result = adapter.getTransactionReceipt(BASE_CHAIN, TX_HASH);

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should calculate confirmations as latestBlock minus txBlock")
        void shouldCalculateConfirmationsCorrectly() {
            // given
            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("eth_getTransactionReceipt")))
                    .willReturn(okJson("""
                            {"jsonrpc":"2.0","id":1,"result":{
                                "blockNumber":"0x64",
                                "status":"0x1",
                                "gasUsed":"0x5208",
                                "effectiveGasPrice":"0x3b9aca00",
                                "transactionHash":"%s"
                            }}""".formatted(TX_HASH))));

            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("eth_blockNumber")))
                    .willReturn(okJson("""
                            {"jsonrpc":"2.0","id":1,"result":"0x84"}""")));

            // when
            var result = adapter.getTransactionReceipt(BASE_CHAIN, TX_HASH);

            // then — block 0x64 = 100, latest 0x84 = 132, confirmations = 32
            var expected = new TransactionReceipt(
                    TX_HASH, 100L, true,
                    new BigDecimal("21000"), new BigDecimal("1000000000"), 32);
            assertThat(result).usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("getLatestBlockNumber")
    class GetLatestBlockNumber {

        @Test
        @DisplayName("should parse hex block number to long")
        void shouldGetLatestBlockNumber() {
            // given
            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("eth_blockNumber")))
                    .willReturn(okJson("""
                            {"jsonrpc":"2.0","id":1,"result":"0x1a4"}""")));

            // when
            var result = adapter.getLatestBlockNumber(BASE_CHAIN);

            // then
            assertThat(result).isEqualTo(420L);
        }
    }

    @Nested
    @DisplayName("getTokenBalance")
    class GetTokenBalance {

        @Test
        @DisplayName("should convert hex balance to BigDecimal with USDC decimals")
        void shouldGetTokenBalance() {
            // given — 0x5F5E100 = 100000000 raw units / 10^6 = 100.000000 USDC
            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("eth_call")))
                    .willReturn(okJson("""
                            {"jsonrpc":"2.0","id":1,"result":"0x5F5E100"}""")));

            // when
            var result = adapter.getTokenBalance(BASE_CHAIN,
                    "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD53", USDC_CONTRACT);

            // then
            var expected = new BigDecimal("100.000000");
            assertThat(result).usingComparator(BigDecimal::compareTo).isEqualTo(expected);
        }

        @Test
        @DisplayName("should return zero balance when hex result is 0x0")
        void shouldReturnZeroBalance() {
            // given
            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("eth_call")))
                    .willReturn(okJson("""
                            {"jsonrpc":"2.0","id":1,"result":"0x0"}""")));

            // when
            var result = adapter.getTokenBalance(BASE_CHAIN,
                    "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD53", USDC_CONTRACT);

            // then
            assertThat(result).usingComparator(BigDecimal::compareTo)
                    .isEqualTo(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should throw EvmRpcException on JSON-RPC error response")
        void shouldThrowOnRpcError() {
            // given
            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("eth_blockNumber")))
                    .willReturn(okJson("""
                            {"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"Method not found"}}""")));

            // when / then
            assertThatThrownBy(() -> adapter.getLatestBlockNumber(BASE_CHAIN))
                    .isInstanceOf(EvmRpcException.class)
                    .hasMessageContaining("Method not found");
        }

        @Test
        @DisplayName("should throw on server error (500)")
        void shouldThrowOnConnectionFailure() {
            // given
            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("eth_blockNumber")))
                    .willReturn(serverError()));

            // when / then
            assertThatThrownBy(() -> adapter.getLatestBlockNumber(BASE_CHAIN))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should throw when chain is not configured")
        void shouldThrowWhenChainNotConfigured() {
            // given
            var unknownChain = new ChainId("ethereum");

            // when / then
            assertThatThrownBy(() -> adapter.getLatestBlockNumber(unknownChain))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No RPC client configured for chain: ethereum");
        }
    }

    @Nested
    @DisplayName("Hex conversion utilities")
    class HexConversion {

        @Test
        @DisplayName("should convert hex string to long")
        void shouldConvertHexToLong() {
            // when
            var result = EvmRpcAdapter.hexToLong("0x64");

            // then
            assertThat(result).isEqualTo(100L);
        }

        @Test
        @DisplayName("should encode balanceOf call with padded address")
        void shouldEncodeBalanceOfCall() {
            // given
            var address = "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD53";

            // when
            var result = EvmRpcAdapter.encodeBalanceOfCall(address);

            // then
            assertThat(result).startsWith("0x70a08231");
            assertThat(result).hasSize(10 + 64); // 0x + 8 selector + 64 padded address
        }
    }
}
