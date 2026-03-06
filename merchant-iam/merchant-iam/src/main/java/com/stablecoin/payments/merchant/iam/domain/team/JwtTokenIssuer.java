package com.stablecoin.payments.merchant.iam.domain.team;

import com.stablecoin.payments.merchant.iam.domain.team.model.MerchantUser;
import com.stablecoin.payments.merchant.iam.domain.team.model.Role;

import java.util.List;
import java.util.UUID;

/**
 * Domain port for issuing ES256 JWTs and publishing the JWKS endpoint key set.
 * Infrastructure provides a Nimbus JOSE+JWT implementation.
 */
public interface JwtTokenIssuer {

    /**
     * Issues an access token for the given user and role.
     * Claims include: sub, merchant_id, user_id, role, role_id, permissions, mfa_verified, jti.
     */
    String issueAccessToken(MerchantUser user, Role role, boolean mfaVerified);

    /**
     * Issues an opaque refresh token bound to the user session.
     */
    String issueRefreshToken(UUID userId, UUID sessionId);

    /**
     * Parses and verifies a JWT access token. Returns the extracted claims.
     *
     * @throws IllegalArgumentException if the token is invalid, expired, or tampered
     */
    ParsedAccessToken parseAndVerify(String token);

    /**
     * Parses and verifies a JWT refresh token. Returns the extracted claims.
     *
     * @throws IllegalArgumentException if the token is invalid, expired, or not a refresh token
     */
    ParsedRefreshToken parseRefreshToken(String token);

    /**
     * Returns the refresh token TTL in seconds (used to set session expiry).
     */
    int refreshTokenTtlSeconds();

    /**
     * Returns the public key set in JWK Set JSON format for the JWKS endpoint.
     */
    String jwksJson();

    record ParsedRefreshToken(
            UUID jti,
            UUID userId,
            UUID sessionId,
            long expiresAtEpochSecond
    ) {}

    record ParsedAccessToken(
            UUID jti,
            UUID userId,
            UUID merchantId,
            UUID roleId,
            String role,
            List<String> permissions,
            boolean mfaVerified,
            long expiresAtEpochSecond
    ) {}
}
