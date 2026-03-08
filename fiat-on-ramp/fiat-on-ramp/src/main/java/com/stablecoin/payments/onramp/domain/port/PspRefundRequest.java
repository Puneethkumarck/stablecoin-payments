package com.stablecoin.payments.onramp.domain.port;

import com.stablecoin.payments.onramp.domain.model.Money;

import java.util.UUID;

public record PspRefundRequest(
        UUID collectionId,
        String pspReference,
        Money refundAmount,
        String pspName,
        String reason
) {
}
