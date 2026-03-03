package com.stablecoin.payments.gateway.iam.application.job;

import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.AccessTokenJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredTokenCleanupJob {

    private final AccessTokenJpaRepository tokenRepository;

    @Scheduled(fixedDelayString = "${api-gateway-iam.jobs.expired-token-cleanup.fixed-delay-ms:900000}")
    @Transactional
    public void cleanupExpiredTokens() {
        var cutoff = Instant.now();
        var deleted = tokenRepository.deleteExpiredBefore(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} expired revoked tokens", deleted);
        }
    }
}
