package com.stablecoin.payments.custody.infrastructure.persistence.entity;

import com.stablecoin.payments.custody.domain.model.ParticipantType;
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
import java.util.UUID;

@Entity
@Table(name = "transfer_participants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferParticipantEntity {

    @Id
    @Column(name = "participant_id", updatable = false)
    private UUID participantId;

    @Column(name = "transfer_id")
    private UUID transferId;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type", length = 10)
    private ParticipantType participantType;

    @Column(name = "address", length = 128)
    private String address;

    @Column(name = "wallet_id")
    private UUID walletId;

    @Column(name = "amount", precision = 30, scale = 8)
    private BigDecimal amount;

    @Column(name = "asset_code", length = 20)
    private String assetCode;
}
