package com.stablecoin.payments.onramp.infrastructure.provider.stripe;

import com.stablecoin.payments.onramp.domain.port.PspGateway;
import com.stablecoin.payments.onramp.domain.port.PspPaymentRequest;
import com.stablecoin.payments.onramp.domain.port.PspPaymentResult;
import com.stablecoin.payments.onramp.domain.port.PspRefundRequest;
import com.stablecoin.payments.onramp.domain.port.PspRefundResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.time.Duration;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.psp.provider", havingValue = "stripe")
@EnableConfigurationProperties(StripeProperties.class)
public class StripePspAdapter implements PspGateway {

    private final RestClient restClient;

    public StripePspAdapter(StripeProperties properties) {
        var httpClient = HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .build();

        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    @CircuitBreaker(name = "stripe", fallbackMethod = "initiatePaymentFallback")
    public PspPaymentResult initiatePayment(PspPaymentRequest request) {
        log.info("[STRIPE] Initiating payment collectionId={} amount={} currency={}",
                request.collectionId(), request.amount().amount(), request.amount().currency());

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("amount", toMinorUnits(request));
        formData.add("currency", request.amount().currency().toLowerCase());
        formData.add("payment_method_types[]", "us_bank_account");
        formData.add("confirm", "true");
        formData.add("metadata[collection_id]", request.collectionId().toString());

        var response = restClient.post()
                .uri("/v1/payment_intents")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(StripePaymentIntentResponse.class);

        log.info("[STRIPE] Payment initiated collectionId={} pspRef={} status={}",
                request.collectionId(), response.id(), response.status());

        return new PspPaymentResult(response.id(), response.status());
    }

    @Override
    @CircuitBreaker(name = "stripe", fallbackMethod = "initiateRefundFallback")
    public PspRefundResult initiateRefund(PspRefundRequest request) {
        log.info("[STRIPE] Initiating refund collectionId={} pspRef={} amount={}",
                request.collectionId(), request.pspReference(), request.refundAmount().amount());

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("payment_intent", request.pspReference());
        formData.add("amount", toMinorUnitsFromRefund(request));
        formData.add("reason", "requested_by_customer");
        formData.add("metadata[collection_id]", request.collectionId().toString());

        var response = restClient.post()
                .uri("/v1/refunds")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(StripeRefundResponse.class);

        log.info("[STRIPE] Refund initiated collectionId={} refundRef={} status={}",
                request.collectionId(), response.id(), response.status());

        return new PspRefundResult(response.id(), response.status());
    }

    @SuppressWarnings("unused")
    private PspPaymentResult initiatePaymentFallback(PspPaymentRequest request, Exception ex) {
        log.error("[STRIPE] Circuit breaker open — payment initiation failed collectionId={}",
                request.collectionId(), ex);
        throw new IllegalStateException("Stripe payment initiation unavailable", ex);
    }

    @SuppressWarnings("unused")
    private PspRefundResult initiateRefundFallback(PspRefundRequest request, Exception ex) {
        log.error("[STRIPE] Circuit breaker open — refund initiation failed collectionId={}",
                request.collectionId(), ex);
        throw new IllegalStateException("Stripe refund initiation unavailable", ex);
    }

    private String toMinorUnits(PspPaymentRequest request) {
        return String.valueOf(request.amount().amount().movePointRight(2).longValueExact());
    }

    private String toMinorUnitsFromRefund(PspRefundRequest request) {
        return String.valueOf(request.refundAmount().amount().movePointRight(2).longValueExact());
    }
}
