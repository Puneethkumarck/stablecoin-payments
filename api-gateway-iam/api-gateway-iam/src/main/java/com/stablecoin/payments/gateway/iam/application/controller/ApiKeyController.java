package com.stablecoin.payments.gateway.iam.application.controller;

import com.stablecoin.payments.gateway.iam.api.request.CreateApiKeyRequest;
import com.stablecoin.payments.gateway.iam.api.response.ApiKeyResponse;
import com.stablecoin.payments.gateway.iam.application.controller.mapper.GatewayRequestResponseMapper;
import com.stablecoin.payments.gateway.iam.domain.model.ApiKeyEnvironment;
import com.stablecoin.payments.gateway.iam.domain.service.ApiKeyCommandHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

import static com.stablecoin.payments.gateway.iam.application.security.SecurityExpressions.HAS_MERCHANT_ACCESS_VIA_API_KEY;
import static com.stablecoin.payments.gateway.iam.application.security.SecurityExpressions.HAS_MERCHANT_ACCESS_VIA_REQUEST;

@Slf4j
@RestController
@RequestMapping("/v1/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyCommandHandler apiKeyCommandHandler;
    private final GatewayRequestResponseMapper mapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(HAS_MERCHANT_ACCESS_VIA_REQUEST)
    public ApiKeyResponse createApiKey(@Valid @RequestBody CreateApiKeyRequest request) {
        log.info("Create API key merchantId={} name={} env={}",
                request.merchantId(), request.name(), request.environment());

        var environment = ApiKeyEnvironment.valueOf(request.environment().toUpperCase());
        Instant expiresAt = request.expiresInSeconds() != null
                ? Instant.now().plusSeconds(request.expiresInSeconds())
                : null;

        var result = apiKeyCommandHandler.create(
                request.merchantId(),
                request.name(),
                environment,
                request.scopes() != null ? request.scopes() : Collections.emptyList(),
                request.allowedIps() != null ? request.allowedIps() : Collections.emptyList(),
                expiresAt);

        return mapper.toApiKeyResponse(result);
    }

    @DeleteMapping("/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(HAS_MERCHANT_ACCESS_VIA_API_KEY)
    public void revokeApiKey(@PathVariable UUID keyId) {
        log.info("Revoke API key keyId={}", keyId);
        apiKeyCommandHandler.revoke(keyId);
    }
}
