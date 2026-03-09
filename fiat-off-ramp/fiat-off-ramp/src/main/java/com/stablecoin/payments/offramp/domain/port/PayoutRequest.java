package com.stablecoin.payments.offramp.domain.port;

import com.stablecoin.payments.offramp.domain.model.BankAccount;
import com.stablecoin.payments.offramp.domain.model.MobileMoneyAccount;
import com.stablecoin.payments.offramp.domain.model.PartnerIdentifier;
import com.stablecoin.payments.offramp.domain.model.PaymentRail;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Port DTO for initiating a fiat payout with an off-ramp partner.
 * <p>
 * This is different from the API PayoutRequest — this is the domain's
 * outbound request to the partner gateway.
 */
public record PayoutRequest(
        UUID payoutId,
        BigDecimal fiatAmount,
        String currency,
        BankAccount bankAccount,
        MobileMoneyAccount mobileMoneyAccount,
        PaymentRail paymentRail,
        PartnerIdentifier partnerIdentifier
) {}
