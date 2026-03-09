package com.stablecoin.payments.custody.fixtures;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.StablecoinTicker;
import com.stablecoin.payments.custody.domain.port.SignRequest;

import java.math.BigDecimal;
import java.util.UUID;

public final class DevCustodyFixtures {

    private DevCustodyFixtures() {}

    public static final UUID DEV_TRANSFER_ID =
            UUID.fromString("d1e2f3a4-b5c6-7890-abcd-ef1234567890");

    public static final String DEV_FROM_ADDRESS = "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD53";
    public static final String DEV_TO_ADDRESS = "0x1234567890AbCdEf1234567890aBcDeF12345678";
    public static final String SOL_FROM_ADDRESS = "FbGeZS8LiPCZiFpFwdUUeF2yxXtSsdfJoHTsVMvM8STh";
    public static final String SOL_TO_ADDRESS = "9WzDXwBbmPg2WjM1CdGUgJb4Mqp7TAUKM3MEPQDwKrSM";

    /**
     * A sign request for Base USDC transfer via dev custody.
     */
    public static SignRequest aDevSignRequest() {
        return new SignRequest(
                DEV_TRANSFER_ID,
                new ChainId("base"),
                DEV_FROM_ADDRESS,
                DEV_TO_ADDRESS,
                new BigDecimal("100.00"),
                StablecoinTicker.of("USDC"),
                5L,
                null
        );
    }

    /**
     * A sign request for Ethereum USDC transfer via dev custody.
     */
    public static SignRequest aDevEthereumSignRequest() {
        return new SignRequest(
                DEV_TRANSFER_ID,
                new ChainId("ethereum"),
                DEV_FROM_ADDRESS,
                DEV_TO_ADDRESS,
                new BigDecimal("250.50"),
                StablecoinTicker.of("USDC"),
                10L,
                null
        );
    }

    /**
     * A sign request for Solana USDC transfer via dev custody.
     */
    public static SignRequest aDevSolanaSignRequest() {
        return new SignRequest(
                DEV_TRANSFER_ID,
                new ChainId("solana"),
                SOL_FROM_ADDRESS,
                SOL_TO_ADDRESS,
                new BigDecimal("500.00"),
                StablecoinTicker.of("USDC"),
                null,
                null
        );
    }

    /**
     * A sign request for an unsupported chain (stellar) — used for error path testing.
     */
    public static SignRequest aDevUnsupportedChainSignRequest() {
        return new SignRequest(
                DEV_TRANSFER_ID,
                new ChainId("stellar"),
                DEV_FROM_ADDRESS,
                DEV_TO_ADDRESS,
                new BigDecimal("100.00"),
                StablecoinTicker.of("USDC"),
                1L,
                null
        );
    }
}
