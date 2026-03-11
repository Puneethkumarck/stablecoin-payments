package com.stablecoin.payments.ledger.infrastructure.persistence.entity;

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

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountEntity {

    @Id
    @Column(name = "account_code", length = 10, updatable = false)
    private String accountCode;

    @Column(name = "account_name", length = 100, nullable = false)
    private String accountName;

    @Column(name = "account_type", length = 20, nullable = false)
    private String accountType;

    @Column(name = "normal_balance", length = 6, nullable = false)
    private String normalBalance;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
