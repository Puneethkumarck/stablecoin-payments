package com.stablecoin.payments.compliance.infrastructure.provider.chainalysis;

import java.util.List;

record ChainalysisTransferResponse(
        String updatedAt,
        String asset,
        String cluster,
        String rating,
        List<Alert> alerts
) {
    record Alert(
            String alertLevel,
            String category,
            String service,
            Long externalId
    ) {}
}
