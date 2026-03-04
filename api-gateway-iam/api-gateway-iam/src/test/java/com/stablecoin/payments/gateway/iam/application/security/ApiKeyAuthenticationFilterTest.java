package com.stablecoin.payments.gateway.iam.application.security;

import com.stablecoin.payments.gateway.iam.domain.exception.ApiKeyExpiredException;
import com.stablecoin.payments.gateway.iam.domain.exception.ApiKeyNotFoundException;
import com.stablecoin.payments.gateway.iam.domain.exception.ApiKeyRevokedException;
import com.stablecoin.payments.gateway.iam.domain.exception.IpNotAllowedException;
import com.stablecoin.payments.gateway.iam.domain.model.ApiKey;
import com.stablecoin.payments.gateway.iam.domain.model.ApiKeyEnvironment;
import com.stablecoin.payments.gateway.iam.domain.service.ApiKeyService;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private FilterChain filterChain;

    private ApiKeyAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthenticationFilter(apiKeyService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class WhenNoApiKeyHeader {

        @Test
        void shouldPassThroughWithoutHeader() throws ServletException, IOException {
            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void shouldPassThroughWithBlankHeader() throws ServletException, IOException {
            request.addHeader("X-API-Key", "  ");

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(apiKeyService, never()).validate(anyString(), anyString());
        }
    }

    @Nested
    class WhenValidApiKey {

        private final UUID merchantId = UUID.randomUUID();
        private final UUID keyId = UUID.randomUUID();

        @Test
        void shouldAuthenticateWithValidKey() throws ServletException, IOException {
            var rawKey = "test-api-key-value";
            request.addHeader("X-API-Key", rawKey);
            request.setRemoteAddr("10.0.0.1");

            var apiKey = ApiKey.builder()
                    .keyId(keyId)
                    .merchantId(merchantId)
                    .keyHash("hash")
                    .keyPrefix("test-api")
                    .name("test")
                    .environment(ApiKeyEnvironment.LIVE)
                    .scopes(List.of("payments:read", "payments:write"))
                    .allowedIps(List.of())
                    .active(true)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .version(0)
                    .build();

            when(apiKeyService.validate(rawKey, "10.0.0.1")).thenReturn(apiKey);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isInstanceOf(MerchantAuthentication.class);
            var merchantAuth = (MerchantAuthentication) auth;
            assertThat(merchantAuth.merchantId()).isEqualTo(merchantId);
            assertThat(merchantAuth.clientId()).isEqualTo(keyId);
            assertThat(merchantAuth.scopes()).containsExactly("payments:read", "payments:write");
            assertThat(merchantAuth.authMethod()).isEqualTo(MerchantAuthentication.AuthMethod.API_KEY);
        }
    }

    @Nested
    class WhenInvalidApiKey {

        @Test
        void shouldRejectNotFoundKey() throws ServletException, IOException {
            request.addHeader("X-API-Key", "invalid_key");
            when(apiKeyService.validate("invalid_key", "127.0.0.1"))
                    .thenThrow(ApiKeyNotFoundException.byHash());

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        void shouldRejectRevokedKey() throws ServletException, IOException {
            var keyId = UUID.randomUUID();
            request.addHeader("X-API-Key", "revoked_key");
            when(apiKeyService.validate("revoked_key", "127.0.0.1"))
                    .thenThrow(ApiKeyRevokedException.of(keyId));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        void shouldRejectExpiredKey() throws ServletException, IOException {
            var keyId = UUID.randomUUID();
            request.addHeader("X-API-Key", "expired_key");
            when(apiKeyService.validate("expired_key", "127.0.0.1"))
                    .thenThrow(ApiKeyExpiredException.of(keyId));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        void shouldRejectDisallowedIp() throws ServletException, IOException {
            request.addHeader("X-API-Key", "valid_key");
            request.setRemoteAddr("192.168.1.1");
            when(apiKeyService.validate("valid_key", "192.168.1.1"))
                    .thenThrow(IpNotAllowedException.of("192.168.1.1"));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    @Test
    void shouldSkipWhenAlreadyAuthenticated() throws ServletException, IOException {
        request.addHeader("X-API-Key", "some-key");
        SecurityContextHolder.getContext().setAuthentication(
                new MerchantAuthentication(UUID.randomUUID(), UUID.randomUUID(),
                        List.of(), MerchantAuthentication.AuthMethod.JWT));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(apiKeyService, never()).validate(anyString(), anyString());
    }
}
