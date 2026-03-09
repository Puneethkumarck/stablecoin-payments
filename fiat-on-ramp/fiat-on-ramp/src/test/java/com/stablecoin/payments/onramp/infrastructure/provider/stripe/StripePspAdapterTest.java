package com.stablecoin.payments.onramp.infrastructure.provider.stripe;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.stablecoin.payments.onramp.domain.model.AccountType;
import com.stablecoin.payments.onramp.domain.model.BankAccount;
import com.stablecoin.payments.onramp.domain.model.Money;
import com.stablecoin.payments.onramp.domain.model.PaymentRail;
import com.stablecoin.payments.onramp.domain.model.PaymentRailType;
import com.stablecoin.payments.onramp.domain.port.PspPaymentRequest;
import com.stablecoin.payments.onramp.domain.port.PspPaymentResult;
import com.stablecoin.payments.onramp.domain.port.PspRefundRequest;
import com.stablecoin.payments.onramp.domain.port.PspRefundResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StripePspAdapterTest {

    private static WireMockServer wireMock;
    private StripePspAdapter adapter;

    private static final UUID COLLECTION_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

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
        var properties = new StripeProperties(wireMock.baseUrl(), "sk_test_xxx", 10);
        adapter = new StripePspAdapter(properties);
    }

    private PspPaymentRequest aPaymentRequest() {
        return new PspPaymentRequest(
                COLLECTION_ID,
                new Money(new BigDecimal("250.00"), "USD"),
                new PaymentRail(PaymentRailType.ACH, "US", "USD"),
                new BankAccount("hash123", "021000021", AccountType.ACH_ROUTING, "US"),
                "stripe",
                COLLECTION_ID.toString()
        );
    }

    private PspRefundRequest aRefundRequest() {
        return new PspRefundRequest(
                COLLECTION_ID,
                "pi_test123",
                new Money(new BigDecimal("100.00"), "USD"),
                "stripe",
                "customer_request"
        );
    }

    @Nested
    @DisplayName("initiatePayment")
    class InitiatePayment {

        @Test
        @DisplayName("should return PspPaymentResult on successful payment intent creation")
        void initiatePayment_success() {
            wireMock.stubFor(post(urlEqualTo("/v1/payment_intents"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "id": "pi_test123",
                                      "status": "succeeded",
                                      "amount": 25000,
                                      "currency": "usd",
                                      "client_secret": "pi_test123_secret_xxx"
                                    }
                                    """)));

            var result = adapter.initiatePayment(aPaymentRequest());

            var expected = new PspPaymentResult("pi_test123", "succeeded");
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("should throw when Stripe returns 402 payment error")
        void initiatePayment_stripeError() {
            wireMock.stubFor(post(urlEqualTo("/v1/payment_intents"))
                    .willReturn(aResponse()
                            .withStatus(402)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "error": {
                                        "type": "card_error",
                                        "message": "Your card was declined."
                                      }
                                    }
                                    """)));

            assertThatThrownBy(() -> adapter.initiatePayment(aPaymentRequest()))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should throw on connection timeout")
        void initiatePayment_timeout() {
            var timeoutProperties = new StripeProperties(wireMock.baseUrl(), "sk_test_xxx", 1);
            var timeoutAdapter = new StripePspAdapter(timeoutProperties);

            wireMock.stubFor(post(urlEqualTo("/v1/payment_intents"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withFixedDelay(3000)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "id": "pi_test_late",
                                      "status": "succeeded",
                                      "amount": 25000,
                                      "currency": "usd",
                                      "client_secret": "pi_test_late_secret"
                                    }
                                    """)));

            assertThatThrownBy(() -> timeoutAdapter.initiatePayment(aPaymentRequest()))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should send correct form parameters to Stripe")
        void initiatePayment_verifiesRequestBody() {
            wireMock.stubFor(post(urlEqualTo("/v1/payment_intents"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "id": "pi_verify",
                                      "status": "requires_action",
                                      "amount": 25000,
                                      "currency": "usd",
                                      "client_secret": "pi_verify_secret"
                                    }
                                    """)));

            adapter.initiatePayment(aPaymentRequest());

            wireMock.verify(postRequestedFor(urlEqualTo("/v1/payment_intents"))
                    .withHeader("Idempotency-Key", equalTo(COLLECTION_ID.toString()))
                    .withRequestBody(containing("amount=25000"))
                    .withRequestBody(containing("currency=usd"))
                    .withRequestBody(containing("payment_method_types%5B%5D=us_bank_account"))
                    .withRequestBody(containing("confirm=true"))
                    .withRequestBody(containing("metadata%5Bcollection_id%5D=" + COLLECTION_ID)));
        }
    }

    @Nested
    @DisplayName("initiateRefund")
    class InitiateRefund {

        @Test
        @DisplayName("should return PspRefundResult on successful refund")
        void initiateRefund_success() {
            wireMock.stubFor(post(urlEqualTo("/v1/refunds"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "id": "re_test456",
                                      "status": "succeeded",
                                      "amount": 10000,
                                      "currency": "usd",
                                      "payment_intent": "pi_test123"
                                    }
                                    """)));

            var result = adapter.initiateRefund(aRefundRequest());

            var expected = new PspRefundResult("re_test456", "succeeded");
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("should throw when payment intent not found for refund")
        void initiateRefund_paymentIntentNotFound() {
            wireMock.stubFor(post(urlEqualTo("/v1/refunds"))
                    .willReturn(aResponse()
                            .withStatus(404)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "error": {
                                        "type": "invalid_request_error",
                                        "message": "No such payment_intent: 'pi_test123'"
                                      }
                                    }
                                    """)));

            assertThatThrownBy(() -> adapter.initiateRefund(aRefundRequest()))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should send correct form parameters for refund")
        void initiateRefund_verifiesRequestBody() {
            wireMock.stubFor(post(urlEqualTo("/v1/refunds"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "id": "re_verify",
                                      "status": "pending",
                                      "amount": 10000,
                                      "currency": "usd",
                                      "payment_intent": "pi_test123"
                                    }
                                    """)));

            adapter.initiateRefund(aRefundRequest());

            wireMock.verify(postRequestedFor(urlEqualTo("/v1/refunds"))
                    .withRequestBody(containing("payment_intent=pi_test123"))
                    .withRequestBody(containing("amount=10000"))
                    .withRequestBody(containing("reason=requested_by_customer"))
                    .withRequestBody(containing("metadata%5Bcollection_id%5D=" + COLLECTION_ID)));
        }
    }
}
