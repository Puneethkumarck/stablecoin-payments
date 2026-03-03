package com.stablecoin.payments.gateway.iam.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorridorTest {

    @Test
    void shouldCreateValidCorridor() {
        var corridor = new Corridor("US", "DE");

        assertThat(corridor.sourceCountry()).isEqualTo("US");
        assertThat(corridor.targetCountry()).isEqualTo("DE");
    }

    @Test
    void shouldRejectNullSourceCountry() {
        assertThatThrownBy(() -> new Corridor(null, "DE"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullTargetCountry() {
        assertThatThrownBy(() -> new Corridor("US", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectInvalidLengthSourceCountry() {
        assertThatThrownBy(() -> new Corridor("USA", "DE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alpha-2");
    }

    @Test
    void shouldRejectInvalidLengthTargetCountry() {
        assertThatThrownBy(() -> new Corridor("US", "DEU"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldSupportEquality() {
        var a = new Corridor("US", "DE");
        var b = new Corridor("US", "DE");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
