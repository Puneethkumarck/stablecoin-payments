package com.stablecoin.payments.merchant.iam.api.request;

import jakarta.validation.constraints.NotBlank;

public record MfaActivateRequest(
        @NotBlank String secret,
        @NotBlank String totpCode
) {}
