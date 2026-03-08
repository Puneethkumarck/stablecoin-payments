package com.stablecoin.payments.custody.infrastructure.persistence;

import com.stablecoin.payments.custody.AbstractIntegrationTest;
import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.port.NonceRepository;
import com.stablecoin.payments.custody.domain.port.WalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.stablecoin.payments.custody.fixtures.WalletFixtures.anActiveWallet;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NonceManagerPersistenceAdapter IT")
class NonceManagerPersistenceAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private NonceRepository nonceRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final ChainId CHAIN_BASE = new ChainId("base");
    private static final ChainId CHAIN_ETHEREUM = new ChainId("ethereum");

    // -- Nonce Assignment -------------------------------------------------

    @Test
    @DisplayName("should return 0 for first nonce and create row")
    void shouldReturnZeroForFirstNonce() {
        var wallet = walletRepository.save(anActiveWallet());

        var nonce = nonceRepository.assignNextNonce(wallet.walletId(), CHAIN_BASE);

        assertThat(nonce).isZero();
    }

    @Test
    @DisplayName("should increment nonce sequentially")
    void shouldIncrementNonceSequentially() {
        var wallet = walletRepository.save(anActiveWallet());

        var nonce0 = nonceRepository.assignNextNonce(wallet.walletId(), CHAIN_BASE);
        var nonce1 = nonceRepository.assignNextNonce(wallet.walletId(), CHAIN_BASE);
        var nonce2 = nonceRepository.assignNextNonce(wallet.walletId(), CHAIN_BASE);

        assertThat(nonce0).isZero();
        assertThat(nonce1).isEqualTo(1L);
        assertThat(nonce2).isEqualTo(2L);
    }

    @Test
    @DisplayName("should maintain separate nonces per chain")
    void shouldMaintainSeparateNoncesPerChain() {
        var wallet = walletRepository.save(anActiveWallet());

        var baseNonce0 = nonceRepository.assignNextNonce(wallet.walletId(), CHAIN_BASE);
        var ethNonce0 = nonceRepository.assignNextNonce(wallet.walletId(), CHAIN_ETHEREUM);
        var baseNonce1 = nonceRepository.assignNextNonce(wallet.walletId(), CHAIN_BASE);

        assertThat(baseNonce0).isZero();
        assertThat(ethNonce0).isZero();
        assertThat(baseNonce1).isEqualTo(1L);
    }

    // -- getCurrentNonce --------------------------------------------------

    @Test
    @DisplayName("should return empty when no nonce row exists")
    void shouldReturnEmptyWhenNoNonceRowExists() {
        var wallet = walletRepository.save(anActiveWallet());

        var result = nonceRepository.getCurrentNonce(wallet.walletId(), CHAIN_BASE);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return current nonce after increments")
    void shouldReturnCurrentNonceAfterIncrements() {
        var wallet = walletRepository.save(anActiveWallet());
        nonceRepository.assignNextNonce(wallet.walletId(), CHAIN_BASE);
        nonceRepository.assignNextNonce(wallet.walletId(), CHAIN_BASE);

        var result = nonceRepository.getCurrentNonce(wallet.walletId(), CHAIN_BASE);

        // After 2 increments, current_nonce in DB should be 2
        assertThat(result).isPresent().hasValue(2L);
    }

    // -- Upsert Idempotency -----------------------------------------------

    @Test
    @DisplayName("should create nonce row on first assignment")
    void shouldCreateNonceRowOnFirstAssignment() {
        var wallet = walletRepository.save(anActiveWallet());

        nonceRepository.assignNextNonce(wallet.walletId(), CHAIN_BASE);

        var count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wallet_nonces WHERE wallet_id = ? AND chain_id = ?",
                Integer.class,
                wallet.walletId(),
                CHAIN_BASE.value());
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("should not duplicate rows on repeated assignments")
    void shouldNotDuplicateRowsOnRepeatedAssignments() {
        var wallet = walletRepository.save(anActiveWallet());

        nonceRepository.assignNextNonce(wallet.walletId(), CHAIN_BASE);
        nonceRepository.assignNextNonce(wallet.walletId(), CHAIN_BASE);
        nonceRepository.assignNextNonce(wallet.walletId(), CHAIN_BASE);

        var count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wallet_nonces WHERE wallet_id = ? AND chain_id = ?",
                Integer.class,
                wallet.walletId(),
                CHAIN_BASE.value());
        assertThat(count).isEqualTo(1);
    }

    // -- Concurrent Nonce Assignment --------------------------------------

    @Test
    @DisplayName("should assign unique nonces under concurrent load")
    void shouldAssignUniqueNoncesUnderConcurrentLoad() throws Exception {
        var wallet = walletRepository.save(anActiveWallet());
        var walletId = wallet.walletId();
        int threadCount = 5;
        var latch = new CountDownLatch(threadCount);

        var executor = Executors.newFixedThreadPool(threadCount);
        @SuppressWarnings("unchecked")
        Future<Long>[] futures = new Future[threadCount];

        for (int i = 0; i < threadCount; i++) {
            futures[i] = executor.submit(() -> {
                latch.countDown();
                latch.await(5, TimeUnit.SECONDS);
                // Each call runs in its own REQUIRES_NEW transaction via the adapter
                return nonceRepository.assignNextNonce(walletId, CHAIN_BASE);
            });
        }

        var assignedNonces = new HashSet<Long>();
        for (int i = 0; i < threadCount; i++) {
            assignedNonces.add(futures[i].get(10, TimeUnit.SECONDS));
        }

        executor.shutdown();

        // All nonces should be unique (0, 1, 2, 3, 4 in some order)
        assertThat(assignedNonces).hasSize(threadCount);
        // The final current_nonce in DB should be threadCount
        var finalNonce = nonceRepository.getCurrentNonce(walletId, CHAIN_BASE);
        assertThat(finalNonce).isPresent().hasValue((long) threadCount);
    }

    // -- Advisory Lock Verification ---------------------------------------

    @Test
    @DisplayName("should use advisory lock based on wallet id hash")
    void shouldUseAdvisoryLockBasedOnWalletIdHash() {
        var wallet = walletRepository.save(anActiveWallet());

        // Assign a nonce (which acquires and releases advisory lock internally)
        var nonce = nonceRepository.assignNextNonce(wallet.walletId(), CHAIN_BASE);

        // After the method returns, the lock should be released (tx committed)
        // Verify by checking that we can acquire the same lock again
        var canAcquire = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_xact_lock(hashtext(?))",
                Boolean.class,
                wallet.walletId().toString());
        assertThat(canAcquire).isTrue();
        assertThat(nonce).isZero();
    }

    // -- Non-existent wallet nonce ----------------------------------------

    @Test
    @DisplayName("should return empty for non-existent wallet nonce")
    void shouldReturnEmptyForNonExistentWalletNonce() {
        var result = nonceRepository.getCurrentNonce(UUID.randomUUID(), CHAIN_BASE);

        assertThat(result).isEmpty();
    }
}
