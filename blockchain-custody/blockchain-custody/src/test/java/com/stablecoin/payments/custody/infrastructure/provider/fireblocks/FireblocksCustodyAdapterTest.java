package com.stablecoin.payments.custody.infrastructure.provider.fireblocks;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.stablecoin.payments.custody.domain.port.SignResult;
import com.stablecoin.payments.custody.domain.port.TransactionStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.stablecoin.payments.custody.fixtures.CustodyEngineFixtures.TRANSFER_ID;
import static com.stablecoin.payments.custody.fixtures.CustodyEngineFixtures.VAULT_ACCOUNT_ID;
import static com.stablecoin.payments.custody.fixtures.CustodyEngineFixtures.aSignRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FireblocksCustodyAdapterTest {

    private static WireMockServer wireMock;
    private static String testPrivateKeyPem;

    private FireblocksCustodyAdapter adapter;

    private static final String API_KEY = "fb-test-api-key";

    @BeforeAll
    static void startWireMock() throws Exception {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();

        var keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        var keyPair = keyGen.generateKeyPair();
        var privateKeyBytes = keyPair.getPrivate().getEncoded();
        var base64Key = Base64.getEncoder().encodeToString(privateKeyBytes);
        testPrivateKeyPem = "-----BEGIN PRIVATE KEY-----\n" + base64Key + "\n-----END PRIVATE KEY-----";
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        var properties = new FireblocksProperties(
                wireMock.baseUrl(),
                API_KEY,
                testPrivateKeyPem,
                VAULT_ACCOUNT_ID,
                10
        );
        adapter = new FireblocksCustodyAdapter(properties);
    }

    @Nested
    @DisplayName("signAndSubmit")
    class SignAndSubmit {

        @Test
        @DisplayName("should return SignResult on successful transaction creation")
        void shouldReturnSignResultOnSuccess() {
            // given
            wireMock.stubFor(post(urlEqualTo("/v1/transactions"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "id": "fb-tx-001",
                                      "status": "SUBMITTED"
                                    }
                                    """)));

            // when
            var result = adapter.signAndSubmit(aSignRequest());

            // then
            var expected = new SignResult(null, "fb-tx-001");
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("should send correct JSON body with assetId, source, destination, and amount")
        void shouldSendCorrectRequestBodyToFireblocks() {
            // given
            wireMock.stubFor(post(urlEqualTo("/v1/transactions"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "id": "fb-tx-verify",
                                      "status": "SUBMITTED"
                                    }
                                    """)));

            // when
            adapter.signAndSubmit(aSignRequest());

            // then
            wireMock.verify(postRequestedFor(urlEqualTo("/v1/transactions"))
                    .withRequestBody(containing("\"assetId\":\"USDC_BASE\""))
                    .withRequestBody(containing("\"type\":\"VAULT_ACCOUNT\""))
                    .withRequestBody(containing("\"id\":\"" + VAULT_ACCOUNT_ID + "\""))
                    .withRequestBody(containing("\"address\":\"0xRecipientAddress\""))
                    .withRequestBody(containing("\"amount\":\"1500.50\""))
                    .withRequestBody(containing("\"externalTxId\":\"" + TRANSFER_ID + "\""))
                    .withRequestBody(containing("\"note\":\"transfer_" + TRANSFER_ID + "\"")));
        }

        @Test
        @DisplayName("should include Authorization Bearer and X-API-Key headers")
        void shouldIncludeAuthHeadersOnSubmit() {
            // given
            wireMock.stubFor(post(urlEqualTo("/v1/transactions"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "id": "fb-tx-auth",
                                      "status": "SUBMITTED"
                                    }
                                    """)));

            // when
            adapter.signAndSubmit(aSignRequest());

            // then
            wireMock.verify(postRequestedFor(urlEqualTo("/v1/transactions"))
                    .withHeader("Authorization", matching("Bearer .+\\..+\\..+"))
                    .withHeader("X-API-Key", matching(API_KEY)));
        }

        @Test
        @DisplayName("should throw when Fireblocks returns 400 error")
        void shouldThrowOnApiError() {
            // given
            wireMock.stubFor(post(urlEqualTo("/v1/transactions"))
                    .willReturn(aResponse()
                            .withStatus(400)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "message": "Invalid asset ID",
                                      "code": 1000
                                    }
                                    """)));

            // when / then
            assertThatThrownBy(() -> adapter.signAndSubmit(aSignRequest()))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("getTransactionStatus")
    class GetTransactionStatus {

        @Test
        @DisplayName("should return TransactionStatus with COMPLETED status")
        void shouldReturnCompletedTransactionStatus() {
            // given
            wireMock.stubFor(get(urlEqualTo("/v1/transactions/fb-tx-001"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "id": "fb-tx-001",
                                      "status": "COMPLETED",
                                      "txHash": "0xabc123def456",
                                      "numOfConfirmations": 12
                                    }
                                    """)));

            // when
            var result = adapter.getTransactionStatus("fb-tx-001");

            // then
            var expected = new TransactionStatus("COMPLETED", "0xabc123def456", 12);
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("should return TransactionStatus with PENDING_SIGNATURE status")
        void shouldReturnPendingTransactionStatus() {
            // given
            wireMock.stubFor(get(urlEqualTo("/v1/transactions/fb-tx-002"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "id": "fb-tx-002",
                                      "status": "PENDING_SIGNATURE",
                                      "txHash": null,
                                      "numOfConfirmations": 0
                                    }
                                    """)));

            // when
            var result = adapter.getTransactionStatus("fb-tx-002");

            // then
            var expected = new TransactionStatus("PENDING_SIGNATURE", null, 0);
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("should throw when transaction not found")
        void shouldThrowWhenTransactionNotFound() {
            // given
            wireMock.stubFor(get(urlEqualTo("/v1/transactions/fb-tx-unknown"))
                    .willReturn(aResponse()
                            .withStatus(404)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "message": "Transaction not found",
                                      "code": 1001
                                    }
                                    """)));

            // when / then
            assertThatThrownBy(() -> adapter.getTransactionStatus("fb-tx-unknown"))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should include Authorization Bearer and X-API-Key headers on GET")
        void shouldIncludeAuthHeadersOnGetStatus() {
            // given
            wireMock.stubFor(get(urlEqualTo("/v1/transactions/fb-tx-auth"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "id": "fb-tx-auth",
                                      "status": "BROADCASTING",
                                      "txHash": "0xdef789",
                                      "numOfConfirmations": 0
                                    }
                                    """)));

            // when
            adapter.getTransactionStatus("fb-tx-auth");

            // then
            wireMock.verify(getRequestedFor(urlEqualTo("/v1/transactions/fb-tx-auth"))
                    .withHeader("Authorization", matching("Bearer .+\\..+\\..+"))
                    .withHeader("X-API-Key", matching(API_KEY)));
        }
    }
}
