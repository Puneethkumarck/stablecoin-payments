package com.stablecoin.payments.offramp.application.controller;

import com.stablecoin.payments.offramp.domain.exception.PayoutNotFoundException;
import com.stablecoin.payments.offramp.domain.port.WebhookSignatureValidator;
import com.stablecoin.payments.offramp.domain.service.PartnerWebhookCommand;
import com.stablecoin.payments.offramp.domain.service.WebhookCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Thin HTTP handler for off-ramp partner webhook callbacks.
 * <p>
 * Validates the HMAC signature, parses the partner event JSON,
 * and delegates to {@link WebhookCommandHandler}.
 */
@Slf4j
@RestController
@RequestMapping("/internal/webhooks/partner")
@RequiredArgsConstructor
public class PartnerWebhookController {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final WebhookSignatureValidator signatureValidator;
    private final WebhookCommandHandler webhookCommandHandler;

    @PostMapping("/{partnerName}")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @PathVariable String partnerName,
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String webhookSignature) {

        if (webhookSignature == null || webhookSignature.isBlank()) {
            log.warn("[PARTNER-WEBHOOK] Missing X-Webhook-Signature header partner={}",
                    partnerName);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing X-Webhook-Signature header"));
        }

        if (!signatureValidator.isValid(rawBody, webhookSignature)) {
            log.warn("[PARTNER-WEBHOOK] Invalid signature — rejecting request partner={}",
                    partnerName);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid webhook signature"));
        }

        try {
            var command = parsePartnerEvent(partnerName, rawBody);
            webhookCommandHandler.handleWebhook(command);
            return ResponseEntity.ok(Map.of("status", "received"));
        } catch (IllegalArgumentException e) {
            log.warn("[PARTNER-WEBHOOK] Invalid webhook payload partner={}: {}",
                    partnerName, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (PayoutNotFoundException e) {
            log.warn("[PARTNER-WEBHOOK] Payout order not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("[PARTNER-WEBHOOK] Invalid state transition: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[PARTNER-WEBHOOK] Unexpected error processing webhook partner={}",
                    partnerName, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Internal error processing webhook"));
        }
    }

    PartnerWebhookCommand parsePartnerEvent(String partnerName, String rawBody) {
        try {
            var root = JSON_MAPPER.readTree(rawBody);

            var eventId = root.path("event_id").asText();
            var eventType = root.path("event_type").asText();
            var partnerReference = root.path("payment_reference").asText();
            var status = root.path("status").asText(null);
            var amount = extractAmount(root);
            var currency = root.path("currency").asText(null);
            var settledAt = extractTimestamp(root, "settled_at");
            var failureReason = root.path("failure_reason").asText(null);

            return new PartnerWebhookCommand(
                    eventId,
                    eventType,
                    partnerName,
                    partnerReference,
                    amount,
                    currency,
                    status,
                    settledAt,
                    failureReason,
                    rawBody);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse partner webhook JSON", e);
        }
    }

    private BigDecimal extractAmount(JsonNode root) {
        var amountNode = root.path("amount");
        if (amountNode.isMissingNode() || amountNode.isNull()) {
            return null;
        }
        return new BigDecimal(amountNode.asText());
    }

    private Instant extractTimestamp(JsonNode root, String fieldName) {
        var node = root.path(fieldName);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            return Instant.parse(node.asText());
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid timestamp format for field '%s': %s".formatted(fieldName, node.asText()), e);
        }
    }
}
