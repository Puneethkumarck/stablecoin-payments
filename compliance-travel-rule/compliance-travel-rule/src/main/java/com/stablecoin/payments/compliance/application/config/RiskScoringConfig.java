package com.stablecoin.payments.compliance.application.config;

import com.stablecoin.payments.compliance.domain.model.RiskScoringWeights;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Configuration for the risk scoring engine.
 * Maps {@code app.compliance.risk-scoring.*} properties to {@link RiskScoringWeights}.
 */
@Configuration
public class RiskScoringConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.compliance.risk-scoring")
    public RiskScoringProperties riskScoringProperties() {
        return new RiskScoringProperties();
    }

    @Bean
    public RiskScoringWeights riskScoringWeights(RiskScoringProperties props) {
        return RiskScoringWeights.builder()
                .kycTier1Penalty(props.getKycTier1Penalty())
                .highValuePenalty(props.getHighValuePenalty())
                .amlFlagPenalty(props.getAmlFlagPenalty())
                .crossBorderPenalty(props.getCrossBorderPenalty())
                .newCustomerPenalty(props.getNewCustomerPenalty())
                .highCorridorRiskPenalty(props.getHighCorridorRiskPenalty())
                .amountToLimitRatioPenalty(props.getAmountToLimitRatioPenalty())
                .highVelocityPenalty(props.getHighVelocityPenalty())
                .corridorRiskScores(props.getCorridorRiskScores() != null
                        ? props.getCorridorRiskScores()
                        : Map.of())
                .build();
    }

    /**
     * Mutable properties bean for Spring Boot's {@code @ConfigurationProperties} binding.
     * Defaults match {@link RiskScoringWeights#defaults()}.
     */
    public static class RiskScoringProperties {
        private int kycTier1Penalty = RiskScoringWeights.DEFAULT_KYC_TIER1_PENALTY;
        private int highValuePenalty = RiskScoringWeights.DEFAULT_HIGH_VALUE_PENALTY;
        private int amlFlagPenalty = RiskScoringWeights.DEFAULT_AML_FLAG_PENALTY;
        private int crossBorderPenalty = RiskScoringWeights.DEFAULT_CROSS_BORDER_PENALTY;
        private int newCustomerPenalty = RiskScoringWeights.DEFAULT_NEW_CUSTOMER_PENALTY;
        private int highCorridorRiskPenalty = RiskScoringWeights.DEFAULT_HIGH_CORRIDOR_RISK_PENALTY;
        private int amountToLimitRatioPenalty = RiskScoringWeights.DEFAULT_AMOUNT_TO_LIMIT_RATIO_PENALTY;
        private int highVelocityPenalty = RiskScoringWeights.DEFAULT_HIGH_VELOCITY_PENALTY;
        private Map<String, Integer> corridorRiskScores;

        public int getKycTier1Penalty() { return kycTier1Penalty; }
        public void setKycTier1Penalty(int kycTier1Penalty) { this.kycTier1Penalty = kycTier1Penalty; }
        public int getHighValuePenalty() { return highValuePenalty; }
        public void setHighValuePenalty(int highValuePenalty) { this.highValuePenalty = highValuePenalty; }
        public int getAmlFlagPenalty() { return amlFlagPenalty; }
        public void setAmlFlagPenalty(int amlFlagPenalty) { this.amlFlagPenalty = amlFlagPenalty; }
        public int getCrossBorderPenalty() { return crossBorderPenalty; }
        public void setCrossBorderPenalty(int crossBorderPenalty) { this.crossBorderPenalty = crossBorderPenalty; }
        public int getNewCustomerPenalty() { return newCustomerPenalty; }
        public void setNewCustomerPenalty(int newCustomerPenalty) { this.newCustomerPenalty = newCustomerPenalty; }
        public int getHighCorridorRiskPenalty() { return highCorridorRiskPenalty; }
        public void setHighCorridorRiskPenalty(int highCorridorRiskPenalty) { this.highCorridorRiskPenalty = highCorridorRiskPenalty; }
        public int getAmountToLimitRatioPenalty() { return amountToLimitRatioPenalty; }
        public void setAmountToLimitRatioPenalty(int amountToLimitRatioPenalty) { this.amountToLimitRatioPenalty = amountToLimitRatioPenalty; }
        public int getHighVelocityPenalty() { return highVelocityPenalty; }
        public void setHighVelocityPenalty(int highVelocityPenalty) { this.highVelocityPenalty = highVelocityPenalty; }
        public Map<String, Integer> getCorridorRiskScores() { return corridorRiskScores; }
        public void setCorridorRiskScores(Map<String, Integer> corridorRiskScores) { this.corridorRiskScores = corridorRiskScores; }
    }
}
