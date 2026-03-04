package com.stablecoin.payments.gateway.iam.api.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateOAuthClientRequest(
        @NotBlank String name,
        List<String> scopes,
        List<String> grantTypes
) {}
