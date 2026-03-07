package com.stablecoin.payments.compliance.infrastructure.provider.onfido;

import com.stablecoin.payments.compliance.domain.model.KycResult;
import com.stablecoin.payments.compliance.domain.model.KycStatus;
import com.stablecoin.payments.compliance.domain.model.KycTier;
import com.stablecoin.payments.compliance.domain.port.KycProvider;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.kyc.provider", havingValue = "onfido")
@EnableConfigurationProperties(OnfidoProperties.class)
public class OnfidoKycAdapter implements KycProvider {

    private static final String CACHE_KEY_PREFIX = "kyc:status:";

    private final RestClient restClient;
    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final Duration cacheTtl;

    public OnfidoKycAdapter(OnfidoProperties properties, StringRedisTemplate redisTemplate) {
        var httpClient = HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .build();

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Token token=" + properties.apiToken())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();

        this.redisTemplate = redisTemplate;
        this.jsonMapper = JsonMapper.builder().build();
        this.cacheTtl = Duration.ofMinutes(properties.cacheTtlMinutes());
    }

    @Override
    @CircuitBreaker(name = "kyc", fallbackMethod = "verifyFallback")
    public KycResult verify(UUID senderId, UUID recipientId) {
        log.info("[ONFIDO] KYC verification sender={} recipient={}", senderId, recipientId);

        var senderResult = resolveCustomerStatus(senderId);
        var recipientResult = resolveCustomerStatus(recipientId);

        var result = KycResult.builder()
                .kycResultId(UUID.randomUUID())
                .senderKycTier(senderResult.tier())
                .senderStatus(senderResult.status())
                .recipientStatus(recipientResult.status())
                .provider("onfido")
                .providerRef("onfido:" + senderId + "/" + recipientId)
                .checkedAt(Instant.now())
                .build();

        log.info("[ONFIDO] KYC result: senderStatus={} recipientStatus={}",
                senderResult.status(), recipientResult.status());
        return result;
    }

    private CustomerKycResult resolveCustomerStatus(UUID customerId) {
        var cached = getFromCache(customerId);
        if (cached != null) {
            log.debug("[ONFIDO] Cache hit for customer={}", customerId);
            return cached;
        }

        var kycResult = callOnfido(customerId);

        if (kycResult.status() == KycStatus.VERIFIED) {
            putInCache(customerId, kycResult);
        }

        return kycResult;
    }

    private CustomerKycResult callOnfido(UUID customerId) {
        var response = restClient.get()
                .uri("/checks?applicant_id={applicantId}", customerId.toString())
                .retrieve()
                .body(OnfidoCheckListResponse.class);

        if (response == null || response.checks() == null || response.checks().isEmpty()) {
            log.info("[ONFIDO] No checks found for customer={}", customerId);
            return new CustomerKycResult(KycStatus.UNVERIFIED, KycTier.KYC_TIER_1);
        }

        var latestCheck = response.checks().getFirst();
        return mapCheckResult(latestCheck);
    }

    private CustomerKycResult mapCheckResult(OnfidoCheckListResponse.OnfidoCheck check) {
        if ("clear".equals(check.result())) {
            return new CustomerKycResult(KycStatus.VERIFIED, KycTier.KYC_TIER_2);
        }
        if ("consider".equals(check.result())) {
            return new CustomerKycResult(KycStatus.PENDING, KycTier.KYC_TIER_1);
        }
        return new CustomerKycResult(KycStatus.UNVERIFIED, KycTier.KYC_TIER_1);
    }

    private CustomerKycResult getFromCache(UUID customerId) {
        try {
            var json = redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + customerId);
            if (json == null) {
                return null;
            }
            var cached = jsonMapper.readValue(json, CachedKycStatus.class);
            return new CustomerKycResult(
                    KycStatus.valueOf(cached.status()),
                    KycTier.valueOf(cached.tier()));
        } catch (Exception ex) {
            log.warn("[ONFIDO] Failed to read KYC cache for customer={}", customerId, ex);
            return null;
        }
    }

    private void putInCache(UUID customerId, CustomerKycResult result) {
        try {
            var cached = new CachedKycStatus(
                    result.status().name(),
                    result.tier().name(),
                    Instant.now().toEpochMilli());
            var json = jsonMapper.writeValueAsString(cached);
            redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + customerId, json, cacheTtl);
            log.debug("[ONFIDO] Cached KYC status for customer={} ttl={}min", customerId, cacheTtl.toMinutes());
        } catch (Exception ex) {
            log.warn("[ONFIDO] Failed to cache KYC status for customer={}", customerId, ex);
        }
    }

    @SuppressWarnings("unused")
    private KycResult verifyFallback(UUID senderId, UUID recipientId, Exception ex) {
        log.error("[ONFIDO] Circuit breaker open — KYC verification failed sender={} recipient={}",
                senderId, recipientId, ex);
        throw new IllegalStateException("KYC verification unavailable", ex);
    }

    private record CustomerKycResult(KycStatus status, KycTier tier) {}

    record CachedKycStatus(String status, String tier, long cachedAtMs) {}
}
