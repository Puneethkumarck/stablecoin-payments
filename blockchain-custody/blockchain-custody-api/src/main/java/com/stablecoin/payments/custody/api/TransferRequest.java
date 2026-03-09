package com.stablecoin.payments.custody.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

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
        @Pattern(regexp = "^\\d+(\\.\\d+)?$", message = "amount must be a valid decimal number")
        @DecimalMin(value = "0.000001", message = "amount must be positive")
        String amount,

        @NotBlank(message = "toWalletAddress is required")
        String toWalletAddress,

        String preferredChain
) {}
