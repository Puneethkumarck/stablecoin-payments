package com.stablecoin.payments.custody.infrastructure.provider.solana;

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

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
class SolanaRpcAdapterTest {

    private static final ChainId SOLANA_CHAIN = new ChainId("solana");
    private static final String USDC_MINT = "4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU";
    private static final String TX_SIGNATURE = "5UfDuX7WXYZsyfBJGR6Lo9bLxLYbGo4cHbsiNPQeia7p4owLWbSMQoFgs4GZpmJQ4D3mYwYrLzH8vUxBnkGir1Mi";
    private static final String OWNER_ADDRESS = "7S3P4HxJpyyigGzodYwHtCxZyUQe9JiBMHyRWXArAaKv";

    private SolanaRpcAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        var properties = new SolanaChainProperties(
                true,
                wmRuntimeInfo.getHttpBaseUrl(),
                USDC_MINT,
                "confirmed",
                5000,
                10000
        );
        adapter = new SolanaRpcAdapter(properties);
    }

    @Nested
    @DisplayName("getTransactionReceipt")
    class GetTransactionReceipt {

        @Test
        @DisplayName("should return parsed transaction receipt with confirmations from slot delta")
        void shouldGetTransactionReceiptSuccessfully() {
            // given
            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("getTransaction")))
                    .willReturn(okJson("""
                            {"jsonrpc":"2.0","id":1,"result":{
                                "slot":100,
                                "blockTime":1700000000,
                                "meta":{
                                    "err":null,
                                    "fee":5000,
                                    "status":{"Ok":null}
                                },
                                "transaction":{
                                    "signatures":["%s"]
                                }
                            }}""".formatted(TX_SIGNATURE))));

            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("getSlot")))
                    .willReturn(okJson("""
                            {"jsonrpc":"2.0","id":1,"result":132}""")));

            // when
            var result = adapter.getTransactionReceipt(SOLANA_CHAIN, TX_SIGNATURE);

            // then
            var expected = new TransactionReceipt(
                    TX_SIGNATURE, 100L, true,
                    new BigDecimal("5000"), BigDecimal.ZERO, 32);
            assertThat(result).usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should return null when transaction not found (pending)")
        void shouldReturnNullWhenTransactionNotFound() {
            // given
            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("getTransaction")))
                    .willReturn(okJson("""
                            {"jsonrpc":"2.0","id":1,"result":null}""")));

            // when
            var result = adapter.getTransactionReceipt(SOLANA_CHAIN, TX_SIGNATURE);

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should detect failed transaction when meta.err is not null")
        void shouldDetectFailedTransaction() {
            // given
            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("getTransaction")))
                    .willReturn(okJson("""
                            {"jsonrpc":"2.0","id":1,"result":{
                                "slot":200,
                                "blockTime":1700000100,
                                "meta":{
                                    "err":{"InstructionError":[0,"InsufficientFunds"]},
                                    "fee":5000,
                                    "status":{"Err":{"InstructionError":[0,"InsufficientFunds"]}}
                                },
                                "transaction":{
                                    "signatures":["%s"]
                                }
                            }}""".formatted(TX_SIGNATURE))));

            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("getSlot")))
                    .willReturn(okJson("""
                            {"jsonrpc":"2.0","id":1,"result":210}""")));

            // when
            var result = adapter.getTransactionReceipt(SOLANA_CHAIN, TX_SIGNATURE);

            // then
            var expected = new TransactionReceipt(
                    TX_SIGNATURE, 200L, false,
                    new BigDecimal("5000"), BigDecimal.ZERO, 10);
            assertThat(result).usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("getLatestBlockNumber (getSlot)")
    class GetLatestBlockNumber {

        @Test
        @DisplayName("should return current slot number")
        void shouldGetCurrentSlot() {
            // given
            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("getSlot")))
                    .willReturn(okJson("""
                            {"jsonrpc":"2.0","id":1,"result":285630456}""")));

            // when
            var result = adapter.getLatestBlockNumber(SOLANA_CHAIN);

            // then
            assertThat(result).isEqualTo(285630456L);
        }
    }

    @Nested
    @DisplayName("getTokenBalance (getTokenAccountsByOwner)")
    class GetTokenBalance {

        @Test
        @DisplayName("should return USDC balance from SPL token account")
        void shouldGetTokenBalance() {
            // given
            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("getTokenAccountsByOwner")))
                    .willReturn(okJson("""
                            {"jsonrpc":"2.0","id":1,"result":{
                                "context":{"slot":100},
                                "value":[{
                                    "pubkey":"TokenAccountPubkey",
                                    "account":{
                                        "data":{
                                            "parsed":{
                                                "info":{
                                                    "mint":"%s",
                                                    "owner":"%s",
                                                    "tokenAmount":{
                                                        "amount":"100000000",
                                                        "decimals":6,
                                                        "uiAmount":100.0,
                                                        "uiAmountString":"100"
                                                    }
                                                },
                                                "type":"account"
                                            },
                                            "program":"spl-token",
                                            "space":165
                                        },
                                        "executable":false,
                                        "lamports":2039280,
                                        "owner":"TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
                                        "rentEpoch":0
                                    }
                                }]
                            }}""".formatted(USDC_MINT, OWNER_ADDRESS))));

            // when
            var result = adapter.getTokenBalance(SOLANA_CHAIN, OWNER_ADDRESS, USDC_MINT);

            // then
            var expected = new BigDecimal("100.000000");
            assertThat(result).usingComparator(BigDecimal::compareTo).isEqualTo(expected);
        }

        @Test
        @DisplayName("should return zero when no token accounts found")
        void shouldReturnZeroWhenNoTokenAccounts() {
            // given
            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("getTokenAccountsByOwner")))
                    .willReturn(okJson("""
                            {"jsonrpc":"2.0","id":1,"result":{
                                "context":{"slot":100},
                                "value":[]
                            }}""")));

            // when
            var result = adapter.getTokenBalance(SOLANA_CHAIN, OWNER_ADDRESS, USDC_MINT);

            // then
            assertThat(result).usingComparator(BigDecimal::compareTo)
                    .isEqualTo(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
        }

        @Test
        @DisplayName("should aggregate balance from multiple token accounts")
        void shouldAggregateMultipleTokenAccounts() {
            // given
            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("getTokenAccountsByOwner")))
                    .willReturn(okJson("""
                            {"jsonrpc":"2.0","id":1,"result":{
                                "context":{"slot":100},
                                "value":[{
                                    "pubkey":"TokenAccount1",
                                    "account":{
                                        "data":{
                                            "parsed":{
                                                "info":{
                                                    "mint":"%s",
                                                    "owner":"%s",
                                                    "tokenAmount":{
                                                        "amount":"75000000",
                                                        "decimals":6,
                                                        "uiAmount":75.0,
                                                        "uiAmountString":"75"
                                                    }
                                                },
                                                "type":"account"
                                            },
                                            "program":"spl-token",
                                            "space":165
                                        },
                                        "executable":false,
                                        "lamports":2039280,
                                        "owner":"TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
                                        "rentEpoch":0
                                    }
                                },{
                                    "pubkey":"TokenAccount2",
                                    "account":{
                                        "data":{
                                            "parsed":{
                                                "info":{
                                                    "mint":"%s",
                                                    "owner":"%s",
                                                    "tokenAmount":{
                                                        "amount":"25000000",
                                                        "decimals":6,
                                                        "uiAmount":25.0,
                                                        "uiAmountString":"25"
                                                    }
                                                },
                                                "type":"account"
                                            },
                                            "program":"spl-token",
                                            "space":165
                                        },
                                        "executable":false,
                                        "lamports":2039280,
                                        "owner":"TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
                                        "rentEpoch":0
                                    }
                                }]
                            }}""".formatted(USDC_MINT, OWNER_ADDRESS, USDC_MINT, OWNER_ADDRESS))));

            // when
            var result = adapter.getTokenBalance(SOLANA_CHAIN, OWNER_ADDRESS, USDC_MINT);

            // then
            var expected = new BigDecimal("100.000000");
            assertThat(result).usingComparator(BigDecimal::compareTo).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should throw SolanaRpcException on JSON-RPC error response")
        void shouldThrowOnRpcError() {
            // given
            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("getSlot")))
                    .willReturn(okJson("""
                            {"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"Method not found"}}""")));

            // when / then
            assertThatThrownBy(() -> adapter.getLatestBlockNumber(SOLANA_CHAIN))
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("Method not found");
        }

        @Test
        @DisplayName("should throw on server error (500)")
        void shouldThrowOnConnectionFailure() {
            // given
            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("getSlot")))
                    .willReturn(serverError()));

            // when / then
            assertThatThrownBy(() -> adapter.getLatestBlockNumber(SOLANA_CHAIN))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should throw SolanaRpcException when transaction RPC returns error")
        void shouldThrowOnTransactionRpcError() {
            // given
            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("getTransaction")))
                    .willReturn(okJson("""
                            {"jsonrpc":"2.0","id":1,"error":{"code":-32009,"message":"Transaction version (0) is not supported"}}""")));

            // when / then
            assertThatThrownBy(() -> adapter.getTransactionReceipt(SOLANA_CHAIN, TX_SIGNATURE))
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("Transaction version (0) is not supported");
        }

        @Test
        @DisplayName("should throw SolanaRpcException when token balance RPC returns error")
        void shouldThrowOnTokenBalanceRpcError() {
            // given
            stubFor(post("/")
                    .withRequestBody(matchingJsonPath("$.method", equalTo("getTokenAccountsByOwner")))
                    .willReturn(okJson("""
                            {"jsonrpc":"2.0","id":1,"error":{"code":-32602,"message":"Invalid param: could not find mint"}}""")));

            // when / then
            assertThatThrownBy(() -> adapter.getTokenBalance(SOLANA_CHAIN, OWNER_ADDRESS, USDC_MINT))
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("Invalid param: could not find mint");
        }
    }
}
