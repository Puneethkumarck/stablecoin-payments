package com.stablecoin.payments.gateway.iam.application.security;

import com.stablecoin.payments.gateway.iam.domain.port.TokenIssuer;
import com.stablecoin.payments.gateway.iam.domain.port.TokenRevocationCache;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private TokenRevocationCache tokenRevocationCache;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(tokenIssuer, tokenRevocationCache);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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
        void shouldPassThroughWithNonBearerAuthHeader() throws ServletException, IOException {
            request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(tokenIssuer, never()).parseAndVerify(anyString());
        }
    }

    @Nested
    class WhenValidBearerToken {

        private final UUID merchantId = UUID.randomUUID();
        private final UUID clientId = UUID.randomUUID();
        private final UUID jti = UUID.randomUUID();

        @Test
        void shouldAuthenticateWithValidJwt() throws ServletException, IOException {
            var token = "valid.jwt.token";
            request.addHeader("Authorization", "Bearer " + token);

            when(tokenIssuer.parseAndVerify(token)).thenReturn(
                    new TokenIssuer.ParsedToken(jti, merchantId, clientId, List.of("payments:read"), 9999999999L));
            when(tokenRevocationCache.isRevoked(jti)).thenReturn(false);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isInstanceOf(MerchantAuthentication.class);
            var merchantAuth = (MerchantAuthentication) auth;
            assertThat(merchantAuth.merchantId()).isEqualTo(merchantId);
            assertThat(merchantAuth.clientId()).isEqualTo(clientId);
            assertThat(merchantAuth.scopes()).containsExactly("payments:read");
            assertThat(merchantAuth.authMethod()).isEqualTo(MerchantAuthentication.AuthMethod.JWT);
        }

        @Test
        void shouldRejectRevokedToken() throws ServletException, IOException {
            var token = "revoked.jwt.token";
            request.addHeader("Authorization", "Bearer " + token);

            when(tokenIssuer.parseAndVerify(token)).thenReturn(
                    new TokenIssuer.ParsedToken(jti, merchantId, clientId, List.of(), 9999999999L));
            when(tokenRevocationCache.isRevoked(jti)).thenReturn(true);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("revoked");
        }
    }

    @Nested
    class WhenInvalidToken {

        @Test
        void shouldRejectExpiredToken() throws ServletException, IOException {
            request.addHeader("Authorization", "Bearer expired.jwt.token");
            when(tokenIssuer.parseAndVerify("expired.jwt.token"))
                    .thenThrow(new IllegalArgumentException("JWT has expired"));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        void shouldRejectMalformedToken() throws ServletException, IOException {
            request.addHeader("Authorization", "Bearer not-a-jwt");
            when(tokenIssuer.parseAndVerify("not-a-jwt"))
                    .thenThrow(new IllegalArgumentException("Invalid JWT"));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    @Test
    void shouldSkipWhenAlreadyAuthenticated() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer some.token");
        SecurityContextHolder.getContext().setAuthentication(
                new MerchantAuthentication(UUID.randomUUID(), UUID.randomUUID(),
                        List.of(), MerchantAuthentication.AuthMethod.API_KEY));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(tokenIssuer, never()).parseAndVerify(anyString());
    }
}
