package com.stablecoin.payments.custody.domain.service;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.NonceAssignment;
import com.stablecoin.payments.custody.domain.port.NonceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * Domain service that manages nonce assignment for blockchain wallets.
 * <p>
 * EVM chains (Ethereum, Base, Polygon, Avalanche, Tron) use an account-based nonce
 * model where each transaction from a wallet must carry a strictly incrementing nonce.
 * This service delegates to {@link NonceRepository} for serialized nonce assignment
 * to prevent two concurrent transactions from receiving the same nonce.
 * <p>
 * Non-EVM chains such as Solana use a different model (recent blockhash) and do not
 * require nonce management -- for those chains, {@link NonceAssignment#notApplicable()}
 * is returned.
 * <p>
 * Resubmission (replace-by-fee): when a transaction is resubmitted, the <em>same</em>
 * nonce is reused so that the new transaction replaces the stuck one in the mempool.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NonceManager {

    private static final Set<String> NONCE_BASED_CHAINS =
            Set.of("ethereum", "base", "polygon", "avalanche", "tron");

    private final NonceRepository nonceRepository;

    /**
     * Assigns a nonce for a wallet on the given chain.
     *
     * @param walletId   the wallet that will sign the transaction
     * @param chainId    the target blockchain chain
     * @param isResubmit {@code true} when resubmitting a stuck transaction (reuse nonce)
     * @return the nonce assignment result
     */
    public NonceAssignment assignNonce(UUID walletId, ChainId chainId, boolean isResubmit) {
        if (walletId == null) {
            throw new IllegalArgumentException("walletId is required");
        }
        if (chainId == null) {
            throw new IllegalArgumentException("chainId is required");
        }

        if (!isNonceBasedChain(chainId)) {
            log.debug("Chain {} does not use nonces -- returning NOT_APPLICABLE for wallet={}",
                    chainId.value(), walletId);
            return NonceAssignment.notApplicable();
        }

        if (isResubmit) {
            return handleResubmit(walletId, chainId);
        }
        return handleFreshNonce(walletId, chainId);
    }

    private NonceAssignment handleResubmit(UUID walletId, ChainId chainId) {
        var currentNonce = nonceRepository.getCurrentNonce(walletId, chainId);
        if (currentNonce.isEmpty()) {
            throw new IllegalStateException(
                    "No existing nonce found for wallet %s on chain %s -- cannot resubmit"
                            .formatted(walletId, chainId.value()));
        }
        // Resubmit reuses the last assigned nonce (current_nonce - 1)
        // because current_nonce in DB tracks the *next* nonce to use
        long nonceToReuse = currentNonce.get() - 1;
        log.info("Resubmit: reusing nonce={} for wallet={} on chain={}",
                nonceToReuse, walletId, chainId.value());
        return NonceAssignment.reused(nonceToReuse);
    }

    private NonceAssignment handleFreshNonce(UUID walletId, ChainId chainId) {
        long nonce = nonceRepository.assignNextNonce(walletId, chainId);
        log.info("Assigned fresh nonce={} for wallet={} on chain={}",
                nonce, walletId, chainId.value());
        return NonceAssignment.incremented(nonce);
    }

    private boolean isNonceBasedChain(ChainId chainId) {
        return NONCE_BASED_CHAINS.contains(chainId.value());
    }
}
