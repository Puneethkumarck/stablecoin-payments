package com.stablecoin.payments.fx.infrastructure.messaging;

import com.stablecoin.payments.fx.domain.event.LiquidityThresholdBreached;
import com.stablecoin.payments.fx.domain.model.FxRateLockStatus;
import com.stablecoin.payments.fx.domain.port.FxRateLockRepository;
import com.stablecoin.payments.fx.domain.port.LiquidityPoolRepository;
import com.stablecoin.payments.fx.domain.service.LiquidityService;
import com.stablecoin.payments.fx.domain.service.LockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final FxRateLockRepository lockRepository;
    private final LiquidityPoolRepository poolRepository;
    private final LockService lockService;
    private final LiquidityService liquidityService;
    private final OutboxEventPublisher eventPublisher;

    @KafkaListener(topics = "payment.completed", groupId = "fx-liquidity-engine")
    @Transactional
    public void onPaymentCompleted(String message) {
        var event = parseEvent(message);
        log.info("[PAYMENT-EVENT] Processing payment.completed paymentId={}", event.paymentId());

        var lockOpt = lockRepository.findByPaymentId(event.paymentId());
        if (lockOpt.isEmpty()) {
            log.warn("[PAYMENT-EVENT] No lock found for paymentId={}", event.paymentId());
            return;
        }

        var lock = lockOpt.get();

        // Idempotency: already consumed
        if (lock.status() == FxRateLockStatus.CONSUMED) {
            log.info("[PAYMENT-EVENT] Lock already consumed lockId={} paymentId={}",
                    lock.lockId(), event.paymentId());
            return;
        }

        // Consume the lock
        var consumedLock = lockService.consumeLock(lock, event.paymentId());
        lockRepository.save(consumedLock);

        // Consume pool reservation
        var pool = poolRepository.findByCorridor(lock.fromCurrency(), lock.toCurrency())
                .orElseThrow(() -> new IllegalStateException(
                        "Pool not found for corridor %s:%s".formatted(lock.fromCurrency(), lock.toCurrency())));

        var updatedPool = liquidityService.consumeReservation(pool, lock.targetAmount());
        poolRepository.save(updatedPool);

        // Publish threshold breach event if needed
        if (updatedPool.isBelowThreshold()) {
            log.warn("[PAYMENT-EVENT] Liquidity threshold breached poolId={} available={} threshold={}",
                    updatedPool.poolId(), updatedPool.availableBalance(), updatedPool.minimumThreshold());
            eventPublisher.publish(new LiquidityThresholdBreached(
                    updatedPool.poolId(),
                    updatedPool.fromCurrency(),
                    updatedPool.toCurrency(),
                    updatedPool.availableBalance(),
                    updatedPool.minimumThreshold(),
                    Instant.now()));
        }

        log.info("[PAYMENT-EVENT] Completed payment.completed lockId={} paymentId={}",
                lock.lockId(), event.paymentId());
    }

    @KafkaListener(topics = "payment.failed", groupId = "fx-liquidity-engine")
    @Transactional
    public void onPaymentFailed(String message) {
        var event = parseEvent(message);
        log.info("[PAYMENT-EVENT] Processing payment.failed paymentId={}", event.paymentId());

        var lockOpt = lockRepository.findByPaymentId(event.paymentId());
        if (lockOpt.isEmpty()) {
            log.warn("[PAYMENT-EVENT] No lock found for paymentId={}", event.paymentId());
            return;
        }

        var lock = lockOpt.get();

        // Idempotency: already expired
        if (lock.status() == FxRateLockStatus.EXPIRED) {
            log.info("[PAYMENT-EVENT] Lock already expired lockId={} paymentId={}",
                    lock.lockId(), event.paymentId());
            return;
        }

        // Expire the lock
        var expiredLock = lockService.expireLock(lock);
        lockRepository.save(expiredLock);

        // Release pool reservation back to available
        var pool = poolRepository.findByCorridor(lock.fromCurrency(), lock.toCurrency())
                .orElseThrow(() -> new IllegalStateException(
                        "Pool not found for corridor %s:%s".formatted(lock.fromCurrency(), lock.toCurrency())));

        var updatedPool = liquidityService.release(pool, lock.targetAmount());
        poolRepository.save(updatedPool);

        log.info("[PAYMENT-EVENT] Released reservation lockId={} paymentId={} amount={}",
                lock.lockId(), event.paymentId(), lock.targetAmount());
    }

    private PaymentEvent parseEvent(String message) {
        try {
            return JSON_MAPPER.readValue(message, PaymentEvent.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse payment event: " + message, e);
        }
    }

    record PaymentEvent(UUID paymentId) {}
}
