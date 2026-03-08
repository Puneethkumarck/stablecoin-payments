package com.stablecoin.payments.custody.infrastructure.persistence.entity;

import com.stablecoin.payments.custody.domain.model.TransferStatus;
import com.stablecoin.payments.custody.domain.model.TransferType;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chain_transfers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChainTransferEntity {

    @Id
    @Column(name = "transfer_id", updatable = false)
    private UUID transferId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_type", length = 20)
    private TransferType transferType;

    @Column(name = "parent_transfer_id")
    private UUID parentTransferId;

    @Column(name = "chain_id", length = 20)
    private String chainId;

    @Column(name = "stablecoin", length = 20)
    private String stablecoin;

    @Column(name = "amount", precision = 30, scale = 8)
    private BigDecimal amount;

    @Column(name = "from_wallet_id")
    private UUID fromWalletId;

    @Column(name = "to_address", length = 128)
    private String toAddress;

    @Column(name = "from_address", length = 128)
    private String fromAddress;

    @Column(name = "nonce")
    private Long nonce;

    @Column(name = "tx_hash", length = 128)
    private String txHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    private TransferStatus status;

    @Column(name = "block_number")
    private Long blockNumber;

    @Column(name = "block_confirmed_at")
    private Instant blockConfirmedAt;

    @Column(name = "confirmations")
    private Integer confirmations;

    @Column(name = "gas_used", precision = 20, scale = 0)
    private BigDecimal gasUsed;

    @Column(name = "gas_price_gwei", precision = 20, scale = 9)
    private BigDecimal gasPriceGwei;

    @Column(name = "attempt_count")
    private int attemptCount;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
