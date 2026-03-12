package com.stablecoin.payments.ledger.application.scheduler;

import com.stablecoin.payments.ledger.domain.port.AuditEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class AuditArchiveJobTest {

    private static final Instant NOW = Instant.parse("2026-03-11T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneId.of("UTC"));

    @Test
    @DisplayName("should delete audit events older than retention period")
    void deletesOldAuditEvents() {
        var auditEventRepository = mock(AuditEventRepository.class);
        var job = new AuditArchiveJob(auditEventRepository, FIXED_CLOCK);
        job.setRetentionDays(90);

        var expectedCutoff = NOW.minus(Duration.ofDays(90));
        given(auditEventRepository.deleteByOccurredAtBefore(expectedCutoff)).willReturn(5);

        job.archiveOldAuditEvents();

        then(auditEventRepository).should().deleteByOccurredAtBefore(expectedCutoff);
    }

    @Test
    @DisplayName("should handle zero records to archive")
    void handlesZeroRecords() {
        var auditEventRepository = mock(AuditEventRepository.class);
        var job = new AuditArchiveJob(auditEventRepository, FIXED_CLOCK);
        job.setRetentionDays(90);

        var expectedCutoff = NOW.minus(Duration.ofDays(90));
        given(auditEventRepository.deleteByOccurredAtBefore(expectedCutoff)).willReturn(0);

        job.archiveOldAuditEvents();

        then(auditEventRepository).should().deleteByOccurredAtBefore(expectedCutoff);
    }
}
