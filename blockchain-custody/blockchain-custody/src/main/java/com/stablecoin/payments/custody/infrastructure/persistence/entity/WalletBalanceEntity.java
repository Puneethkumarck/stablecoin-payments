package com.stablecoin.payments.custody.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallet_balances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletBalanceEntity {

    @Id
    @Column(name = "balance_id", updatable = false)
    private UUID balanceId;

    @Column(name = "wallet_id")
    private UUID walletId;

    @Column(name = "chain_id", length = 20)
    private String chainId;

    @Column(name = "stablecoin", length = 20)
    private String stablecoin;

    @Column(name = "available_balance", precision = 30, scale = 8)
    private BigDecimal availableBalance;

    @Column(name = "reserved_balance", precision = 30, scale = 8)
    private BigDecimal reservedBalance;

    @Column(name = "blockchain_balance", precision = 30, scale = 8)
    private BigDecimal blockchainBalance;

    @Column(name = "last_indexed_block")
    private long lastIndexedBlock;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
