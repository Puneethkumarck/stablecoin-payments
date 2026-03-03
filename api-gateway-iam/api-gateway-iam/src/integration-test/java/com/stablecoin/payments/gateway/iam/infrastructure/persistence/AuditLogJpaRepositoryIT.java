package com.stablecoin.payments.gateway.iam.infrastructure.persistence;

import com.stablecoin.payments.gateway.iam.AbstractIntegrationTest;
import com.stablecoin.payments.gateway.iam.fixtures.GatewayEntityFixtures;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.entity.AuditLogEntity;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.AuditLogJpaRepository;
import com.stablecoin.payments.gateway.iam.infrastructure.persistence.repository.MerchantJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuditLogJpaRepository IT")
class AuditLogJpaRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private AuditLogJpaRepository auditLogRepository;

    @Autowired
    private MerchantJpaRepository merchantRepository;

    private UUID merchantId;

    @BeforeEach
    void setUpMerchant() {
        var merchant = GatewayEntityFixtures.anActiveMerchant();
        merchantRepository.save(merchant);
        merchantId = merchant.getMerchantId();
    }

    @Test
    @DisplayName("should save and find audit log entry")
    void shouldSaveAndFind() {
        var entry = GatewayEntityFixtures.anAuditLogEntry(merchantId);
        auditLogRepository.save(entry);

        var id = new AuditLogEntity.AuditLogId(entry.getLogId(), entry.getOccurredAt());
        var found = auditLogRepository.findById(id);

        assertThat(found).isPresent();
        assertThat(found.get().getAction()).isEqualTo("TOKEN_ISSUED");
        assertThat(found.get().getResource()).isEqualTo("/v1/auth/token");
        assertThat(found.get().getSourceIp()).isEqualTo("192.168.1.1");
    }

    @Test
    @DisplayName("should persist detail as jsonb")
    void shouldPersistDetailAsJsonb() {
        var entry = GatewayEntityFixtures.anAuditLogEntry(merchantId);
        auditLogRepository.save(entry);

        var id = new AuditLogEntity.AuditLogId(entry.getLogId(), entry.getOccurredAt());
        var found = auditLogRepository.findById(id).orElseThrow();

        assertThat(found.getDetail()).containsKey("client_id");
    }

    @Test
    @DisplayName("should save entry without merchant id")
    void shouldSaveWithoutMerchantId() {
        var entry = GatewayEntityFixtures.anAuditLogEntry(null);
        entry.setAction("HEALTH_CHECK");
        auditLogRepository.save(entry);

        var id = new AuditLogEntity.AuditLogId(entry.getLogId(), entry.getOccurredAt());
        var found = auditLogRepository.findById(id);

        assertThat(found).isPresent();
        assertThat(found.get().getMerchantId()).isNull();
    }
}
