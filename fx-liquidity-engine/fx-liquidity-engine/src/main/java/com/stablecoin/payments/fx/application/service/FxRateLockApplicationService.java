package com.stablecoin.payments.fx.application.service;

import com.stablecoin.payments.fx.api.request.FxRateLockRequest;
import com.stablecoin.payments.fx.api.response.FxRateLockResponse;
import com.stablecoin.payments.fx.application.mapper.FxResponseMapper;
import com.stablecoin.payments.fx.domain.event.FxRateLocked;
import com.stablecoin.payments.fx.domain.exception.InsufficientLiquidityException;
import com.stablecoin.payments.fx.domain.exception.PoolNotFoundException;
import com.stablecoin.payments.fx.domain.exception.QuoteAlreadyLockedException;
import com.stablecoin.payments.fx.domain.exception.QuoteExpiredException;
import com.stablecoin.payments.fx.domain.exception.QuoteNotFoundException;
import com.stablecoin.payments.fx.domain.model.FxQuote;
import com.stablecoin.payments.fx.domain.model.FxQuoteStatus;
import com.stablecoin.payments.fx.domain.port.EventPublisher;
import com.stablecoin.payments.fx.domain.port.FxQuoteRepository;
import com.stablecoin.payments.fx.domain.port.FxRateLockRepository;
import com.stablecoin.payments.fx.domain.port.LiquidityPoolRepository;
import com.stablecoin.payments.fx.domain.service.LockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FxRateLockApplicationService {

    private final FxQuoteRepository quoteRepository;
    private final FxRateLockRepository lockRepository;
    private final LiquidityPoolRepository poolRepository;
    private final LockService lockService;
    private final EventPublisher<Object> eventPublisher;
    private final FxResponseMapper responseMapper;

    /**
     * Result of a lock-rate operation, indicating whether the lock was newly created
     * or returned from an existing idempotent match.
     */
    public record LockRateResult(FxRateLockResponse response, boolean created) {}

    @Transactional
    public LockRateResult lockRate(UUID quoteId, FxRateLockRequest request) {
        log.info("Locking rate for quote={} payment={}", quoteId, request.paymentId());

        // Idempotency: check if a lock already exists for this paymentId
        var existingLock = lockRepository.findByPaymentId(request.paymentId());
        if (existingLock.isPresent()) {
            log.info("Idempotent lock return for payment={} lockId={}",
                    request.paymentId(), existingLock.get().lockId());
            return new LockRateResult(responseMapper.toResponse(existingLock.get()), false);
        }

        // Load and validate quote
        var quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> QuoteNotFoundException.withId(quoteId));

        validateQuote(quote);

        // Load liquidity pool for corridor
        var pool = poolRepository.findByCorridor(quote.fromCurrency(), quote.toCurrency())
                .orElseThrow(() -> PoolNotFoundException.forCorridor(
                        quote.fromCurrency(), quote.toCurrency()));

        // Check sufficient liquidity
        if (!pool.hasSufficientLiquidity(quote.targetAmount())) {
            throw InsufficientLiquidityException.forCorridor(
                    quote.fromCurrency(), quote.toCurrency(),
                    quote.targetAmount(), pool.availableBalance());
        }

        // Delegate to domain service
        var lockResult = lockService.lockRate(
                quote, request.paymentId(), request.correlationId(),
                request.sourceCountry(), request.targetCountry(), pool);

        // Persist all changes
        quoteRepository.save(lockResult.lockedQuote());
        var savedLock = lockRepository.save(lockResult.lock());
        poolRepository.save(lockResult.updatedPool());

        // Publish domain event via outbox
        publishFxRateLockedEvent(savedLock, request.correlationId());

        log.info("Rate locked: lockId={} rate={} expires={}",
                savedLock.lockId(), savedLock.lockedRate(), savedLock.expiresAt());

        return new LockRateResult(responseMapper.toResponse(savedLock), true);
    }

    private void validateQuote(FxQuote quote) {
        if (quote.isExpired()) {
            throw QuoteExpiredException.withId(quote.quoteId());
        }
        if (quote.status() == FxQuoteStatus.LOCKED) {
            throw QuoteAlreadyLockedException.withId(quote.quoteId());
        }
    }

    private void publishFxRateLockedEvent(com.stablecoin.payments.fx.domain.model.FxRateLock lock,
                                           UUID correlationId) {
        var event = new FxRateLocked(
                lock.lockId(),
                lock.quoteId(),
                lock.paymentId(),
                correlationId,
                lock.fromCurrency(),
                lock.toCurrency(),
                lock.sourceAmount(),
                lock.targetAmount(),
                lock.lockedRate(),
                lock.feeBps(),
                lock.lockedAt(),
                lock.expiresAt()
        );
        eventPublisher.publish(event);
    }
}
