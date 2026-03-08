package com.stablecoin.payments.custody.fixtures;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.NonceAssignment;

import java.util.UUID;

import static com.stablecoin.payments.custody.domain.model.NonceAssignment.NonceSource.INCREMENTED;
import static com.stablecoin.payments.custody.domain.model.NonceAssignment.NonceSource.NOT_APPLICABLE;
import static com.stablecoin.payments.custody.domain.model.NonceAssignment.NonceSource.REUSED;

public final class NonceFixtures {

    private NonceFixtures() {}

    public static final UUID WALLET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    public static final ChainId CHAIN_BASE = new ChainId("base");
    public static final ChainId CHAIN_ETHEREUM = new ChainId("ethereum");
    public static final ChainId CHAIN_SOLANA = new ChainId("solana");
    public static final ChainId CHAIN_POLYGON = new ChainId("polygon");

    /**
     * A nonce assignment for a fresh EVM nonce (incremented).
     */
    public static NonceAssignment anIncrementedAssignment(long nonce) {
        return new NonceAssignment(nonce, INCREMENTED);
    }

    /**
     * A nonce assignment for a resubmitted transaction (reused nonce).
     */
    public static NonceAssignment aReusedAssignment(long nonce) {
        return new NonceAssignment(nonce, REUSED);
    }

    /**
     * A nonce assignment for a non-nonce chain (e.g. Solana).
     */
    public static NonceAssignment aNotApplicableAssignment() {
        return new NonceAssignment(null, NOT_APPLICABLE);
    }
}
