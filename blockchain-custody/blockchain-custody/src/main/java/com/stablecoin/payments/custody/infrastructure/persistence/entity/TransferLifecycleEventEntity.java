package com.stablecoin.payments.custody.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "transfer_lifecycle_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferLifecycleEventEntity {

    @Id
    @Column(name = "event_id", updatable = false)
    private UUID eventId;

    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "state", length = 50)
    private String state;

    @Column(name = "participant_type", length = 10)
    private String participantType;

    @Column(name = "address", length = 128)
    private String address;

    @Column(name = "occurred_at")
    private Instant occurredAt;
}
