package com.stablecoin.payments.compliance.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static com.stablecoin.payments.compliance.domain.model.RiskBand.CRITICAL;
import static com.stablecoin.payments.compliance.domain.model.RiskBand.LOW;
import static com.stablecoin.payments.compliance.domain.model.RiskBand.MEDIUM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RiskScore value object")
class RiskScoreTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create RiskScore with valid score within range")
        void should_createRiskScore_when_scoreIsValid() {
            var riskScore = RiskScore.builder()
                    .score(50)
                    .band(MEDIUM)
                    .factors(List.of("cross_border"))
                    .build();

            var expected = RiskScore.builder()
                    .score(50)
                    .band(MEDIUM)
                    .factors(List.of("cross_border"))
                    .build();
            assertThat(riskScore)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("should create RiskScore with score 0")
        void should_createRiskScore_when_scoreIsZero() {
            var riskScore = RiskScore.builder()
                    .score(0)
                    .band(LOW)
                    .factors(List.of())
                    .build();

            assertThat(riskScore.score()).isZero();
        }

        @Test
        @DisplayName("should create RiskScore with score 100")
        void should_createRiskScore_when_scoreIs100() {
            var riskScore = RiskScore.builder()
                    .score(100)
                    .band(CRITICAL)
                    .factors(List.of("multiple_flags"))
                    .build();

            assertThat(riskScore.score()).isEqualTo(100);
        }

        @Test
        @DisplayName("should throw when score is negative")
        void should_throw_when_scoreIsNegative() {
            assertThatThrownBy(() -> RiskScore.builder()
                    .score(-1)
                    .band(LOW)
                    .factors(List.of())
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between 0 and 100");
        }

        @Test
        @DisplayName("should throw when score exceeds 100")
        void should_throw_when_scoreExceeds100() {
            assertThatThrownBy(() -> RiskScore.builder()
                    .score(101)
                    .band(CRITICAL)
                    .factors(List.of())
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between 0 and 100");
        }
    }

    @Nested
    @DisplayName("bandForScore()")
    class BandForScore {

        @ParameterizedTest(name = "score {0} should map to {1}")
        @CsvSource({
                "0, LOW",
                "1, LOW",
                "25, LOW",
                "26, MEDIUM",
                "50, MEDIUM",
                "51, HIGH",
                "75, HIGH",
                "76, CRITICAL",
                "100, CRITICAL"
        })
        @DisplayName("should return correct band for boundary scores")
        void should_returnCorrectBand_when_scoreIsAtBoundary(int score, RiskBand expectedBand) {
            assertThat(RiskScore.bandForScore(score)).isEqualTo(expectedBand);
        }
    }
}
