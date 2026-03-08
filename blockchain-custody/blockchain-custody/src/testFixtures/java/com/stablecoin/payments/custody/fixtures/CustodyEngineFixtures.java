package com.stablecoin.payments.custody.fixtures;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.StablecoinTicker;
import com.stablecoin.payments.custody.domain.port.SignRequest;

import java.math.BigDecimal;
import java.util.UUID;

public final class CustodyEngineFixtures {

    private CustodyEngineFixtures() {}

    public static final UUID TRANSFER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    public static final String VAULT_ACCOUNT_ID = "42";

    /**
     * A sign request for Base USDC transfer.
     */
    public static SignRequest aSignRequest() {
        return new SignRequest(
                TRANSFER_ID,
                new ChainId("base"),
                "0xSenderAddress",
                "0xRecipientAddress",
                new BigDecimal("1500.50"),
                StablecoinTicker.of("USDC"),
                42L,
                VAULT_ACCOUNT_ID
        );
    }
}
