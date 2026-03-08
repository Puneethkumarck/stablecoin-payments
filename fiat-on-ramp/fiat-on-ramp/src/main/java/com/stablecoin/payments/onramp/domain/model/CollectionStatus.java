package com.stablecoin.payments.onramp.domain.model;

public enum CollectionStatus {
    PENDING,
    PAYMENT_INITIATED,
    AWAITING_CONFIRMATION,
    COLLECTED,
    COLLECTION_FAILED,
    AMOUNT_MISMATCH,
    MANUAL_REVIEW,
    REFUND_INITIATED,
    REFUND_PROCESSING,
    REFUNDED
}
