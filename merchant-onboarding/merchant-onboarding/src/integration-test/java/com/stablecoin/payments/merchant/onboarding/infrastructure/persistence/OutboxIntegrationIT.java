package com.stablecoin.payments.merchant.onboarding.infrastructure.persistence;

import com.stablecoin.payments.merchant.onboarding.AbstractIntegrationTest;
import com.stablecoin.payments.merchant.onboarding.infrastructure.messaging.OutboxEventRepository;
import com.stablecoin.payments.merchant.onboarding.infrastructure.persistence.entity.MerchantJpaRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Outbox Integration IT")
class OutboxIntegrationIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private MerchantJpaRepository merchantJpa;

    @BeforeEach
    void cleanUp() {
        outboxRepository.deleteAll();
        merchantJpa.deleteAll();
    }

    @Test
    @DisplayName("should create outbox event when merchant is applied")
    @WithMockUser(authorities = "merchant:write")
    void shouldCreateOutboxEventOnApply() throws Exception {
        // when
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "legalName": "Outbox Test Corp",
                                    "tradingName": "OutboxCo",
                                    "registrationNumber": "REG-OUTBOX-001",
                                    "registrationCountry": "GB",
                                    "entityType": "PRIVATE_LIMITED",
                                    "websiteUrl": "https://outbox.com",
                                    "primaryCurrency": "USD",
                                    "registeredAddress": {
                                        "streetLine1": "1 Outbox Lane",
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
                                """))
                .andExpect(status().isCreated());

        // then
        var events = outboxRepository.findAll();
        assertThat(events).hasSize(1);

        var event = events.getFirst();
        assertThat(event.getTopic()).isEqualTo("merchant.applied");
        assertThat(event.getEventType()).isEqualTo("merchant.applied");
        assertThat(event.isProcessed()).isFalse();
        assertThat(event.getPayload()).contains("Outbox Test Corp");
    }

    @Test
    @DisplayName("should persist merchant and outbox event in same transaction")
    @WithMockUser(authorities = "merchant:write")
    void shouldPersistMerchantAndOutboxAtomically() throws Exception {
        // when
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "legalName": "Atomic Test Corp",
                                    "tradingName": "AtomicCo",
                                    "registrationNumber": "REG-ATOMIC-001",
                                    "registrationCountry": "GB",
                                    "entityType": "PRIVATE_LIMITED",
                                    "websiteUrl": "https://atomic.com",
                                    "primaryCurrency": "USD",
                                    "registeredAddress": {
                                        "streetLine1": "1 Atomic Lane",
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
                                """))
                .andExpect(status().isCreated());

        // then — both merchant and outbox event persisted
        assertThat(merchantJpa.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("should relay outbox event to Kafka topic")
    @WithMockUser(authorities = "merchant:write")
    void shouldRelayOutboxEventToKafka() throws Exception {
        // given — create a merchant (produces outbox event)
        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "legalName": "Kafka E2E Corp",
                                    "tradingName": "KafkaCo",
                                    "registrationNumber": "REG-KAFKA-001",
                                    "registrationCountry": "GB",
                                    "entityType": "PRIVATE_LIMITED",
                                    "websiteUrl": "https://kafka-e2e.com",
                                    "primaryCurrency": "USD",
                                    "registeredAddress": {
                                        "streetLine1": "1 Kafka Lane",
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
                                """))
                .andExpect(status().isCreated());

        // then — wait for OutboxRelayJob to mark event as processed
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var events = outboxRepository.findAll();
                    assertThat(events).isNotEmpty();
                    assertThat(events.stream().anyMatch(e ->
                            e.isProcessed() && e.getPayload().contains("Kafka E2E Corp"))).isTrue();
                });

        // then — consume from Kafka and verify the message arrived
        try (var consumer = createConsumer("merchant.applied")) {
            var records = consumer.poll(Duration.ofSeconds(10));
            assertThat(records.count()).isGreaterThanOrEqualTo(1);

            var found = false;
            for (var record : records) {
                if (record.value().contains("Kafka E2E Corp")) {
                    assertThat(record.topic()).isEqualTo("merchant.applied");
                    found = true;
                    break;
                }
            }
            assertThat(found).as("Expected a Kafka record containing 'Kafka E2E Corp'").isTrue();
        }
    }

    private KafkaConsumer<String, String> createConsumer(String topic) {
        var props = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        );
        var consumer = new KafkaConsumer<String, String>(props);
        consumer.subscribe(List.of(topic));
        return consumer;
    }
}
