package com.stablecoin.payments.merchant.onboarding.infrastructure.temporal.signal;

import java.io.Serializable;

/**
 * Signal payload sent when a merchant uploads a required document. Received by
 * {@code MerchantOnboardingWorkflow.documentUploaded()}.
 */
public record DocumentUploadedSignal(String documentType, String fileName, String s3Key) implements Serializable {
}
