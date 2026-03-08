package com.stablecoin.payments.custody.domain.port;

import com.stablecoin.payments.custody.domain.model.ChainSelectionResult;

import java.util.UUID;

/**
 * Port for persisting chain selection evaluation results.
 */
public interface ChainSelectionLogRepository {

    /**
     * Saves the chain selection result for the given transfer.
     *
     * @param transferId the transfer for which chain was selected
     * @param result     the selection result including all scored candidates
     */
    void save(UUID transferId, ChainSelectionResult result);
}
