package com.stablecoin.payments.gateway.iam.application.service;

import com.stablecoin.payments.gateway.iam.api.request.CreateApiKeyRequest;
import com.stablecoin.payments.gateway.iam.api.response.ApiKeyResponse;
import com.stablecoin.payments.gateway.iam.domain.model.ApiKeyEnvironment;
import com.stablecoin.payments.gateway.iam.domain.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ApiKeyApplicationService {

    private final ApiKeyService apiKeyService;

    public ApiKeyResponse createApiKey(CreateApiKeyRequest request) {
        var environment = ApiKeyEnvironment.valueOf(request.environment().toUpperCase());
        Instant expiresAt = request.expiresInSeconds() != null
                ? Instant.now().plusSeconds(request.expiresInSeconds())
                : null;

        var result = apiKeyService.create(
                request.merchantId(),
                request.name(),
                environment,
                request.scopes() != null ? request.scopes() : Collections.emptyList(),
                request.allowedIps() != null ? request.allowedIps() : Collections.emptyList(),
                expiresAt);

        return new ApiKeyResponse(
                result.apiKey().getKeyId(),
                result.rawKey(),
                result.apiKey().getKeyPrefix(),
                result.apiKey().getName(),
                result.apiKey().getEnvironment().name(),
                result.apiKey().getScopes(),
                result.apiKey().getAllowedIps(),
                result.apiKey().getExpiresAt(),
                result.apiKey().getCreatedAt());
    }

    public void revokeApiKey(UUID keyId) {
        apiKeyService.revoke(keyId);
    }
}
