package com.stablecoin.payments.ledger.infrastructure.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

public class AccountBalanceId implements Serializable {

    private String accountCode;
    private String currency;

    public AccountBalanceId() {
    }

    public AccountBalanceId(String accountCode, String currency) {
        this.accountCode = accountCode;
        this.currency = currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountBalanceId that = (AccountBalanceId) o;
        return Objects.equals(accountCode, that.accountCode) && Objects.equals(currency, that.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountCode, currency);
    }
}
