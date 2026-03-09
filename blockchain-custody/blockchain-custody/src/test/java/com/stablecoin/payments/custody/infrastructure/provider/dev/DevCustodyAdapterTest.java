package com.stablecoin.payments.custody.infrastructure.provider.dev;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.stablecoin.payments.custody.domain.port.SignResult;
import com.stablecoin.payments.custody.domain.port.TransactionStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.stablecoin.payments.custody.fixtures.DevCustodyFixtures.aDevEthereumSignRequest;
import static com.stablecoin.payments.custody.fixtures.DevCustodyFixtures.aDevSignRequest;
import static com.stablecoin.payments.custody.fixtures.DevCustodyFixtures.aDevSolanaSignRequest;
import static com.stablecoin.payments.custody.fixtures.DevCustodyFixtures.aDevUnsupportedChainSignRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DevCustodyAdapterTest {

    private static WireMockServer wireMock;

    // Well-known test private key (do NOT use in production)
    private static final String TEST_PRIVATE_KEY =
            "4c0883a69102937d6231471b5dbb6204fe512961708279f44aee1b4fbc0debf1";

    private static final String TX_HASH =
            "0xabc123def456789012345678901234567890123456789012345678901234abcd";

    private DevCustodyAdapter adapter;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();

        var properties = new DevCustodyProperties(
                TEST_PRIVATE_KEY,
                84532L,
                11155111L,
                wireMock.baseUrl(),
                wireMock.baseUrl(),
                "https://api.devnet.solana.com",
                "0x036CbD53842c5426634e7929541eC2318f3dCF7e",
                "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238",
                1_000_000_000L,
                65000L,
                5000,
                10000
        );

        var httpClient = HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);

        var restClient = RestClient.builder()
                .baseUrl(wireMock.baseUrl())
                .requestFactory(requestFactory)
                .build();

        Map<String, RestClient> restClients = new ConcurrentHashMap<>();
        restClients.put("base", restClient);
        restClients.put("ethereum", restClient);

        adapter = new DevCustodyAdapter(properties, restClients);
    }

    @Nested
    @DisplayName("signAndSubmit — EVM")
    class SignAndSubmitEvm {

        @Test
        @DisplayName("should sign ERC-20 transfer and submit via eth_sendRawTransaction for Base")
        void shouldSignAndSubmitEvmTransactionForBase() {
            // given
            wireMock.stubFor(post(urlEqualTo("/"))
                    .withRequestBody(containing("eth_sendRawTransaction"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"2.0","id":1,"result":"%s"}
                                    """.formatted(TX_HASH))));

            // when
            var result = adapter.signAndSubmit(aDevSignRequest());

            // then
            var expected = new SignResult(TX_HASH, "ignored");
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("custodyTxId")
                    .isEqualTo(expected);
            assertThat(result.custodyTxId()).startsWith("dev-");
        }

        @Test
        @DisplayName("should sign ERC-20 transfer and submit for Ethereum chain")
        void shouldSignAndSubmitEvmTransactionForEthereum() {
            // given
            wireMock.stubFor(post(urlEqualTo("/"))
                    .withRequestBody(containing("eth_sendRawTransaction"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"2.0","id":1,"result":"%s"}
                                    """.formatted(TX_HASH))));

            // when
            var result = adapter.signAndSubmit(aDevEthereumSignRequest());

            // then
            var expected = new SignResult(TX_HASH, "ignored");
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("custodyTxId")
                    .isEqualTo(expected);
            assertThat(result.custodyTxId()).startsWith("dev-");
        }

        @Test
        @DisplayName("should throw DevCustodyException when RPC returns error")
        void shouldThrowWhenRpcReturnsError() {
            // given
            wireMock.stubFor(post(urlEqualTo("/"))
                    .withRequestBody(containing("eth_sendRawTransaction"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"2.0","id":1,"error":{"code":-32000,"message":"nonce too low"}}
                                    """)));

            // when / then
            assertThatThrownBy(() -> adapter.signAndSubmit(aDevSignRequest()))
                    .isInstanceOf(DevCustodyException.class)
                    .hasMessageContaining("nonce too low");
        }
    }

    @Nested
    @DisplayName("signAndSubmit — Solana")
    class SignAndSubmitSolana {

        @Test
        @DisplayName("should return simulated signature with warning for Solana")
        void shouldReturnSimulatedSignatureForSolana() {
            // when
            var result = adapter.signAndSubmit(aDevSolanaSignRequest());

            // then
            var expected = new SignResult("ignored", "ignored");
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("txHash", "custodyTxId")
                    .isEqualTo(expected);
            assertThat(result.txHash()).startsWith("sim-");
            assertThat(result.custodyTxId()).startsWith("dev-");
        }
    }

    @Nested
    @DisplayName("getTransactionStatus")
    class GetTransactionStatusTests {

        @Test
        @DisplayName("should return PENDING for unknown txId")
        void shouldReturnPendingForUnknownTxId() {
            // when
            var result = adapter.getTransactionStatus("dev-unknown-tx");

            // then
            var expected = new TransactionStatus("PENDING", null, 0);
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("should return SUBMITTED with txHash for known dev transaction")
        void shouldReturnSubmittedWithTxHashForKnownTransaction() {
            // given — first submit to populate mapping
            wireMock.stubFor(post(urlEqualTo("/"))
                    .withRequestBody(containing("eth_sendRawTransaction"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"jsonrpc":"2.0","id":1,"result":"%s"}
                                    """.formatted(TX_HASH))));

            var signResult = adapter.signAndSubmit(aDevSignRequest());

            // when
            var result = adapter.getTransactionStatus(signResult.custodyTxId());

            // then
            var expected = new TransactionStatus("SUBMITTED", TX_HASH, 0);
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("resolveEvmChainConfig")
    class ResolveChainConfig {

        @Test
        @DisplayName("should return Base Sepolia config for 'base' chain")
        void shouldResolveBaseSepoliaConfig() {
            // when
            var config = adapter.resolveEvmChainConfig("base");

            // then
            var expected = new DevCustodyAdapter.EvmChainConfig(
                    84532L,
                    wireMock.baseUrl(),
                    "0x036CbD53842c5426634e7929541eC2318f3dCF7e"
            );
            assertThat(config).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("should return Ethereum Sepolia config for 'ethereum' chain")
        void shouldResolveEthereumSepoliaConfig() {
            // when
            var config = adapter.resolveEvmChainConfig("ethereum");

            // then
            var expected = new DevCustodyAdapter.EvmChainConfig(
                    11155111L,
                    wireMock.baseUrl(),
                    "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238"
            );
            assertThat(config).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for unsupported chain")
        void shouldThrowForUnsupportedEvmChain() {
            // when / then
            assertThatThrownBy(() -> adapter.resolveEvmChainConfig("avalanche"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported EVM chain: avalanche");
        }
    }

    @Nested
    @DisplayName("unsupported chain via signAndSubmit")
    class UnsupportedChain {

        @Test
        @DisplayName("should throw IllegalArgumentException for unsupported chain in signAndSubmit")
        void shouldThrowForUnsupportedChainInSignAndSubmit() {
            // when / then
            assertThatThrownBy(() -> adapter.signAndSubmit(aDevUnsupportedChainSignRequest()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported chain for dev custody: stellar");
        }
    }

    @Nested
    @DisplayName("ERC-20 transfer encoding")
    class Erc20Encoding {

        @Test
        @DisplayName("should encode ERC-20 transfer with correct selector and padded params")
        void shouldEncodeErc20Transfer() {
            // when — amount is in minor units (100 USDC = 100_000_000)
            var result = DevCustodyAdapter.encodeErc20Transfer(
                    "0x1234567890AbCdEf1234567890aBcDeF12345678",
                    BigInteger.valueOf(100_000_000)
            );

            // then — starts with 0x + transfer selector a9059cbb, length = 2 + 8 + 64 + 64 = 138
            assertThat(result).startsWith("0xa9059cbb").hasSize(138);
        }

        @Test
        @DisplayName("should reject invalid EVM address")
        void shouldRejectInvalidEvmAddress() {
            // when / then
            assertThatThrownBy(() -> DevCustodyAdapter.encodeErc20Transfer("0xINVALID", BigInteger.ONE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid EVM address");
        }
    }
}
