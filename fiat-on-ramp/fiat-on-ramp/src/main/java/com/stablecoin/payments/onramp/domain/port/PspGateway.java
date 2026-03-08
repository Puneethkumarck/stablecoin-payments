package com.stablecoin.payments.onramp.domain.port;

public interface PspGateway {

    PspPaymentResult initiatePayment(PspPaymentRequest request);

    PspRefundResult initiateRefund(PspRefundRequest request);
}
