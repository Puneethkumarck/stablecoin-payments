package com.stablecoin.payments.custody.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TransferRequest(
        @NotNull(message = "paymentId is required")
        UUID paymentId,

        @NotNull(message = "correlationId is required")
        UUID correlationId,

        @NotBlank(message = "transferType is required")
        String transferType,

        UUID parentTransferId,

        @NotBlank(message = "stablecoin is required")
        String stablecoin,

        @NotBlank(message = "amount is required")
        @DecimalMin(value = "0.000001", message = "amount must be positive")
        String amount,

        @NotBlank(message = "toWalletAddress is required")
        String toWalletAddress,

        String preferredChain
) {}
