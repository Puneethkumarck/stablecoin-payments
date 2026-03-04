package com.stablecoin.payments.merchant.onboarding.domain.merchant.model.core;

import java.time.Instant;

public record DocumentUploadResult(
        String uploadUrl,
        Instant expiresAt
) {}
