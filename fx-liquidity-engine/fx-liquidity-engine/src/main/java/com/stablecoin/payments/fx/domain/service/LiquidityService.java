package com.stablecoin.payments.fx.domain.service;

import com.stablecoin.payments.fx.domain.model.LiquidityPool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
public class LiquidityService {

    public LiquidityPool createPool(String fromCurrency, String toCurrency,
                                     BigDecimal initialBalance, BigDecimal minimumThreshold,
                                     BigDecimal maximumCapacity) {
        log.info("Creating liquidity pool for {}:{} balance={}", fromCurrency, toCurrency, initialBalance);
        return LiquidityPool.create(fromCurrency, toCurrency, initialBalance, minimumThreshold, maximumCapacity);
    }

    public LiquidityPool reserve(LiquidityPool pool, BigDecimal amount) {
        log.info("Reserving {} from pool={}", amount, pool.poolId());
        var updated = pool.reserve(amount);

        if (updated.isBelowThreshold()) {
            log.warn("Pool {} is below minimum threshold: available={}, threshold={}",
                    pool.poolId(), updated.availableBalance(), pool.minimumThreshold());
        }

        return updated;
    }

    public LiquidityPool release(LiquidityPool pool, BigDecimal amount) {
        log.info("Releasing {} to pool={}", amount, pool.poolId());
        return pool.release(amount);
    }

    public LiquidityPool consumeReservation(LiquidityPool pool, BigDecimal amount) {
        log.info("Consuming reservation {} from pool={}", amount, pool.poolId());
        return pool.consumeReservation(amount);
    }

    public boolean isBelowThreshold(LiquidityPool pool) {
        return pool.isBelowThreshold();
    }
}
