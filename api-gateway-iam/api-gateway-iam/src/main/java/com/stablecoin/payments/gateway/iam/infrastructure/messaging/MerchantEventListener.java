package com.stablecoin.payments.gateway.iam.infrastructure.messaging;

import com.stablecoin.payments.gateway.iam.domain.service.MerchantCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantEventListener {

    private final MerchantCommandHandler merchantCommandHandler;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "merchant.activated", groupId = "api-gateway-iam-activate")
    public void onMerchantActivated(@Payload String payload) {
        try {
            var event = objectMapper.readValue(payload, MerchantActivatedEvent.class);
            log.info("Received merchant.activated merchantId={}", event.merchantId());

            merchantCommandHandler.activateAndProvisionOAuthClient(
                    event.merchantId(), event.companyName(), event.scopes());

            log.info("Activated merchant and provisioned default OAuth client merchantId={}",
                    event.merchantId());
        } catch (Exception e) {
            log.error("Failed to process merchant.activated: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process merchant.activated", e);
        }
    }

    @KafkaListener(topics = "merchant.suspended", groupId = "api-gateway-iam-suspend")
    public void onMerchantSuspended(@Payload String payload) {
        try {
            var event = objectMapper.readValue(payload, MerchantSuspendedEvent.class);
            log.info("Received merchant.suspended merchantId={}", event.merchantId());

            merchantCommandHandler.suspend(event.merchantId());

            log.info("Suspended merchant merchantId={}", event.merchantId());
        } catch (Exception e) {
            log.error("Failed to process merchant.suspended: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process merchant.suspended", e);
        }
    }

    @KafkaListener(topics = "merchant.closed", groupId = "api-gateway-iam-close")
    public void onMerchantClosed(@Payload String payload) {
        try {
            var event = objectMapper.readValue(payload, MerchantClosedEvent.class);
            log.info("Received merchant.closed merchantId={}", event.merchantId());

            merchantCommandHandler.close(event.merchantId());

            log.info("Closed merchant merchantId={}", event.merchantId());
        } catch (Exception e) {
            log.error("Failed to process merchant.closed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process merchant.closed", e);
        }
    }

    record MerchantActivatedEvent(UUID merchantId, String companyName, String country,
                                  java.util.List<String> scopes) {
        MerchantActivatedEvent {
            if (scopes == null) {
                scopes = Collections.emptyList();
            }
        }
    }

    record MerchantSuspendedEvent(UUID merchantId, String reason) {}

    record MerchantClosedEvent(UUID merchantId) {}
}
