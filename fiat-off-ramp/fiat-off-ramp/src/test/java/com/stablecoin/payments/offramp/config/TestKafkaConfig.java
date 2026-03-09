package com.stablecoin.payments.offramp.config;

import org.springframework.boot.test.context.TestConfiguration;

/**
 * Test configuration for Kafka. Disables real Kafka bindings during unit tests.
 * Integration tests use TestContainers Kafka via {@code AbstractIntegrationTest}.
 */
@TestConfiguration
public class TestKafkaConfig {
    // Kafka-specific test beans will be added as consumers/producers are implemented.
}
