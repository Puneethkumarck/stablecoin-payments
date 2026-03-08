package com.stablecoin.payments.orchestrator.domain.workflow.dto;

import java.util.UUID;

/**
 * Signal sent to the PaymentWorkflow when the on-chain transfer is confirmed.
 * <p>
 * Phase 3 preparation — will be used when S4 Blockchain service is implemented.
 */
public record ChainConfirmedSignal(
        UUID paymentId,
        String txHash,
        String chainId,
        long blockNumber
) {}
