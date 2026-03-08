package com.stablecoin.payments.onramp.api;

import java.math.BigDecimal;

public record RefundRequest(BigDecimal refundAmount, String currency, String reason) {
}
