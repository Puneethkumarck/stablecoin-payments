package com.stablecoin.payments.custody.domain.model;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

/**
 * The result of a chain selection evaluation, containing the selected chain,
 * all evaluated candidates with their scores, and the transfer ID.
 */
@Builder(toBuilder = true)
public record ChainSelectionResult(
        ChainId selectedChain,
        List<ChainCandidate> candidates,
        UUID transferId
) {

    public ChainSelectionResult {
        if (selectedChain == null) {
            throw new IllegalArgumentException("selectedChain is required");
        }
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates must not be empty");
        }
        if (transferId == null) {
            throw new IllegalArgumentException("transferId is required");
        }
        candidates = List.copyOf(candidates);
    }
}
