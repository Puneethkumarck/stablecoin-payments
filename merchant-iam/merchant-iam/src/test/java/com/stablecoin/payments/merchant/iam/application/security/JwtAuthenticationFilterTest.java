package com.stablecoin.payments.merchant.iam.application.security;

import com.stablecoin.payments.merchant.iam.domain.team.JwtTokenIssuer;
import com.stablecoin.payments.merchant.iam.domain.team.JwtTokenIssuer.ParsedAccessToken;
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
    private JwtTokenIssuer jwtTokenIssuer;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenIssuer);
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
            verify(jwtTokenIssuer, never()).parseAndVerify(anyString());
        }
    }

    @Nested
    class WhenValidBearerToken {

        private final UUID userId = UUID.randomUUID();
        private final UUID merchantId = UUID.randomUUID();
        private final UUID roleId = UUID.randomUUID();
        private final UUID jti = UUID.randomUUID();

        @Test
        void shouldAuthenticateWithValidJwt() throws ServletException, IOException {
            var token = "valid.jwt.token";
            request.addHeader("Authorization", "Bearer " + token);

            when(jwtTokenIssuer.parseAndVerify(token)).thenReturn(
                    new ParsedAccessToken(jti, userId, merchantId, roleId,
                            "ADMIN", List.of("payments:read", "team:manage"), true, 9999999999L));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isInstanceOf(UserAuthentication.class);
            var userAuth = (UserAuthentication) auth;
            assertThat(userAuth.userId()).isEqualTo(userId);
            assertThat(userAuth.merchantId()).isEqualTo(merchantId);
            assertThat(userAuth.roleId()).isEqualTo(roleId);
            assertThat(userAuth.role()).isEqualTo("ADMIN");
            assertThat(userAuth.permissions()).containsExactly("payments:read", "team:manage");
            assertThat(userAuth.mfaVerified()).isTrue();
        }
    }

    @Nested
    class WhenInvalidToken {

        @Test
        void shouldRejectExpiredToken() throws ServletException, IOException {
            request.addHeader("Authorization", "Bearer expired.jwt.token");
            when(jwtTokenIssuer.parseAndVerify("expired.jwt.token"))
                    .thenThrow(new IllegalArgumentException("JWT has expired"));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("IAM-4010");
        }

        @Test
        void shouldRejectMalformedToken() throws ServletException, IOException {
            request.addHeader("Authorization", "Bearer not-a-jwt");
            when(jwtTokenIssuer.parseAndVerify("not-a-jwt"))
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
                new UserAuthentication(UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), "ADMIN", List.of(), false));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(jwtTokenIssuer, never()).parseAndVerify(anyString());
    }
}
