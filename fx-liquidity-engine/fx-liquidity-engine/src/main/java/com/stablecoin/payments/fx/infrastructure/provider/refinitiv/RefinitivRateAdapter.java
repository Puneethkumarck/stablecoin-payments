package com.stablecoin.payments.fx.infrastructure.provider.refinitiv;

import com.stablecoin.payments.fx.domain.model.CorridorRate;
import com.stablecoin.payments.fx.domain.port.RateProvider;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.fx.rate-provider", havingValue = "refinitiv")
@EnableConfigurationProperties(RefinitivProperties.class)
public class RefinitivRateAdapter implements RateProvider {

    private static final int DEFAULT_SPREAD_BPS = 30;
    private static final int DEFAULT_FEE_BPS = 30;

    private final RestClient restClient;

    public RefinitivRateAdapter(RefinitivProperties properties) {
        var httpClient = HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .build();

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    @Override
    @CircuitBreaker(name = "fxRate", fallbackMethod = "getRateFallback")
    public Optional<CorridorRate> getRate(String fromCurrency, String toCurrency) {
        log.info("[REFINITIV] Fetching rate for {}:{}", fromCurrency, toCurrency);

        var response = restClient.get()
                .uri("/data/pricing/v1/rates/{from}{to}", fromCurrency, toCurrency)
                .retrieve()
                .body(RefinitivRateResponse.class);

        if (response == null || response.mid() == null) {
            log.warn("[REFINITIV] No rate returned for {}:{}", fromCurrency, toCurrency);
            return Optional.empty();
        }

        int ageMs = response.timestamp() != null
                ? (int) (Instant.now().toEpochMilli() - response.timestamp())
                : 0;
        if (ageMs < 0) {
            ageMs = 0;
        }

        var corridorRate = CorridorRate.builder()
                .fromCurrency(fromCurrency)
                .toCurrency(toCurrency)
                .rate(response.mid())
                .spreadBps(DEFAULT_SPREAD_BPS)
                .feeBps(DEFAULT_FEE_BPS)
                .provider("refinitiv")
                .ageMs(Math.min(ageMs, 4999))
                .build();

        log.info("[REFINITIV] Rate fetched {}:{}={} ageMs={}", fromCurrency, toCurrency, response.mid(), ageMs);
        return Optional.of(corridorRate);
    }

    @Override
    public String providerName() {
        return "refinitiv";
    }

    @SuppressWarnings("unused")
    private Optional<CorridorRate> getRateFallback(String fromCurrency, String toCurrency, Exception ex) {
        log.error("[REFINITIV] Circuit breaker open for {}:{}", fromCurrency, toCurrency, ex);
        return Optional.empty();
    }
}
