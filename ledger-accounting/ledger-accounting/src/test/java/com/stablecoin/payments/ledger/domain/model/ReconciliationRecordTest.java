package com.stablecoin.payments.ledger.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.stablecoin.payments.ledger.domain.model.ReconciliationLegType.CHAIN_TRANSFERRED;
import static com.stablecoin.payments.ledger.domain.model.ReconciliationLegType.FIAT_IN;
import static com.stablecoin.payments.ledger.domain.model.ReconciliationLegType.FIAT_OUT;
import static com.stablecoin.payments.ledger.domain.model.ReconciliationLegType.FX_RATE;
import static com.stablecoin.payments.ledger.domain.model.ReconciliationLegType.STABLECOIN_MINTED;
import static com.stablecoin.payments.ledger.domain.model.ReconciliationLegType.STABLECOIN_REDEEMED;
import static com.stablecoin.payments.ledger.domain.model.ReconciliationStatus.DISCREPANCY;
import static com.stablecoin.payments.ledger.domain.model.ReconciliationStatus.PARTIAL;
import static com.stablecoin.payments.ledger.domain.model.ReconciliationStatus.PENDING;
import static com.stablecoin.payments.ledger.domain.model.ReconciliationStatus.RECONCILED;
import static com.stablecoin.payments.ledger.fixtures.LedgerFixtures.PAYMENT_ID;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.DEFAULT_TOLERANCE;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aChainTransferredLeg;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aFiatInLeg;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aFiatOutLeg;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aFxRateLeg;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aPendingRecord;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aRecordWithAllRequiredLegs;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aStablecoinMintedLeg;
import static com.stablecoin.payments.ledger.fixtures.ReconciliationFixtures.aStablecoinRedeemedLeg;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReconciliationRecord")
class ReconciliationRecordTest {

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("creates a PENDING record with no legs")
        void createsPendingRecord() {
            var record = ReconciliationRecord.create(PAYMENT_ID, DEFAULT_TOLERANCE);

            assertThat(record.status()).isEqualTo(PENDING);
            assertThat(record.legs()).isEmpty();
            assertThat(record.reconciledAt()).isNull();
        }

