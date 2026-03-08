package com.stablecoin.payments.fx.domain.port;

import com.stablecoin.payments.fx.domain.model.FxRateLock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FxRateLockRepository {
    FxRateLock save(FxRateLock lock);
    Optional<FxRateLock> findById(UUID lockId);
    Optional<FxRateLock> findByPaymentId(UUID paymentId);
    List<FxRateLock> findActiveLocksExpiredBefore(Instant cutoff);
}
