package com.stablecoin.payments.ledger.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.stablecoin.payments.ledger.domain.model.EntryType.CREDIT;
import static com.stablecoin.payments.ledger.domain.model.EntryType.DEBIT;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EntryType")
class EntryTypeTest {

    @Test
    @DisplayName("opposite of DEBIT is CREDIT")
    void oppositeOfDebitIsCredit() {
        assertThat(DEBIT.opposite()).isEqualTo(CREDIT);
    }

    @Test
    @DisplayName("opposite of CREDIT is DEBIT")
    void oppositeOfCreditIsDebit() {
        assertThat(CREDIT.opposite()).isEqualTo(DEBIT);
    }

    @Test
    @DisplayName("double opposite returns original")
    void doubleOppositeReturnsSame() {
        assertThat(DEBIT.opposite().opposite()).isEqualTo(DEBIT);
    }
}
