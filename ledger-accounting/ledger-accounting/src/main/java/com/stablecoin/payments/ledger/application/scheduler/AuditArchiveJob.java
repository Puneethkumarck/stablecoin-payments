package com.stablecoin.payments.ledger.application.scheduler;

import com.stablecoin.payments.ledger.domain.port.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ledger.reconciliation.audit-archive-enabled", havingValue = "true")
public class AuditArchiveJob {

    private final AuditEventRepository auditEventRepository;
    private final Clock clock;

    @Setter
    @Value("${app.ledger.audit-archive.retention-days:90}")
    private long retentionDays;

    @Scheduled(cron = "${app.ledger.audit-archive.cron:0 0 3 * * ?}")
    @Transactional
    public void archiveOldAuditEvents() {
        log.info("[AUDIT-ARCHIVE] Starting archive job — retention={}d", retentionDays);

        var cutoff = clock.instant().minus(Duration.ofDays(retentionDays));
        var deletedCount = auditEventRepository.deleteByOccurredAtBefore(cutoff);

        log.info("[AUDIT-ARCHIVE] Completed — deleted={} cutoff={}", deletedCount, cutoff);
    }
}
