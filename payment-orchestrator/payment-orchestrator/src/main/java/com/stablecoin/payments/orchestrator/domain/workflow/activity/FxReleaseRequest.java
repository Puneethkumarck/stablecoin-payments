package com.stablecoin.payments.orchestrator.domain.workflow.activity;

import java.util.UUID;

/**
 * Request DTO for the FX lock release compensation activity.
 * <p>
 * Used during saga compensation to release a previously locked FX rate,
 * freeing the reserved liquidity pool balance.
 */
public record FxReleaseRequest(
        UUID lockId,
        UUID paymentId,
        String reason
) {}
