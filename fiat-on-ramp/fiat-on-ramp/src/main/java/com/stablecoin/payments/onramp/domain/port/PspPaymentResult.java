package com.stablecoin.payments.onramp.domain.port;

public record PspPaymentResult(String pspReference, String status) {
}
