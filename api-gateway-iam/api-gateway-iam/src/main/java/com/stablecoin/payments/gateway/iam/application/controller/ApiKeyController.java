package com.stablecoin.payments.gateway.iam.application.controller;

import com.stablecoin.payments.gateway.iam.api.request.CreateApiKeyRequest;
import com.stablecoin.payments.gateway.iam.api.response.ApiKeyResponse;
import com.stablecoin.payments.gateway.iam.domain.model.ApiKeyEnvironment;
import com.stablecoin.payments.gateway.iam.domain.service.ApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyResponse createApiKey(@Valid @RequestBody CreateApiKeyRequest request) {
        log.info("Create API key merchantId={} name={} env={}",
                request.merchantId(), request.name(), request.environment());

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

    @DeleteMapping("/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeApiKey(@PathVariable UUID keyId) {
        log.info("Revoke API key keyId={}", keyId);
        apiKeyService.revoke(keyId);
    }
}
