package com.stablecoin.payments.gateway.iam.application.security;

import com.stablecoin.payments.gateway.iam.domain.model.AuditLogEntry;
import com.stablecoin.payments.gateway.iam.domain.port.AuditLogRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogFilterTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private FilterChain filterChain;

    private AuditLogFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new AuditLogFilter(auditLogRepository);
        request = new MockHttpServletRequest("POST", "/v1/payments");
        request.setRemoteAddr("10.0.0.1");
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAlwaysCallFilterChain() throws ServletException, IOException {
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldSkipAuditWhenNotAuthenticated() throws ServletException, IOException {
        filter.doFilterInternal(request, response, filterChain);

        verify(auditLogRepository, never()).save(any());
    }

    @Nested
    class WhenAuthenticated {

        private final UUID merchantId = UUID.randomUUID();
        private final UUID clientId = UUID.randomUUID();

        @BeforeEach
        void setAuthentication() {
            SecurityContextHolder.getContext().setAuthentication(
                    new MerchantAuthentication(merchantId, clientId,
                            List.of("payments:read"), MerchantAuthentication.AuthMethod.API_KEY));
        }

        @Test
        void shouldPersistAuditLogEntry() throws ServletException, IOException {
            response.setStatus(201);

            filter.doFilterInternal(request, response, filterChain);

            var captor = ArgumentCaptor.forClass(AuditLogEntry.class);
            verify(auditLogRepository).save(captor.capture());

            var entry = captor.getValue();
            assertThat(entry.getMerchantId()).isEqualTo(merchantId);
            assertThat(entry.getAction()).isEqualTo("POST");
            assertThat(entry.getResource()).isEqualTo("/v1/payments");
            assertThat(entry.getSourceIp()).isEqualTo("10.0.0.1");
            assertThat(entry.getDetail()).containsEntry("status_code", 201);
            assertThat(entry.getDetail()).containsEntry("auth_method", "API_KEY");
            assertThat(entry.getDetail()).containsEntry("client_id", clientId.toString());
            assertThat(entry.getOccurredAt()).isNotNull();
        }

        @Test
        void shouldRecordJwtAuthMethod() throws ServletException, IOException {
            SecurityContextHolder.getContext().setAuthentication(
                    new MerchantAuthentication(merchantId, clientId,
                            List.of(), MerchantAuthentication.AuthMethod.JWT));

            filter.doFilterInternal(request, response, filterChain);

            var captor = ArgumentCaptor.forClass(AuditLogEntry.class);
            verify(auditLogRepository).save(captor.capture());
            assertThat(captor.getValue().getDetail()).containsEntry("auth_method", "JWT");
        }

        @Test
        void shouldNotFailWhenRepositoryThrows() throws ServletException, IOException {
            doThrow(new RuntimeException("DB down"))
                    .when(auditLogRepository).save(any());

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            // No exception propagated — filter swallows it
        }

        @Test
        void shouldAuditEvenWhenFilterChainThrows() throws ServletException, IOException {
            doThrow(new ServletException("downstream error"))
                    .when(filterChain).doFilter(request, response);

            assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                    .isInstanceOf(ServletException.class);

            verify(auditLogRepository).save(any());
        }
    }
}
