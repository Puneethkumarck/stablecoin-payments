package com.stablecoin.payments.offramp.domain.port;

/**
 * Port interface for webhook signature validation.
 * <p>
 * Implemented by partner-specific adapters (e.g., Modulr) to verify
 * the authenticity of incoming webhook requests using HMAC signatures.
 */
public interface WebhookSignatureValidator {

    /**
     * Validates the webhook signature against the raw payload.
     *
     * @param payload   the raw request body
     * @param signature the signature header value
     * @return {@code true} if the signature is valid
     */
    boolean isValid(String payload, String signature);
}
