package com.stablecoin.payments.merchant.onboarding.infrastructure.document;

import com.stablecoin.payments.merchant.onboarding.domain.merchant.DocumentStore;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class MockDocumentStoreAdapter implements DocumentStore {

    @Override
    public String generateUploadUrl(UUID merchantId, String documentType, String fileName) {
        var url = "https://mock-s3.local/%s/%s/%s".formatted(merchantId, documentType, fileName);
        log.debug("Generated mock upload URL merchantId={} documentType={}", merchantId, documentType);
        return url;
    }
}
