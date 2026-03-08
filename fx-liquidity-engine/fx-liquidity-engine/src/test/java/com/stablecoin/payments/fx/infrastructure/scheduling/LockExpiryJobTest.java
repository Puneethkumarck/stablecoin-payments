package com.stablecoin.payments.fx.infrastructure.scheduling;

import com.stablecoin.payments.fx.domain.event.FxRateExpired;
import com.stablecoin.payments.fx.domain.model.FxRateLock;
import com.stablecoin.payments.fx.domain.model.FxRateLockStatus;
import com.stablecoin.payments.fx.infrastructure.messaging.OutboxEventPublisher;
import com.stablecoin.payments.fx.domain.port.FxRateLockRepository;
import com.stablecoin.payments.fx.domain.port.LiquidityPoolRepository;
import com.stablecoin.payments.fx.domain.service.LiquidityService;
import com.stablecoin.payments.fx.domain.service.LockService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.stablecoin.payments.fx.fixtures.FxRateLockFixtures.anActiveLock;
import static com.stablecoin.payments.fx.fixtures.LiquidityPoolFixtures.aUsdEurPool;
import static com.stablecoin.payments.fx.fixtures.TestUtils.eqIgnoringTimestamps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class LockExpiryJobTest {

    @Mock
    private FxRateLockRepository lockRepository;

    @Mock
    private LiquidityPoolRepository poolRepository;

    @Mock
    private LockService lockService;

    @Mock
    private LiquidityService liquidityService;

    @Mock
    private OutboxEventPublisher eventPublisher;

    @InjectMocks
    private LockExpiryJob lockExpiryJob;

    @Captor
    private ArgumentCaptor<FxRateExpired> eventCaptor;

    @Captor
    private ArgumentCaptor<Instant> cutoffCaptor;

    @Test
    @DisplayName("should expire stale locks and release pool reservations")
    void expiresStaleLocksAndReleasesReservations() {
        var quoteId = UUID.randomUUID();
        var lock = anActiveLock(quoteId);
        var expiredLock = new FxRateLock(
                lock.lockId(), lock.quoteId(), lock.paymentId(), lock.correlationId(),
                lock.fromCurrency(), lock.toCurrency(), lock.sourceAmount(), lock.targetAmount(),
                lock.lockedRate(), lock.feeBps(), lock.feeAmount(),
                lock.sourceCountry(), lock.targetCountry(), FxRateLockStatus.EXPIRED,
                lock.lockedAt(), lock.expiresAt(), null
        );
        var pool = aUsdEurPool().reserve(lock.targetAmount());
        var releasedPool = pool.release(lock.targetAmount());

        given(lockRepository.findActiveLocksExpiredBefore(cutoffCaptor.capture())).willReturn(List.of(lock));
        given(lockService.expireLock(lock)).willReturn(expiredLock);
        given(lockRepository.save(expiredLock)).willReturn(expiredLock);
        given(poolRepository.findByCorridor("USD", "EUR")).willReturn(Optional.of(pool));
        given(liquidityService.release(pool, lock.targetAmount())).willReturn(releasedPool);

        lockExpiryJob.expireStaleRateLocks();

        then(lockRepository).should().save(expiredLock);
        then(poolRepository).should().save(releasedPool);
        then(eventPublisher).should().publish(eventCaptor.capture());

        var event = eventCaptor.getValue();
        assertThat(event.lockId()).isEqualTo(lock.lockId());
        assertThat(event.paymentId()).isEqualTo(lock.paymentId());
    }

    @Test
    @DisplayName("should do nothing when no expired locks found")
    void noOpWhenNoExpiredLocks() {
        given(lockRepository.findActiveLocksExpiredBefore(cutoffCaptor.capture())).willReturn(List.of());

        lockExpiryJob.expireStaleRateLocks();

        then(lockService).should(never()).expireLock(eqIgnoringTimestamps(anActiveLock(UUID.randomUUID())));
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("should continue processing remaining locks when one fails")
    void continuesOnSingleLockFailure() {
        var lock1 = anActiveLock(UUID.randomUUID());
        var lock2 = anActiveLock(UUID.randomUUID());
        var expiredLock2 = new FxRateLock(
                lock2.lockId(), lock2.quoteId(), lock2.paymentId(), lock2.correlationId(),
                lock2.fromCurrency(), lock2.toCurrency(), lock2.sourceAmount(), lock2.targetAmount(),
                lock2.lockedRate(), lock2.feeBps(), lock2.feeAmount(),
                lock2.sourceCountry(), lock2.targetCountry(), FxRateLockStatus.EXPIRED,
                lock2.lockedAt(), lock2.expiresAt(), null
        );

        given(lockRepository.findActiveLocksExpiredBefore(cutoffCaptor.capture())).willReturn(List.of(lock1, lock2));
        given(lockService.expireLock(lock1)).willThrow(new RuntimeException("unexpected error"));
        given(lockService.expireLock(lock2)).willReturn(expiredLock2);
        given(lockRepository.save(expiredLock2)).willReturn(expiredLock2);
        given(poolRepository.findByCorridor("USD", "EUR")).willReturn(Optional.empty());

        lockExpiryJob.expireStaleRateLocks();

        then(lockRepository).should().save(expiredLock2);
        then(eventPublisher).should().publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().lockId()).isEqualTo(lock2.lockId());
    }

    @Test
    @DisplayName("should expire lock even if no pool exists for corridor")
    void expiresLockWithoutPool() {
        var lock = anActiveLock(UUID.randomUUID());
        var expiredLock = new FxRateLock(
                lock.lockId(), lock.quoteId(), lock.paymentId(), lock.correlationId(),
                lock.fromCurrency(), lock.toCurrency(), lock.sourceAmount(), lock.targetAmount(),
                lock.lockedRate(), lock.feeBps(), lock.feeAmount(),
                lock.sourceCountry(), lock.targetCountry(), FxRateLockStatus.EXPIRED,
                lock.lockedAt(), lock.expiresAt(), null
        );

        given(lockRepository.findActiveLocksExpiredBefore(cutoffCaptor.capture())).willReturn(List.of(lock));
        given(lockService.expireLock(lock)).willReturn(expiredLock);
        given(lockRepository.save(expiredLock)).willReturn(expiredLock);
        given(poolRepository.findByCorridor("USD", "EUR")).willReturn(Optional.empty());

        lockExpiryJob.expireStaleRateLocks();

        then(lockRepository).should().save(expiredLock);
        then(eventPublisher).should().publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().lockId()).isEqualTo(lock.lockId());
    }
}
