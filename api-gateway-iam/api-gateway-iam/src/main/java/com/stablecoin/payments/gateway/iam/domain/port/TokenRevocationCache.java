package com.stablecoin.payments.gateway.iam.domain.port;

import java.time.Duration;
import java.util.UUID;

public interface TokenRevocationCache {

    void markRevoked(UUID jti, Duration ttl);

    boolean isRevoked(UUID jti);
}
