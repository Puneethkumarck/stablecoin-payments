package com.stablecoin.payments.custody.fixtures;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.StablecoinTicker;
import com.stablecoin.payments.custody.domain.model.Wallet;
import com.stablecoin.payments.custody.domain.model.WalletPurpose;
import com.stablecoin.payments.custody.domain.model.WalletTier;

public final class WalletFixtures {

    private WalletFixtures() {}

    public static final ChainId CHAIN_BASE = new ChainId("base");
    public static final String ADDRESS = "0xWalletAddress1234567890abcdef";
    public static final String ADDRESS_CHECKSUM = "0xWalletAddress1234567890AbCdEf";
    public static final WalletTier TIER_HOT = WalletTier.HOT;
    public static final WalletPurpose PURPOSE_ON_RAMP = WalletPurpose.ON_RAMP;
    public static final String CUSTODIAN = "fireblocks";
    public static final String VAULT_ACCOUNT_ID = "vault-001";
    public static final StablecoinTicker USDC = StablecoinTicker.of("USDC");

    /**
     * An active wallet with default values.
     */
    public static Wallet anActiveWallet() {
        return Wallet.create(
                CHAIN_BASE, ADDRESS, ADDRESS_CHECKSUM,
                TIER_HOT, PURPOSE_ON_RAMP,
                CUSTODIAN, VAULT_ACCOUNT_ID, USDC
        );
    }

    /**
     * A deactivated wallet.
     */
    public static Wallet aDeactivatedWallet() {
        return anActiveWallet().deactivate();
    }
}
