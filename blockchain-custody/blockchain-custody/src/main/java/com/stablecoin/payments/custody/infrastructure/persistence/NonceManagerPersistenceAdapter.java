package com.stablecoin.payments.custody.infrastructure.persistence;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.port.NonceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL-backed implementation of {@link NonceRepository}.
 * <p>
 * Uses {@code pg_advisory_xact_lock} for transaction-scoped locking to serialize
 * nonce assignment per wallet. The lock key is derived from the wallet UUID via
 * {@code hashtext()} to produce a stable 32-bit integer key. The lock is automatically
 * released when the transaction commits or rolls back.
 * <p>
 * The blocking advisory lock + upsert + atomic increment pattern guarantees
 * unique, sequential nonce assignment even under concurrent load. Concurrent
 * callers will wait until the lock is released rather than failing immediately.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class NonceManagerPersistenceAdapter implements NonceRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long assignNextNonce(UUID walletId, ChainId chainId) {
        // Acquire blocking transaction-scoped advisory lock (waits if held, auto-released at tx commit)
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtext(?))",
                rs -> { /* void function -- result set ignored */ },
                walletId.toString());

        log.debug("Advisory lock acquired for wallet={}", walletId);

        // Upsert: ensure the row exists
        jdbcTemplate.update(
                """
                INSERT INTO wallet_nonces (wallet_id, chain_id, current_nonce, updated_at)
                VALUES (?, ?, 0, NOW())
                ON CONFLICT (wallet_id, chain_id) DO NOTHING
                """,
                walletId,
                chainId.value());

        // Atomically read current value and increment, returning the pre-increment value
        Long nonce = jdbcTemplate.queryForObject(
                """
                UPDATE wallet_nonces
                SET current_nonce = current_nonce + 1,
                    updated_at = NOW()
                WHERE wallet_id = ? AND chain_id = ?
                RETURNING current_nonce - 1
                """,
                Long.class,
                walletId,
                chainId.value());

        if (nonce == null) {
            throw new IllegalStateException(
                    "Failed to acquire nonce for wallet %s on chain %s"
                            .formatted(walletId, chainId.value()));
        }

        log.debug("Assigned nonce={} for wallet={} on chain={}", nonce, walletId, chainId.value());
        return nonce;
    }

    @Override
    public Optional<Long> getCurrentNonce(UUID walletId, ChainId chainId) {
        var results = jdbcTemplate.queryForList(
                "SELECT current_nonce FROM wallet_nonces WHERE wallet_id = ? AND chain_id = ?",
                Long.class,
                walletId,
                chainId.value());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }
}