        @Test
        @DisplayName("generates a random recId")
        void generatesRandomRecId() {
            var record1 = ReconciliationRecord.create(PAYMENT_ID, DEFAULT_TOLERANCE);
            var record2 = ReconciliationRecord.create(PAYMENT_ID, DEFAULT_TOLERANCE);

            assertThat(record1.recId()).isNotEqualTo(record2.recId());
        }
    }

    @Nested
    @DisplayName("addLeg()")
    class AddLeg {

        @Test
        @DisplayName("adds first leg and transitions to PARTIAL")
        void addsFirstLeg() {
            var record = aPendingRecord().addLeg(aFiatInLeg());

            assertThat(record.status()).isEqualTo(PARTIAL);
            assertThat(record.legs()).hasSize(1);
        }

        @Test
        @DisplayName("adds second leg and stays PARTIAL")
        void addsSecondLeg() {
            var record = aPendingRecord()
                    .addLeg(aFiatInLeg())
                    .addLeg(aStablecoinMintedLeg());

            assertThat(record.status()).isEqualTo(PARTIAL);
            assertThat(record.legs()).hasSize(2);
        }

        @Test
        @DisplayName("adds all 5 required legs and stays PARTIAL until finalized")
        void addsAllRequiredLegs() {
            var record = aRecordWithAllRequiredLegs();

            assertThat(record.status()).isEqualTo(PARTIAL);
            assertThat(record.legs()).hasSize(5);
        }

        @Test
        @DisplayName("adds FX_RATE metadata leg (6th leg)")
        void addsFxRateMetadataLeg() {
            var record = aRecordWithAllRequiredLegs().addLeg(aFxRateLeg());

            assertThat(record.legs()).hasSize(6);
        }
    }

    @Nested
    @DisplayName("hasLeg()")
    class HasLeg {

        @Test
        @DisplayName("returns true when leg type exists")
        void returnsTrueWhenExists() {
            var record = aPendingRecord().addLeg(aFiatInLeg());

            assertThat(record.hasLeg(FIAT_IN)).isTrue();
        }

        @Test
        @DisplayName("returns false when leg type does not exist")
        void returnsFalseWhenNotExists() {
            var record = aPendingRecord().addLeg(aFiatInLeg());

            assertThat(record.hasLeg(FIAT_OUT)).isFalse();
        }
    }

    @Nested
    @DisplayName("hasAllRequiredLegs()")
    class HasAllRequiredLegs {

        @Test
        @DisplayName("returns false with only some legs")
        void returnsFalseWithPartialLegs() {
            var record = aPendingRecord()
                    .addLeg(aFiatInLeg())
                    .addLeg(aStablecoinMintedLeg());

            assertThat(record.hasAllRequiredLegs()).isFalse();
        }

        @Test
        @DisplayName("returns true with all 5 required legs")
        void returnsTrueWithAllLegs() {
            var record = aRecordWithAllRequiredLegs();

            assertThat(record.hasAllRequiredLegs()).isTrue();
        }

        @Test
        @DisplayName("FX_RATE is not required for hasAllRequiredLegs()")
        void fxRateNotRequired() {
            var record = aRecordWithAllRequiredLegs();

            assertThat(record.hasLeg(FX_RATE)).isFalse();
            assertThat(record.hasAllRequiredLegs()).isTrue();
        }
    }

    @Nested
    @DisplayName("finalize()")
    class Finalize {

        @Test
        @DisplayName("RECONCILED when all legs present and discrepancy within tolerance")
        void reconciledWhenWithinTolerance() {
            var record = aRecordWithAllRequiredLegs()
                    .finalize(new BigDecimal("0.005"));

            assertThat(record.status()).isEqualTo(RECONCILED);
            assertThat(record.reconciledAt()).isNotNull();
        }

        @Test
        @DisplayName("RECONCILED when discrepancy equals tolerance")
        void reconciledWhenEqualsTolerance() {
            var record = aRecordWithAllRequiredLegs()
                    .finalize(new BigDecimal("0.01"));

            assertThat(record.status()).isEqualTo(RECONCILED);
        }

        @Test
        @DisplayName("RECONCILED when discrepancy is zero")
        void reconciledWhenZeroDiscrepancy() {
            var record = aRecordWithAllRequiredLegs()
                    .finalize(BigDecimal.ZERO);

            assertThat(record.status()).isEqualTo(RECONCILED);
        }

        @Test
        @DisplayName("DISCREPANCY when discrepancy exceeds tolerance")
        void discrepancyWhenExceedsTolerance() {
            var record = aRecordWithAllRequiredLegs()
                    .finalize(new BigDecimal("0.02"));

            assertThat(record.status()).isEqualTo(DISCREPANCY);
            assertThat(record.reconciledAt()).isNull();
        }

        @Test
        @DisplayName("DISCREPANCY when negative discrepancy exceeds tolerance")
        void discrepancyWithNegativeValue() {
            var record = aRecordWithAllRequiredLegs()
                    .finalize(new BigDecimal("-0.05"));

            assertThat(record.status()).isEqualTo(DISCREPANCY);
        }

        @Test
        @DisplayName("DISCREPANCY when not all legs present even if discrepancy within tolerance")
        void discrepancyWhenMissingLegs() {
            var record = aPendingRecord()
                    .addLeg(aFiatInLeg())
                    .addLeg(aStablecoinMintedLeg())
                    .finalize(BigDecimal.ZERO);

            assertThat(record.status()).isEqualTo(DISCREPANCY);
        }
    }

    @Nested
    @DisplayName("markDiscrepancy()")
    class MarkDiscrepancy {

        @Test
        @DisplayName("sets status to DISCREPANCY")
        void setsDiscrepancyStatus() {
            var record = aPendingRecord().markDiscrepancy();

            assertThat(record.status()).isEqualTo(DISCREPANCY);
        }

        @Test
        @DisplayName("reconciledAt remains null")
        void reconciledAtRemainsNull() {
            var record = aPendingRecord().markDiscrepancy();

            assertThat(record.reconciledAt()).isNull();
        }

        @Test
        @DisplayName("preserves existing legs")
        void preservesExistingLegs() {
            var record = aPendingRecord()
                    .addLeg(aFiatInLeg())
                    .markDiscrepancy();

            assertThat(record.legs()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("immutability")
    class Immutability {

        @Test
        @DisplayName("addLeg returns a new instance")
        void addLegReturnsNewInstance() {
            var original = aPendingRecord();
            var withLeg = original.addLeg(aFiatInLeg());

            assertThat(original.legs()).isEmpty();
            assertThat(withLeg.legs()).hasSize(1);
        }

        @Test
        @DisplayName("finalize returns a new instance")
        void finalizeReturnsNewInstance() {
            var record = aRecordWithAllRequiredLegs();
            var finalized = record.finalize(BigDecimal.ZERO);

            assertThat(record.status()).isEqualTo(PARTIAL);
            assertThat(finalized.status()).isEqualTo(RECONCILED);
        }

        @Test
        @DisplayName("legs list is unmodifiable")
        void legsListUnmodifiable() {
            var record = aPendingRecord().addLeg(aFiatInLeg());

            assertThat(record.legs()).isUnmodifiable();
        }
    }

    @Nested
    @DisplayName("leg types — all 6 types supported")
    class LegTypes {

        @Test
        @DisplayName("FIAT_IN leg")
        void fiatInLeg() {
            assertThat(aFiatInLeg().legType()).isEqualTo(FIAT_IN);
        }

        @Test
        @DisplayName("STABLECOIN_MINTED leg")
        void stablecoinMintedLeg() {
            assertThat(aStablecoinMintedLeg().legType()).isEqualTo(STABLECOIN_MINTED);
        }

        @Test
        @DisplayName("CHAIN_TRANSFERRED leg")
        void chainTransferredLeg() {
            assertThat(aChainTransferredLeg().legType()).isEqualTo(CHAIN_TRANSFERRED);
        }

        @Test
        @DisplayName("STABLECOIN_REDEEMED leg")
        void stablecoinRedeemedLeg() {
            assertThat(aStablecoinRedeemedLeg().legType()).isEqualTo(STABLECOIN_REDEEMED);
        }

        @Test
        @DisplayName("FIAT_OUT leg")
        void fiatOutLeg() {
            assertThat(aFiatOutLeg().legType()).isEqualTo(FIAT_OUT);
        }

        @Test
        @DisplayName("FX_RATE leg")
        void fxRateLeg() {
            assertThat(aFxRateLeg().legType()).isEqualTo(FX_RATE);
        }
    }
}
