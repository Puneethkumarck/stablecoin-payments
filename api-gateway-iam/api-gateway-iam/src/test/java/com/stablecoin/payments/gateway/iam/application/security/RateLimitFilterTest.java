package com.stablecoin.payments.gateway.iam.application.security;

import com.stablecoin.payments.gateway.iam.domain.model.Merchant;
import com.stablecoin.payments.gateway.iam.domain.model.MerchantStatus;
import com.stablecoin.payments.gateway.iam.domain.model.RateLimitTier;
import com.stablecoin.payments.gateway.iam.domain.port.MerchantRepository;
import com.stablecoin.payments.gateway.iam.domain.port.RateLimitEventRepository;
import com.stablecoin.payments.gateway.iam.domain.port.RateLimiter;
import com.stablecoin.payments.gateway.iam.domain.port.RateLimiter.RateLimitResult;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private RateLimitEventRepository rateLimitEventRepository;

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private final UUID merchantId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(rateLimiter, merchantRepository, rateLimitEventRepository);
        request = new MockHttpServletRequest("GET", "/v1/payments");
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Merchant buildMerchant(RateLimitTier tier) {
        return Merchant.builder()
                .merchantId(merchantId)
                .externalId(UUID.randomUUID())
                .name("Test")
                .country("US")
                .scopes(List.of())
                .corridors(List.of())
                .status(MerchantStatus.ACTIVE)
                .rateLimitTier(tier)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0)
                .build();
    }

    private void setAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new MerchantAuthentication(merchantId, clientId, List.of(), MerchantAuthentication.AuthMethod.JWT));
    }

    @Test
    void shouldPassThroughWhenNotAuthenticated() throws ServletException, IOException {
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(rateLimiter, never()).check(any(), any(), any());
    }

    @Test
    void shouldSkipRateLimitingForUnlimitedTier() throws ServletException, IOException {
        setAuthentication();
        when(merchantRepository.findById(merchantId))
                .thenReturn(Optional.of(buildMerchant(RateLimitTier.UNLIMITED)));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(rateLimiter, never()).check(any(), any(), any());
    }

    @Nested
    class WhenWithinLimits {

        @Test
        void shouldAllowRequestAndSetHeaders() throws ServletException, IOException {
            setAuthentication();
            var merchant = buildMerchant(RateLimitTier.STARTER);
            when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
            when(rateLimiter.check(eq(merchantId), any(), any()))
                    .thenReturn(new RateLimitResult(true, 5, 60, "1m"));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("60");
            assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("55");
        }
    }

    @Nested
    class WhenExceedingLimits {

        @Test
        void shouldRejectWithMinuteBreachAndRetryAfter() throws ServletException, IOException {
            setAuthentication();
            var merchant = buildMerchant(RateLimitTier.STARTER);
            when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
            when(rateLimiter.check(eq(merchantId), any(), any()))
                    .thenReturn(new RateLimitResult(false, 61, 60, "1m"));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(429);
            assertThat(response.getHeader("Retry-After")).isEqualTo("60");
            assertThat(response.getContentAsString()).contains("GW-6001");
        }

        @Test
        void shouldRejectWithDayBreachAndRetryAfter() throws ServletException, IOException {
            setAuthentication();
            var merchant = buildMerchant(RateLimitTier.STARTER);
            when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
            when(rateLimiter.check(eq(merchantId), any(), any()))
                    .thenReturn(new RateLimitResult(false, 10001, 10000, "1d"));

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain, never()).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(429);
            assertThat(response.getHeader("Retry-After")).isEqualTo("3600");
        }

        @Test
        void shouldPersistRateLimitEvent() throws ServletException, IOException {
            setAuthentication();
            var merchant = buildMerchant(RateLimitTier.STARTER);
            when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
            when(rateLimiter.check(eq(merchantId), any(), any()))
                    .thenReturn(new RateLimitResult(false, 61, 60, "1m"));

            filter.doFilterInternal(request, response, filterChain);

            verify(rateLimitEventRepository).save(any());
        }
    }
}
