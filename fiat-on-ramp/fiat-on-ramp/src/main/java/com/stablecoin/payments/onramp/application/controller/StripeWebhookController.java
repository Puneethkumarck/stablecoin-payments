package com.stablecoin.payments.onramp.application.controller;

import com.stablecoin.payments.onramp.domain.exception.CollectionOrderNotFoundException;
import com.stablecoin.payments.onramp.domain.model.Money;
import com.stablecoin.payments.onramp.domain.port.WebhookSignatureValidator;
import com.stablecoin.payments.onramp.domain.service.WebhookCommand;
import com.stablecoin.payments.onramp.domain.service.WebhookCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

/**
 * Thin HTTP handler for Stripe webhook callbacks.
 * <p>
 * Validates the HMAC signature, parses the Stripe event JSON,
 * and delegates to {@link WebhookCommandHandler}.
 */
@Slf4j
@RestController
@RequestMapping("/internal/webhooks/psp/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final WebhookSignatureValidator signatureValidator;
    private final WebhookCommandHandler webhookCommandHandler;

    @PostMapping
    public ResponseEntity<Map<String, String>> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature) {

        if (stripeSignature == null || stripeSignature.isBlank()) {
            log.warn("[STRIPE-WEBHOOK] Missing Stripe-Signature header");
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing Stripe-Signature header"));
        }

        if (!signatureValidator.isValid(rawBody, stripeSignature)) {
            log.warn("[STRIPE-WEBHOOK] Invalid signature — rejecting request");
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid webhook signature"));
        }

        try {
            var command = parseStripeEvent(rawBody);
            webhookCommandHandler.handleWebhook(command);
            return ResponseEntity.ok(Map.of("status", "received"));
        } catch (CollectionOrderNotFoundException e) {
            log.warn("[STRIPE-WEBHOOK] Collection order not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("[STRIPE-WEBHOOK] Invalid state transition: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[STRIPE-WEBHOOK] Unexpected error processing webhook", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Internal error processing webhook"));
        }
    }

    WebhookCommand parseStripeEvent(String rawBody) {
        try {
            var root = JSON_MAPPER.readTree(rawBody);
            var eventId = root.path("id").asText();
            var eventType = root.path("type").asText();
            var dataObject = root.path("data").path("object");

            var pspReference = dataObject.path("id").asText();
            var amountCents = dataObject.path("amount").asLong(0);
            var currency = dataObject.path("currency").asText("usd").toUpperCase();
            var status = dataObject.path("status").asText();

            var collectionId = extractCollectionId(dataObject);
            var amount = amountCents > 0
                    ? new Money(fromMinorUnits(amountCents), currency)
                    : null;

            return new WebhookCommand(
                    eventId,
                    eventType,
                    pspReference,
                    collectionId,
                    amount,
                    status,
                    rawBody);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse Stripe event JSON", e);
        }
    }

    private UUID extractCollectionId(JsonNode dataObject) {
        var metadata = dataObject.path("metadata");
        if (metadata.isMissingNode()) {
            return null;
        }
        var collectionIdStr = metadata.path("collection_id").asText(null);
        if (collectionIdStr == null || collectionIdStr.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(collectionIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("[STRIPE-WEBHOOK] Invalid collection_id in metadata: {}", collectionIdStr);
            return null;
        }
    }

    private BigDecimal fromMinorUnits(long amountCents) {
        return BigDecimal.valueOf(amountCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
