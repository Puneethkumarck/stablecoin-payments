package com.stablecoin.payments.custody.domain.model;

import lombok.AccessLevel;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a custody wallet on a specific blockchain chain.
 * <p>
 * Wallets are managed by a custody provider (e.g., Fireblocks) and have a specific
 * tier (HOT/WARM/COLD) and purpose (ON_RAMP/OFF_RAMP/SWEEP/RESERVE).
 */
@Builder(toBuilder = true, access = AccessLevel.PACKAGE)
public record Wallet(
        UUID walletId,
        ChainId chainId,
        String address,
        String addressChecksum,
        WalletTier tier,
        WalletPurpose purpose,
        String custodian,
        String vaultAccountId,
        StablecoinTicker stablecoin,
        boolean active,
        Instant createdAt,
        Instant deactivatedAt
) {

    // -- Factory Method -------------------------------------------------

    /**
     * Creates a new active wallet.
     */
    public static Wallet create(ChainId chainId, String address, String addressChecksum,
                                WalletTier tier, WalletPurpose purpose,
                                String custodian, String vaultAccountId,
                                StablecoinTicker stablecoin) {
        if (chainId == null) {
            throw new IllegalArgumentException("chainId is required");
        }
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("address is required");
        }
        if (addressChecksum == null || addressChecksum.isBlank()) {
            throw new IllegalArgumentException("addressChecksum is required");
        }
        if (tier == null) {
            throw new IllegalArgumentException("tier is required");
        }
        if (purpose == null) {
            throw new IllegalArgumentException("purpose is required");
        }
        if (custodian == null || custodian.isBlank()) {
            throw new IllegalArgumentException("custodian is required");
        }
        if (vaultAccountId == null || vaultAccountId.isBlank()) {
            throw new IllegalArgumentException("vaultAccountId is required");
        }
        if (stablecoin == null) {
            throw new IllegalArgumentException("stablecoin is required");
        }

        var now = Instant.now();
        return Wallet.builder()
                .walletId(UUID.randomUUID())
                .chainId(chainId)
                .address(address)
                .addressChecksum(addressChecksum)
                .tier(tier)
                .purpose(purpose)
                .custodian(custodian)
                .vaultAccountId(vaultAccountId)
                .stablecoin(stablecoin)
                .active(true)
                .createdAt(now)
                .build();
    }

    // -- Domain Methods -------------------------------------------------

    /**
     * Deactivates this wallet. Returns a new instance with active=false.
     */
    public Wallet deactivate() {
        if (!active) {
            throw new IllegalStateException(
                    "Wallet %s is already deactivated".formatted(walletId));
        }
        return toBuilder()
                .active(false)
                .deactivatedAt(Instant.now())
                .build();
    }

    // -- Query Methods --------------------------------------------------

    /**
     * Returns true if this wallet is currently active.
     */
    public boolean isActive() {
        return active;
    }
}
