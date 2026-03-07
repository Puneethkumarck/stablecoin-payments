package com.stablecoin.payments.fx.infrastructure.messaging;

import com.stablecoin.payments.fx.domain.event.LiquidityThresholdBreached;
import com.stablecoin.payments.fx.domain.model.FxRateLock;
import com.stablecoin.payments.fx.domain.model.FxRateLockStatus;
import com.stablecoin.payments.fx.domain.model.LiquidityPool;
import com.stablecoin.payments.fx.domain.port.FxRateLockRepository;
import com.stablecoin.payments.fx.domain.port.LiquidityPoolRepository;
import com.stablecoin.payments.fx.domain.service.LiquidityService;
import com.stablecoin.payments.fx.domain.service.LockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class PaymentEventConsumerTest {

    private FxRateLockRepository lockRepository;
    private LiquidityPoolRepository poolRepository;
    private OutboxEventPublisher eventPublisher;
    private PaymentEventConsumer consumer;

    @BeforeEach
    void setUp() {
        lockRepository = mock(FxRateLockRepository.class);
        poolRepository = mock(LiquidityPoolRepository.class);
        eventPublisher = mock(OutboxEventPublisher.class);

        // Real domain services — pure logic, no dependencies
        var lockService = new LockService();
        var liquidityService = new LiquidityService();

        consumer = new PaymentEventConsumer(
                lockRepository, poolRepository,
                lockService, liquidityService, eventPublisher);
    }

    private static FxRateLock anActiveLock(UUID paymentId) {
        var now = Instant.now();
        return new FxRateLock(
                UUID.randomUUID(), UUID.randomUUID(), paymentId, UUID.randomUUID(),
                "USD", "EUR",
                new BigDecimal("1000.00"), new BigDecimal("920.00"), new BigDecimal("0.92"),
                30, new BigDecimal("3.00"),
                "US", "DE",
                FxRateLockStatus.ACTIVE,
                now, now.plusSeconds(30), null);
    }

    private static LiquidityPool aPool(BigDecimal available, BigDecimal reserved, BigDecimal threshold) {
        return new LiquidityPool(
                UUID.randomUUID(), "USD", "EUR",
                available, reserved,
                threshold, new BigDecimal("5000000.00"),
                Instant.now());
    }

    private static String paymentEvent(UUID paymentId) {
        return "{\"paymentId\":\"%s\"}".formatted(paymentId);
    }

    @Nested
    @DisplayName("payment.completed")
    class PaymentCompleted {

        @Test
        @DisplayName("should consume lock and pool reservation")
        void consumesLockAndReservation() {
            var paymentId = UUID.randomUUID();
            var lock = anActiveLock(paymentId);
            var pool = aPool(new BigDecimal("500000.00"), new BigDecimal("920.00"), new BigDecimal("100000.00"));

            given(lockRepository.findByPaymentId(paymentId)).willReturn(Optional.of(lock));
            given(poolRepository.findByCorridor("USD", "EUR")).willReturn(Optional.of(pool));
            given(lockRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(poolRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            consumer.onPaymentCompleted(paymentEvent(paymentId));

            then(lockRepository).should().save(any(FxRateLock.class));
            then(poolRepository).should().save(any(LiquidityPool.class));
            then(eventPublisher).should(never()).publish(any());
        }

        @Test
        @DisplayName("should publish threshold event when below minimum")
        void publishesThresholdEvent() {
            var paymentId = UUID.randomUUID();
            var lock = anActiveLock(paymentId);
            // Pool available=1000 after consuming 920 reservation stays at 1000, threshold=50000 — breach
            var pool = aPool(new BigDecimal("1000.00"), new BigDecimal("920.00"), new BigDecimal("50000.00"));

            given(lockRepository.findByPaymentId(paymentId)).willReturn(Optional.of(lock));
            given(poolRepository.findByCorridor("USD", "EUR")).willReturn(Optional.of(pool));
            given(lockRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(poolRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            consumer.onPaymentCompleted(paymentEvent(paymentId));

            then(eventPublisher).should().publish(any(LiquidityThresholdBreached.class));
        }

        @Test
        @DisplayName("should skip already consumed lock (idempotent)")
        void idempotentConsumed() {
            var paymentId = UUID.randomUUID();
            var lock = anActiveLock(paymentId);
            var consumedLock = new FxRateLock(
                    lock.lockId(), lock.quoteId(), lock.paymentId(), lock.correlationId(),
                    lock.fromCurrency(), lock.toCurrency(),
                    lock.sourceAmount(), lock.targetAmount(), lock.lockedRate(),
                    lock.feeBps(), lock.feeAmount(),
                    lock.sourceCountry(), lock.targetCountry(),
                    FxRateLockStatus.CONSUMED,
                    lock.lockedAt(), lock.expiresAt(), Instant.now());

            given(lockRepository.findByPaymentId(paymentId)).willReturn(Optional.of(consumedLock));

            consumer.onPaymentCompleted(paymentEvent(paymentId));

            then(lockRepository).should(never()).save(any());
            then(poolRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("should skip when no lock found")
        void noLockFound() {
            var paymentId = UUID.randomUUID();
            given(lockRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());

            consumer.onPaymentCompleted(paymentEvent(paymentId));

            then(lockRepository).should(never()).save(any());
            then(poolRepository).should(never()).findByCorridor(any(), any());
        }
    }

    @Nested
    @DisplayName("payment.failed")
    class PaymentFailed {

        @Test
        @DisplayName("should expire lock and release pool reservation")
        void expiresLockAndReleasesReservation() {
            var paymentId = UUID.randomUUID();
            var lock = anActiveLock(paymentId);
            var pool = aPool(new BigDecimal("500000.00"), new BigDecimal("920.00"), new BigDecimal("100000.00"));

            given(lockRepository.findByPaymentId(paymentId)).willReturn(Optional.of(lock));
            given(poolRepository.findByCorridor("USD", "EUR")).willReturn(Optional.of(pool));
            given(lockRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(poolRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            consumer.onPaymentFailed(paymentEvent(paymentId));

            then(lockRepository).should().save(any(FxRateLock.class));
            then(poolRepository).should().save(any(LiquidityPool.class));
        }

        @Test
        @DisplayName("should skip already expired lock (idempotent)")
        void idempotentExpired() {
            var paymentId = UUID.randomUUID();
            var lock = anActiveLock(paymentId);
            var expiredLock = new FxRateLock(
                    lock.lockId(), lock.quoteId(), lock.paymentId(), lock.correlationId(),
                    lock.fromCurrency(), lock.toCurrency(),
                    lock.sourceAmount(), lock.targetAmount(), lock.lockedRate(),
                    lock.feeBps(), lock.feeAmount(),
                    lock.sourceCountry(), lock.targetCountry(),
                    FxRateLockStatus.EXPIRED,
                    lock.lockedAt(), lock.expiresAt(), null);

            given(lockRepository.findByPaymentId(paymentId)).willReturn(Optional.of(expiredLock));

            consumer.onPaymentFailed(paymentEvent(paymentId));

            then(lockRepository).should(never()).save(any());
            then(poolRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("should skip when no lock found")
        void noLockFoundFailed() {
            var paymentId = UUID.randomUUID();
            given(lockRepository.findByPaymentId(paymentId)).willReturn(Optional.empty());

            consumer.onPaymentFailed(paymentEvent(paymentId));

            then(lockRepository).should(never()).save(any());
        }
    }
}
