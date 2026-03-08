package com.stablecoin.payments.custody.domain.port;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.StablecoinTicker;

import java.math.BigDecimal;
import java.util.UUID;

public record SignRequest(
        UUID transferId,
        ChainId chainId,
        String fromAddress,
        String toAddress,
        BigDecimal amount,
        StablecoinTicker stablecoin,
        Long nonce,
        String vaultAccountId
) {}
