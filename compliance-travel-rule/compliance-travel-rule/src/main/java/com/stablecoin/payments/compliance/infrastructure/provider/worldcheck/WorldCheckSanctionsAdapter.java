package com.stablecoin.payments.compliance.infrastructure.provider.worldcheck;

import com.stablecoin.payments.compliance.domain.model.SanctionsResult;
import com.stablecoin.payments.compliance.domain.port.SanctionsProvider;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.sanctions.provider", havingValue = "world-check")
@EnableConfigurationProperties(WorldCheckProperties.class)
public class WorldCheckSanctionsAdapter implements SanctionsProvider {

    private static final List<String> LISTS_CHECKED = List.of("OFAC_SDN", "EU_CONSOLIDATED", "UN");

    private final RestClient restClient;

    public WorldCheckSanctionsAdapter(WorldCheckProperties properties) {
        var httpClient = HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .build();

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    @Override
    @CircuitBreaker(name = "sanctions", fallbackMethod = "screenFallback")
    public SanctionsResult screen(UUID senderId, UUID recipientId) {
        log.info("[WORLD-CHECK] Screening sender={} recipient={}", senderId, recipientId);

        var senderResponse = screenEntity(senderId.toString(), "INDIVIDUAL");
        var recipientResponse = screenEntity(recipientId.toString(), "INDIVIDUAL");

        boolean senderHit = hasMatch(senderResponse);
        boolean recipientHit = hasMatch(recipientResponse);

        var hitDetails = buildHitDetails(senderResponse, recipientResponse, senderHit, recipientHit);

        var result = SanctionsResult.builder()
                .sanctionsResultId(UUID.randomUUID())
                .senderScreened(true)
                .recipientScreened(true)
                .senderHit(senderHit)
                .recipientHit(recipientHit)
                .hitDetails(hitDetails)
                .listsChecked(LISTS_CHECKED)
                .provider("world-check")
                .providerRef(buildProviderRef(senderResponse, recipientResponse))
                .screenedAt(Instant.now())
                .build();

        if (senderHit || recipientHit) {
            log.warn("[WORLD-CHECK] Sanctions HIT detected sender={} senderHit={} recipient={} recipientHit={}",
                    senderId, senderHit, recipientId, recipientHit);
        } else {
            log.info("[WORLD-CHECK] No sanctions hits sender={} recipient={}", senderId, recipientId);
        }

        return result;
    }

    private WorldCheckScreeningResponse screenEntity(String entityId, String entityType) {
        var requestBody = Map.of(
                "groupId", "default",
                "entityType", entityType,
                "caseId", entityId,
                "name", entityId,
                "secondaryFields", List.of(
                        Map.of("typeId", "SFCT_1", "value", "SANCTIONS")
                )
        );

        return restClient.post()
                .uri("/cases/screeningRequest")
                .body(requestBody)
                .retrieve()
                .body(WorldCheckScreeningResponse.class);
    }

    private boolean hasMatch(WorldCheckScreeningResponse response) {
        if (response == null || response.results() == null) {
            return false;
        }
        return response.results().stream()
                .anyMatch(r -> "STRONG".equals(r.matchStrength()) || "EXACT".equals(r.matchStrength()));
    }

    private String buildHitDetails(WorldCheckScreeningResponse senderResp,
                                   WorldCheckScreeningResponse recipientResp,
                                   boolean senderHit, boolean recipientHit) {
        if (!senderHit && !recipientHit) {
            return null;
        }

        var details = new StringBuilder("{");
        if (senderHit && senderResp != null && senderResp.results() != null) {
            details.append("\"senderMatches\":[");
            var matches = senderResp.results().stream()
                    .filter(r -> "STRONG".equals(r.matchStrength()) || "EXACT".equals(r.matchStrength()))
                    .map(r -> "{\"matchStrength\":\"%s\",\"lists\":%s,\"categories\":%s}"
                            .formatted(r.matchStrength(), toJsonArray(r.matchedLists()), toJsonArray(r.categories())))
                    .toList();
            details.append(String.join(",", matches));
            details.append("]");
        }
        if (senderHit && recipientHit) {
            details.append(",");
        }
        if (recipientHit && recipientResp != null && recipientResp.results() != null) {
            details.append("\"recipientMatches\":[");
            var matches = recipientResp.results().stream()
                    .filter(r -> "STRONG".equals(r.matchStrength()) || "EXACT".equals(r.matchStrength()))
                    .map(r -> "{\"matchStrength\":\"%s\",\"lists\":%s,\"categories\":%s}"
                            .formatted(r.matchStrength(), toJsonArray(r.matchedLists()), toJsonArray(r.categories())))
                    .toList();
            details.append(String.join(",", matches));
            details.append("]");
        }
        details.append("}");
        return details.toString();
    }

    private String toJsonArray(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }
        return "[" + items.stream()
                .map(s -> "\"" + s + "\"")
                .reduce((a, b) -> a + "," + b)
                .orElse("") + "]";
    }

    private String buildProviderRef(WorldCheckScreeningResponse senderResp,
                                    WorldCheckScreeningResponse recipientResp) {
        var senderRef = senderResp != null ? senderResp.caseSystemId() : "unknown";
        var recipientRef = recipientResp != null ? recipientResp.caseSystemId() : "unknown";
        return "wc:%s/%s".formatted(senderRef, recipientRef);
    }

    @SuppressWarnings("unused")
    private SanctionsResult screenFallback(UUID senderId, UUID recipientId, Exception ex) {
        log.error("[WORLD-CHECK] Circuit breaker open — screening failed sender={} recipient={}",
                senderId, recipientId, ex);
        throw new IllegalStateException("Sanctions screening unavailable", ex);
    }
}
