package com.stablecoin.payments.fx.infrastructure.scheduling;

import com.stablecoin.payments.fx.domain.event.FxRateExpired;
import com.stablecoin.payments.fx.domain.model.FxRateLock;
import com.stablecoin.payments.fx.domain.port.FxRateLockRepository;
import com.stablecoin.payments.fx.domain.port.LiquidityPoolRepository;
import com.stablecoin.payments.fx.domain.service.LiquidityService;
import com.stablecoin.payments.fx.domain.service.LockService;
import com.stablecoin.payments.fx.infrastructure.messaging.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.fx.lock-expiry.enabled", havingValue = "true", matchIfMissing = true)
public class LockExpiryJob {

    private final FxRateLockRepository lockRepository;
    private final LiquidityPoolRepository poolRepository;
    private final LockService lockService;
    private final LiquidityService liquidityService;
    private final OutboxEventPublisher eventPublisher;

    @Scheduled(fixedDelayString = "${app.fx.lock-expiry.interval-ms:5000}")
    @Transactional
    public void expireStaleRateLocks() {
        var now = Instant.now();
        var expiredLocks = lockRepository.findActiveLocksExpiredBefore(now);

        if (expiredLocks.isEmpty()) {
            return;
        }

        log.info("Found {} expired active locks to process", expiredLocks.size());

        for (var lock : expiredLocks) {
            try {
                expireLock(lock);
            } catch (Exception e) {
                log.error("Failed to expire lock {}: {}", lock.lockId(), e.getMessage());
            }
        }
    }

    private void expireLock(FxRateLock lock) {
        var expired = lockService.expireLock(lock);
        lockRepository.save(expired);

        releasePoolReservation(lock);

        var event = new FxRateExpired(lock.lockId(), lock.paymentId(), Instant.now());
        eventPublisher.publish(event);

        log.info("Expired lock {} for payment {}, released {} {} reservation",
                lock.lockId(), lock.paymentId(), lock.targetAmount(), lock.toCurrency());
    }

    private void releasePoolReservation(FxRateLock lock) {
        poolRepository.findByCorridor(lock.fromCurrency(), lock.toCurrency())
                .ifPresent(pool -> {
                    var released = liquidityService.release(pool, lock.targetAmount());
                    poolRepository.save(released);
                });
    }
}
