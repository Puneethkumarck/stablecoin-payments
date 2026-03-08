package com.stablecoin.payments.custody.fixtures;

import com.stablecoin.payments.custody.domain.model.ChainId;
import com.stablecoin.payments.custody.domain.model.StablecoinTicker;
import com.stablecoin.payments.custody.domain.model.WalletBalance;

import java.math.BigDecimal;
import java.util.UUID;

public final class WalletBalanceFixtures {

    private WalletBalanceFixtures() {}

    public static final UUID WALLET_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    public static final ChainId CHAIN_BASE = new ChainId("base");
    public static final StablecoinTicker USDC = StablecoinTicker.of("USDC");

    /**
     * A freshly initialized balance with all zeros.
     */
    public static WalletBalance aZeroBalance() {
        return WalletBalance.initialize(WALLET_ID, CHAIN_BASE, USDC);
    }

    /**
     * A balance with specific available and reserved amounts.
     * Achieved by syncing from chain and then reserving.
     */
    public static WalletBalance aBalanceWith(BigDecimal available, BigDecimal reserved) {
        var total = available.add(reserved);
        var synced = aZeroBalance().syncFromChain(total, 100L);
        if (reserved.compareTo(BigDecimal.ZERO) > 0) {
            return synced.reserve(reserved);
        }
        return synced;
    }
}
