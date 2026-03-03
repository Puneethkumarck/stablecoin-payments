package com.stablecoin.payments.gateway.iam.application.controller;

import com.stablecoin.payments.gateway.iam.api.request.CreateApiKeyRequest;
import com.stablecoin.payments.gateway.iam.api.response.ApiKeyResponse;
import com.stablecoin.payments.gateway.iam.application.service.ApiKeyApplicationService;
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

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyApplicationService apiKeyApplicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyResponse createApiKey(@Valid @RequestBody CreateApiKeyRequest request) {
        log.info("Create API key merchantId={} name={} env={}",
                request.merchantId(), request.name(), request.environment());
        return apiKeyApplicationService.createApiKey(request);
    }

    @DeleteMapping("/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeApiKey(@PathVariable UUID keyId) {
        log.info("Revoke API key keyId={}", keyId);
        apiKeyApplicationService.revokeApiKey(keyId);
    }
}
