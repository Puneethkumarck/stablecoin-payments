package com.stablecoin.payments.custody.domain.model;

import lombok.Builder;

/**
 * A candidate chain evaluation result used during chain selection.
 * <p>
 * Each candidate records the fee estimate, finality time, health score,
 * the composite weighted score, and whether this candidate was ultimately selected.
 */
@Builder(toBuilder = true)
public record ChainCandidate(
        ChainId chainId,
        double feeUsd,
        int finalitySeconds,
        double healthScore,
        double score,
        boolean selected
) {}
