package com.stablecoin.payments.offramp.infrastructure.provider.circle;

import com.stablecoin.payments.offramp.domain.port.RedemptionGateway;
import com.stablecoin.payments.offramp.domain.port.RedemptionRequest;
import com.stablecoin.payments.offramp.domain.port.RedemptionResult;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.redemption.provider", havingValue = "circle")
@EnableConfigurationProperties(CircleProperties.class)
public class CircleRedemptionAdapter implements RedemptionGateway {

    private final RestClient restClient;
    private final CircleProperties properties;

    public CircleRedemptionAdapter(CircleProperties properties) {
        this.properties = properties;

        var httpClient = HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .build();

        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    @CircuitBreaker(name = "circle", fallbackMethod = "redeemFallback")
    public RedemptionResult redeem(RedemptionRequest request) {
        log.info("[CIRCLE] Redeeming stablecoin payoutId={} stablecoin={} amount={}",
                request.payoutId(), request.stablecoin(), request.amount());

        var circleRequest = new CirclePayoutRequest(
                request.payoutId().toString(),
                new CirclePayoutRequest.CircleDestination("wire", properties.destinationId()),
                new CirclePayoutRequest.CircleAmount(request.amount().toPlainString(), "USD")
        );

        var response = restClient.post()
                .uri("/v1/businessAccount/payouts")
                .body(circleRequest)
                .retrieve()
                .body(CirclePayoutResponse.class);

        if (response == null || response.data() == null || response.data().amount() == null) {
            throw new IllegalStateException("Circle payout response missing required fields");
        }

        var payoutData = response.data();

        log.info("[CIRCLE] Redemption initiated payoutId={} circleRef={} status={} fiatAmount={} currency={}",
                request.payoutId(), payoutData.id(), payoutData.status(),
                payoutData.amount().amount(), payoutData.amount().currency());

        return new RedemptionResult(
                payoutData.id(),
                new BigDecimal(payoutData.amount().amount()),
                payoutData.amount().currency(),
                Instant.parse(payoutData.createDate())
        );
    }

    @SuppressWarnings("unused")
    private RedemptionResult redeemFallback(RedemptionRequest request, CallNotPermittedException ex) {
        log.error("[CIRCLE] Circuit breaker open — redemption failed payoutId={}",
                request.payoutId(), ex);
        throw new IllegalStateException("Circle redemption unavailable", ex);
    }
}
