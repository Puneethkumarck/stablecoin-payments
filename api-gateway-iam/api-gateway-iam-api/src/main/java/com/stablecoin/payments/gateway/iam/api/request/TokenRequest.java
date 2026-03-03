package com.stablecoin.payments.gateway.iam.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TokenRequest(
        @NotBlank String grantType,
        @NotNull UUID clientId,
        @NotBlank String clientSecret,
        String scope
) {}
