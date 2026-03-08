package com.stablecoin.payments.custody.infrastructure.persistence.entity;

import com.stablecoin.payments.custody.domain.model.WalletPurpose;
import com.stablecoin.payments.custody.domain.model.WalletTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletEntity {

    @Id
    @Column(name = "wallet_id", updatable = false)
    private UUID walletId;

    @Column(name = "chain_id", length = 20)
    private String chainId;

    @Column(name = "address", length = 128)
    private String address;

    @Column(name = "address_checksum", length = 128)
    private String addressChecksum;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", length = 10)
    private WalletTier tier;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", length = 20)
    private WalletPurpose purpose;

    @Column(name = "custodian", length = 50)
    private String custodian;

    @Column(name = "vault_account_id", length = 200)
    private String vaultAccountId;

    @Column(name = "stablecoin", length = 20)
    private String stablecoin;

    @Column(name = "is_active")
    private boolean active;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;
}
