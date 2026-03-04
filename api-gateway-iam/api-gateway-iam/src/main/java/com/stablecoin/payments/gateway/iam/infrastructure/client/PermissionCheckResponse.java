package com.stablecoin.payments.gateway.iam.infrastructure.client;

public record PermissionCheckResponse(boolean allowed, String role, String via) {}
