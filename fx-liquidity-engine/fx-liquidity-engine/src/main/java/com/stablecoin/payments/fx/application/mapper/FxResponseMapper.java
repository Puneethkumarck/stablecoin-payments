package com.stablecoin.payments.fx.application.mapper;

import com.stablecoin.payments.fx.api.response.CorridorResponse;
import com.stablecoin.payments.fx.api.response.FxQuoteResponse;
import com.stablecoin.payments.fx.api.response.FxRateLockResponse;
import com.stablecoin.payments.fx.api.response.LiquidityPoolResponse;
import com.stablecoin.payments.fx.domain.model.CorridorRate;
import com.stablecoin.payments.fx.domain.model.FxQuote;
import com.stablecoin.payments.fx.domain.model.FxRateLock;
import com.stablecoin.payments.fx.domain.model.LiquidityPool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Component
public class FxResponseMapper {

    public FxQuoteResponse toResponse(FxQuote quote) {
        return new FxQuoteResponse(
                quote.quoteId(),
                quote.fromCurrency(),
                quote.toCurrency(),
                quote.sourceAmount(),
                quote.targetAmount(),
                quote.rate(),
                quote.inverseRate(),
                quote.feeBps(),
                quote.feeAmount(),
                quote.provider(),
                quote.createdAt(),
                quote.expiresAt()
        );
    }

    public FxRateLockResponse toResponse(FxRateLock lock) {
        return new FxRateLockResponse(
                lock.lockId(),
                lock.quoteId(),
                lock.paymentId(),
                lock.fromCurrency(),
                lock.toCurrency(),
                lock.sourceAmount(),
                lock.targetAmount(),
                lock.lockedRate(),
                lock.feeBps(),
                lock.feeAmount(),
                lock.lockedAt(),
                lock.expiresAt()
        );
    }

    public LiquidityPoolResponse toResponse(LiquidityPool pool) {
        var totalBalance = pool.totalBalance();
        var utilizationPct = BigDecimal.ZERO;
        if (totalBalance.compareTo(BigDecimal.ZERO) > 0) {
            utilizationPct = pool.reservedBalance()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(totalBalance, 1, RoundingMode.HALF_UP);
        }

        var status = pool.isBelowThreshold() ? "LOW" : "HEALTHY";

        return new LiquidityPoolResponse(
                pool.poolId(),
                pool.fromCurrency(),
                pool.toCurrency(),
                pool.availableBalance(),
                pool.reservedBalance(),
                pool.minimumThreshold(),
                pool.maximumCapacity(),
                utilizationPct,
                status,
                pool.updatedAt()
        );
    }

    public CorridorResponse toResponse(CorridorRate corridorRate) {
        return new CorridorResponse(
                corridorRate.fromCurrency(),
                corridorRate.toCurrency(),
                corridorRate.rate(),
                corridorRate.feeBps(),
                corridorRate.spreadBps(),
                corridorRate.provider(),
                Instant.now().minusMillis(corridorRate.ageMs())
        );
    }
}
