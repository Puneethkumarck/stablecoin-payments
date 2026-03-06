package com.stablecoin.payments.merchant.onboarding.infrastructure.persistence;

import com.stablecoin.payments.merchant.onboarding.AbstractIntegrationTest;
import com.stablecoin.payments.merchant.onboarding.infrastructure.persistence.entity.MerchantJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Outbox Integration IT")
class OutboxIntegrationIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MerchantJpaRepository merchantJpa;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanUp() {
        jdbc.execute("DELETE FROM onboarding_outbox_record");
        merchantJpa.deleteAll();
    }

    @Test
    @DisplayName("should create outbox event when merchant is applied")
    @WithMockUser(authorities = "merchant:write")
    void shouldCreateOutboxEventOnApply() throws Exception {
        // when
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(merchantPayload("Outbox Test Corp", "REG-OUTBOX-001")))
                .andExpect(status().isCreated());

        // then — namastack outbox record created
        var count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM onboarding_outbox_record", Integer.class);
        assertThat(count).isEqualTo(1);

        var payload = jdbc.queryForObject(
                "SELECT payload FROM onboarding_outbox_record LIMIT 1", String.class);
        assertThat(payload).contains("Outbox Test Corp");
    }

    @Test
    @DisplayName("should persist merchant and outbox event in same transaction")
    @WithMockUser(authorities = "merchant:write")
    void shouldPersistMerchantAndOutboxAtomically() throws Exception {
        // when
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(merchantPayload("Atomic Test Corp", "REG-ATOMIC-001")))
                .andExpect(status().isCreated());

        // then — both merchant and outbox event persisted atomically
        assertThat(merchantJpa.count()).isEqualTo(1);
        var outboxCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM onboarding_outbox_record", Integer.class);
        assertThat(outboxCount).isEqualTo(1);
    }

    @Test
    @DisplayName("should store outbox record with correct key (merchantId)")
    @WithMockUser(authorities = "merchant:write")
    void shouldStoreOutboxRecordWithMerchantKey() throws Exception {
        // when
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(merchantPayload("Key Test Corp", "REG-KEY-001")))
                .andExpect(status().isCreated());

        // then — record key is the merchantId
        var recordKey = jdbc.queryForObject(
                "SELECT record_key FROM onboarding_outbox_record LIMIT 1", String.class);
        assertThat(recordKey).isNotBlank();

        // Verify it's a valid UUID (merchantId)
        assertThat(recordKey).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    private String merchantPayload(String legalName, String regNumber) {
        return """
                {
                    "legalName": "%s",
                    "tradingName": "TestCo",
                    "registrationNumber": "%s",
                    "registrationCountry": "GB",
                    "entityType": "PRIVATE_LIMITED",
                    "websiteUrl": "https://test.com",
                    "primaryCurrency": "USD",
                    "primaryContactEmail": "test@example.com",
                    "primaryContactName": "Test Contact",
                    "registeredAddress": {
                        "streetLine1": "1 Test Lane",
                        "city": "London",
                        "postcode": "EC1A 1BB",
                        "country": "GB"
                    },
                    "beneficialOwners": [{
                        "fullName": "Test Owner",
                        "dateOfBirth": "1985-06-15",
                        "nationality": "GB",
                        "ownershipPct": 100.00,
                        "isPoliticallyExposed": false
                    }],
                    "requestedCorridors": ["GB->US"]
                }
                """.formatted(legalName, regNumber);
    }
}
