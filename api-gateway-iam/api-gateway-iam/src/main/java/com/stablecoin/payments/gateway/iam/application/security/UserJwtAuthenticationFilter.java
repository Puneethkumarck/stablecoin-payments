package com.stablecoin.payments.gateway.iam.application.security;

import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.SignedJWT;
import com.stablecoin.payments.gateway.iam.domain.exception.UserJwksUnavailableException;
import com.stablecoin.payments.gateway.iam.domain.port.UserJwksProvider;
import com.stablecoin.payments.gateway.iam.infrastructure.config.MerchantIamProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Validates S13-issued user JWTs at the S10 gateway.
 * Fetches S13's JWKS to verify ES256 signatures, then extracts user claims
 * into a {@link UserAuthentication} principal.
 *
 * <p>Runs after the S10 merchant JWT filter. If the token's issuer doesn't match
 * the S13 issuer, this filter passes through (the token may be an S10 merchant JWT
 * that was already handled or will be rejected upstream).</p>
 */
@Slf4j
@RequiredArgsConstructor
public class UserJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final UserJwksProvider userJwksProvider;
    private final MerchantIamProperties merchantIamProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        var token = authHeader.substring(BEARER_PREFIX.length());

        try {
            var jwt = SignedJWT.parse(token);
            var claims = jwt.getJWTClaimsSet();

            // Only handle tokens issued by S13
            if (!merchantIamProperties.issuer().equals(claims.getIssuer())) {
                chain.doFilter(request, response);
                return;
            }

            // Validate audience
            if (claims.getAudience() == null
                    || !claims.getAudience().contains(merchantIamProperties.audience())) {
                sendUnauthorized(response, "JWT audience mismatch");
                return;
            }

            // Validate expiration
            if (claims.getExpirationTime() == null
                    || claims.getExpirationTime().before(new Date())) {
                sendUnauthorized(response, "JWT has expired");
                return;
            }

            // Verify signature against S13 JWKS
            var jwksJson = userJwksProvider.fetchJwks();
            var jwkSet = JWKSet.parse(jwksJson);
            var kid = jwt.getHeader().getKeyID();
            var jwk = jwkSet.getKeyByKeyId(kid);

            if (jwk == null) {
                sendUnauthorized(response, "Unknown signing key");
                return;
            }

            var verifier = new ECDSAVerifier((ECKey) jwk);
            if (!jwt.verify(verifier)) {
                sendUnauthorized(response, "JWT signature verification failed");
                return;
            }

            // Extract claims
            var userId = UUID.fromString(claims.getStringClaim("user_id"));
            var merchantId = UUID.fromString(claims.getStringClaim("merchant_id"));
            var roleId = UUID.fromString(claims.getStringClaim("role_id"));
            var role = claims.getStringClaim("role");
            var mfaVerified = claims.getBooleanClaim("mfa_verified");

            var permissionsClaim = claims.getStringListClaim("permissions");
            var permissions = permissionsClaim != null ? permissionsClaim : List.<String>of();

            var auth = new UserAuthentication(userId, merchantId, roleId,
                    role, permissions, Boolean.TRUE.equals(mfaVerified));

            SecurityContextHolder.getContext().setAuthentication(auth);
            log.debug("S13 user JWT authenticated userId={} merchantId={} role={}",
                    userId, merchantId, role);

        } catch (UserJwksUnavailableException e) {
            log.error("S13 JWKS unavailable: {}", e.getMessage());
            sendServiceUnavailable(response);
            return;
        } catch (IllegalArgumentException e) {
            log.info("S13 user JWT authentication failed: {}", e.getMessage());
            sendUnauthorized(response, "Invalid or expired token");
            return;
        } catch (Exception e) {
            log.info("S13 user JWT authentication failed: {}", e.getMessage());
            sendUnauthorized(response, "Invalid or expired token");
            return;
        }

        chain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"code\":\"GW-1001\",\"status\":\"Unauthorized\",\"message\":\"%s\"}".formatted(message)
        );
    }

    private void sendServiceUnavailable(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"code\":\"GW-5001\",\"status\":\"Service Unavailable\","
                        + "\"message\":\"Authentication service temporarily unavailable\"}");
    }
}
