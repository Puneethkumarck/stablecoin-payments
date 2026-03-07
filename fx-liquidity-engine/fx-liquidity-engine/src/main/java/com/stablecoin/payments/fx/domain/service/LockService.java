package com.stablecoin.payments.fx.domain.service;

import com.stablecoin.payments.fx.domain.model.FxQuote;
import com.stablecoin.payments.fx.domain.model.FxRateLock;
import com.stablecoin.payments.fx.domain.model.LiquidityPool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class LockService {

    public record LockResult(FxQuote lockedQuote, FxRateLock lock, LiquidityPool updatedPool) {}

    public LockResult lockRate(FxQuote quote, UUID paymentId, UUID correlationId,
                                String sourceCountry, String targetCountry,
                                LiquidityPool pool) {
        log.info("Locking rate for quote={} payment={}", quote.quoteId(), paymentId);

        // Validate quote is lockable
        if (!quote.isActive()) {
            throw new IllegalStateException("Quote %s is not active (status=%s)"
                    .formatted(quote.quoteId(), quote.status()));
        }

        // Check liquidity
        if (!pool.hasSufficientLiquidity(quote.targetAmount())) {
            throw new IllegalStateException(
                    "Insufficient liquidity in pool %s for amount %s (available=%s)"
                            .formatted(pool.poolId(), quote.targetAmount(), pool.availableBalance()));
        }

        // Lock the quote
        var lockedQuote = quote.lock();

        // Create the rate lock
        var lock = FxRateLock.fromQuote(quote, paymentId, correlationId, sourceCountry, targetCountry);

        // Reserve liquidity
        var updatedPool = pool.reserve(quote.targetAmount());

        log.info("Locked rate: lock={} rate={} expires={}", lock.lockId(), lock.lockedRate(), lock.expiresAt());
        return new LockResult(lockedQuote, lock, updatedPool);
    }

    public FxRateLock consumeLock(FxRateLock lock, UUID paymentId) {
        log.info("Consuming lock={} for payment={}", lock.lockId(), paymentId);
        return lock.consume(paymentId);
    }

    public FxRateLock expireLock(FxRateLock lock) {
        log.info("Expiring lock={}", lock.lockId());
        return lock.expire();
    }
}
