package com.stablecoin.payments.offramp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartnerIdentifierTest {

    @Test
    @DisplayName("creates valid partner identifier")
    void createsValidPartnerIdentifier() {
        var partner = new PartnerIdentifier("modulr_001", "Modulr");

        var expected = new PartnerIdentifier("modulr_001", "Modulr");
        assertThat(partner).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    @DisplayName("throws when partnerId is null")
    void throwsWhenPartnerIdNull() {
        assertThatThrownBy(() -> new PartnerIdentifier(null, "Modulr"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Partner ID");
    }

    @Test
    @DisplayName("throws when partnerId is blank")
    void throwsWhenPartnerIdBlank() {
        assertThatThrownBy(() -> new PartnerIdentifier("  ", "Modulr"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Partner ID");
    }

    @Test
    @DisplayName("throws when partnerName is null")
    void throwsWhenPartnerNameNull() {
        assertThatThrownBy(() -> new PartnerIdentifier("modulr_001", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Partner name");
    }

    @Test
    @DisplayName("throws when partnerName is blank")
    void throwsWhenPartnerNameBlank() {
        assertThatThrownBy(() -> new PartnerIdentifier("modulr_001", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Partner name");
    }
}
