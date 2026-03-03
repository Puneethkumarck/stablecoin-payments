package com.stablecoin.payments.gateway.iam.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateApiKeyRequest(
        @NotNull UUID merchantId,
        @NotBlank String name,
        @NotBlank String environment,
        List<String> scopes,
        List<String> allowedIps,
        Long expiresInSeconds
) {}
