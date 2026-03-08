package com.stablecoin.payments.orchestrator.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Corridor value object")
class CorridorTest {

    @Test
    @DisplayName("creates valid corridor with distinct countries")
    void createsValidCorridor() {
        var corridor = new Corridor("US", "DE");

        var expected = new Corridor("US", "DE");

        assertThat(corridor)
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("rejects null sourceCountry")
    void rejectsNullSourceCountry() {
        assertThatThrownBy(() -> new Corridor(null, "DE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceCountry is required");
    }

    @Test
    @DisplayName("rejects blank sourceCountry")
    void rejectsBlankSourceCountry() {
        assertThatThrownBy(() -> new Corridor("  ", "DE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceCountry is required");
    }

    @Test
    @DisplayName("rejects empty sourceCountry")
    void rejectsEmptySourceCountry() {
        assertThatThrownBy(() -> new Corridor("", "DE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceCountry is required");
    }

    @Test
    @DisplayName("rejects null targetCountry")
    void rejectsNullTargetCountry() {
        assertThatThrownBy(() -> new Corridor("US", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("targetCountry is required");
    }

    @Test
    @DisplayName("rejects blank targetCountry")
    void rejectsBlankTargetCountry() {
        assertThatThrownBy(() -> new Corridor("US", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("targetCountry is required");
    }

    @Test
    @DisplayName("rejects same sourceCountry and targetCountry")
    void rejectsSameCountries() {
        assertThatThrownBy(() -> new Corridor("US", "US"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceCountry must not equal targetCountry");
    }

    @Test
    @DisplayName("accepts different country codes")
    void acceptsDifferentCountryCodes() {
        var corridor = new Corridor("GB", "JP");

        assertThat(corridor.sourceCountry()).isEqualTo("GB");
    }
}
