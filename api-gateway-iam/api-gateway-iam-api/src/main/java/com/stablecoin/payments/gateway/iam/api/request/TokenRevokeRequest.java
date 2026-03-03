package com.stablecoin.payments.gateway.iam.api.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TokenRevokeRequest(
        @NotNull UUID jti
) {}
