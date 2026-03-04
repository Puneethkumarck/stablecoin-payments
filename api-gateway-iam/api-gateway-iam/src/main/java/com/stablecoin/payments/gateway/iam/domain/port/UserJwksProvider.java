package com.stablecoin.payments.gateway.iam.domain.port;

import com.stablecoin.payments.gateway.iam.domain.exception.UserJwksUnavailableException;

/**
 * Outbound port for fetching the JWKS (JSON Web Key Set) from the Merchant IAM service (S13).
 * Infrastructure decides caching strategy and failover behaviour.
 */
public interface UserJwksProvider {

    /**
     * Returns the JWKS JSON from S13.
     *
     * @throws UserJwksUnavailableException if S13 is unreachable and no cached value is available
     */
    String fetchJwks();
}
