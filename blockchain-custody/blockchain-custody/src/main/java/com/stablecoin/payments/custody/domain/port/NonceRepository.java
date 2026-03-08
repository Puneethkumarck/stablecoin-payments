package com.stablecoin.payments.custody.domain.port;

import com.stablecoin.payments.custody.domain.model.ChainId;

import java.util.Optional;
import java.util.UUID;

/**
 * Port for nonce persistence and wallet-level serialization.
 * <p>
 * Implementations serialize nonce assignment for a given wallet to prevent
 * concurrent transactions from receiving the same nonce. The serialization
 * mechanism (advisory locks, row-level locks, etc.) is an infrastructure concern.
 */
public interface NonceRepository {

    /**
     * Atomically acquires a wallet-level lock, reads the current nonce, increments it
     * in the database, and returns the value <em>before</em> the increment (i.e. the
     * nonce to use for the next transaction).
     * <p>
     * If no nonce row exists for the wallet/chain pair, one is created with
     * {@code current_nonce = 1} and {@code 0} is returned.
     * <p>
     * Implementations must serialize concurrent calls for the same wallet/chain
     * pair to guarantee unique nonce assignment.
     *
     * @param walletId the wallet ID
     * @param chainId  the blockchain chain
     * @return the nonce to use for the next transaction
     */
    long assignNextNonce(UUID walletId, ChainId chainId);

    /**
     * Returns the current nonce counter for a wallet on a specific chain
     * without incrementing. Used for resubmit (replace-by-fee) scenarios
     * where the same nonce must be reused.
     *
     * @param walletId the wallet ID
     * @param chainId  the blockchain chain
     * @return the current nonce counter, or empty if no nonce row exists yet
     */
    Optional<Long> getCurrentNonce(UUID walletId, ChainId chainId);
}
