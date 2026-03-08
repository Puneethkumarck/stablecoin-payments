package com.stablecoin.payments.custody.domain.model;

/**
 * Result of a nonce assignment operation.
 *
 * @param nonce the assigned nonce, or {@code null} for chains that do not use nonces (e.g. Solana)
 * @param source how the nonce was obtained
 */
public record NonceAssignment(
        Long nonce,
        NonceSource source
) {

    public enum NonceSource {
        /** Fresh nonce — incremented from the database */
        INCREMENTED,
        /** Reused nonce — same nonce for replace-by-fee */
        REUSED,
        /** Chain does not use nonces (e.g. Solana uses recent blockhash) */
        NOT_APPLICABLE
    }

    /**
     * Creates a nonce assignment for a chain that does not use nonces.
     */
    public static NonceAssignment notApplicable() {
        return new NonceAssignment(null, NonceSource.NOT_APPLICABLE);
    }

    /**
     * Creates a nonce assignment from a freshly incremented nonce.
     */
    public static NonceAssignment incremented(long nonce) {
        return new NonceAssignment(nonce, NonceSource.INCREMENTED);
    }

    /**
     * Creates a nonce assignment from a reused nonce (replace-by-fee).
     */
    public static NonceAssignment reused(long nonce) {
        return new NonceAssignment(nonce, NonceSource.REUSED);
    }
}
