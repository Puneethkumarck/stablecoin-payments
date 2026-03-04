package com.stablecoin.payments.gateway.iam.application.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.stablecoin.payments.gateway.iam.domain.exception.UserJwksUnavailableException;
import com.stablecoin.payments.gateway.iam.domain.port.UserJwksProvider;
import com.stablecoin.payments.gateway.iam.infrastructure.config.MerchantIamProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserJwtAuthenticationFilterTest {

    private static final String S13_ISSUER = "https://api.stablecoin-payments.dev";
    private static final String AUDIENCE = "payment-platform";

    @Mock
    private UserJwksProvider userJwksProvider;

    @Mock
    private FilterChain filterChain;

    private UserJwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private ECKey signingKey;
    private ECDSASigner signer;
    private String jwksJson;

    @BeforeEach
    void setUp() throws Exception {
        var properties = new MerchantIamProperties(
                "http://localhost:8083", S13_ISSUER, AUDIENCE, 24);
        filter = new UserJwtAuthenticationFilter(userJwksProvider, properties);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();

        signingKey = new ECKeyGenerator(Curve.P_256)
                .keyUse(KeyUse.SIGNATURE)
                .keyID("test-kid")
                .algorithm(JWSAlgorithm.ES256)
                .generate();
        signer = new ECDSASigner(signingKey);
        jwksJson = new JWKSet(signingKey.toPublicJWK()).toString();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String buildToken(String issuer, String audience, Instant expiry,
                              UUID userId, UUID merchantId) throws Exception {
        var claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(userId.toString())
                .audience(audience)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(new Date())
                .expirationTime(Date.from(expiry))
                .claim("user_id", userId.toString())
                .claim("merchant_id", merchantId.toString())
                .claim("role_id", UUID.randomUUID().toString())
                .claim("role", "ADMIN")
                .claim("permissions", List.of("payments:read", "team:manage"))
                .claim("mfa_verified", true)
                .build();

        var header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(signingKey.getKeyID())
                .build();

        var jwt = new SignedJWT(header, claims);
        jwt.sign(signer);
        return jwt.serialize();
    }

    @Nested
    class WhenNoBearerToken {

        @Test
        void shouldPassThroughWithoutAuthHeader() throws ServletException, IOException {
            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void shouldPassThroughWithNonBearerHeader() throws ServletException, IOException {
            request.addHeader("Authorization", "Basic abc123");

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    class WhenAlreadyAuthenticated {

        @Test
        void shouldSkipWhenAlreadyAuthenticated() throws ServletException, IOException {
            request.addHeader("Authorization", "Bearer some-token");
            SecurityContextHolder.getContext().setAuthentication(
                    new MerchantAuthentication(UUID.randomUUID(), UUID.randomUUID(),
                            List.of(), MerchantAuthentication.AuthMethod.API_KEY));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication())
                    .isInstanceOf(MerchantAuthentication.class);
        }
    }

    @Nested
    class WhenValidS13Token {

        @Test
        void shouldAuthenticateWithValidUserJwt() throws Exception {
            var userId = UUID.randomUUID();
            var merchantId = UUID.randomUUID();
            var token = buildToken(S13_ISSUER, AUDIENCE,
                    Instant.now().plusSeconds(3600), userId, merchantId);
            request.addHeader("Authorization", "Bearer " + token);
            when(userJwksProvider.fetchJwks()).thenReturn(jwksJson);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isInstanceOf(UserAuthentication.class);
            var userAuth = (UserAuthentication) auth;
            assertThat(userAuth.userId()).isEqualTo(userId);
            assertThat(userAuth.merchantId()).isEqualTo(merchantId);
            assertThat(userAuth.role()).isEqualTo("ADMIN");
            assertThat(userAuth.permissions()).containsExactly("payments:read", "team:manage");
            assertThat(userAuth.mfaVerified()).isTrue();
        }
    }

    @Nested
    class WhenDifferentIssuer {

        @Test
        void shouldPassThroughForNonS13Issuer() throws Exception {
            var token = buildToken("https://gateway.stablecoin-payments.dev", AUDIENCE,
                    Instant.now().plusSeconds(3600), UUID.randomUUID(), UUID.randomUUID());
            request.addHeader("Authorization", "Bearer " + token);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    @Nested
    class WhenInvalidToken {

        @Test
        void shouldRejectExpiredToken() throws Exception {
            var token = buildToken(S13_ISSUER, AUDIENCE,
                    Instant.now().minusSeconds(60), UUID.randomUUID(), UUID.randomUUID());
            request.addHeader("Authorization", "Bearer " + token);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        void shouldRejectWrongAudience() throws Exception {
            var token = buildToken(S13_ISSUER, "wrong-audience",
                    Instant.now().plusSeconds(3600), UUID.randomUUID(), UUID.randomUUID());
            request.addHeader("Authorization", "Bearer " + token);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        void shouldRejectTokenSignedByDifferentKey() throws Exception {
            var otherKey = new ECKeyGenerator(Curve.P_256)
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID("other-kid")
                    .algorithm(JWSAlgorithm.ES256)
                    .generate();

            var claims = new JWTClaimsSet.Builder()
                    .issuer(S13_ISSUER)
                    .audience(AUDIENCE)
                    .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                    .claim("user_id", UUID.randomUUID().toString())
                    .claim("merchant_id", UUID.randomUUID().toString())
                    .claim("role_id", UUID.randomUUID().toString())
                    .claim("role", "ADMIN")
                    .claim("permissions", List.of())
                    .claim("mfa_verified", false)
                    .build();

            var jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(signingKey.getKeyID()).build(),
                    claims);
            jwt.sign(new ECDSASigner(otherKey));

            request.addHeader("Authorization", "Bearer " + jwt.serialize());
            when(userJwksProvider.fetchJwks()).thenReturn(jwksJson);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        void shouldRejectGarbageToken() throws ServletException, IOException {
            request.addHeader("Authorization", "Bearer not-a-jwt");

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    @Nested
    class WhenJwksUnavailable {

        @Test
        void shouldReturn503WhenJwksUnavailable() throws Exception {
            var token = buildToken(S13_ISSUER, AUDIENCE,
                    Instant.now().plusSeconds(3600), UUID.randomUUID(), UUID.randomUUID());
            request.addHeader("Authorization", "Bearer " + token);
            when(userJwksProvider.fetchJwks())
                    .thenThrow(new UserJwksUnavailableException("S13 down", new RuntimeException()));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(response.getContentAsString()).contains("GW-5001");
        }
    }
}
