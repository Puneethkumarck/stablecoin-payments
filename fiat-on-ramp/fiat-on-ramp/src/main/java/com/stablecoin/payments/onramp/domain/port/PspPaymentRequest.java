package com.stablecoin.payments.onramp.domain.port;

import com.stablecoin.payments.onramp.domain.model.BankAccount;
import com.stablecoin.payments.onramp.domain.model.Money;
import com.stablecoin.payments.onramp.domain.model.PaymentRail;

import java.util.UUID;

public record PspPaymentRequest(
        UUID collectionId,
        Money amount,
        PaymentRail paymentRail,
        BankAccount senderAccount,
        String pspName
) {
}
