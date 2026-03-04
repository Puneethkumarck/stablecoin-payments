package com.stablecoin.payments.gateway.iam.application.security;

import com.stablecoin.payments.gateway.iam.domain.model.AuditLogEntry;
import com.stablecoin.payments.gateway.iam.domain.port.AuditLogRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class AuditLogFilter extends OncePerRequestFilter {

    private final AuditLogRepository auditLogRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            writeAuditLog(request, response);
        }
    }

    private void writeAuditLog(HttpServletRequest request, HttpServletResponse response) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof MerchantAuthentication merchantAuth)) {
            return;
        }

        try {
            var entry = AuditLogEntry.builder()
                    .logId(UUID.randomUUID())
                    .merchantId(merchantAuth.merchantId())
                    .action(request.getMethod())
                    .resource(request.getRequestURI())
                    .sourceIp(request.getRemoteAddr())
                    .detail(Map.of(
                            "status_code", response.getStatus(),
                            "auth_method", merchantAuth.authMethod().name(),
                            "client_id", merchantAuth.clientId().toString()
                    ))
                    .occurredAt(Instant.now())
                    .build();

            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to write audit log for merchantId={}: {}",
                    merchantAuth.merchantId(), e.getMessage());
        }
    }
}
