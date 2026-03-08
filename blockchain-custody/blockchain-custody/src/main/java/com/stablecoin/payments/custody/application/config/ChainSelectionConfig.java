package com.stablecoin.payments.custody.application.config;

import com.stablecoin.payments.custody.domain.model.ChainSelectionWeights;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the chain selection engine.
 * Maps {@code app.custody.chain-selection.*} properties to {@link ChainSelectionWeights}.
 */
@Configuration
public class ChainSelectionConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.custody.chain-selection")
    public ChainSelectionProperties chainSelectionProperties() {
        return new ChainSelectionProperties();
    }

    @Bean
    public ChainSelectionWeights chainSelectionWeights(ChainSelectionProperties props) {
        return ChainSelectionWeights.builder()
                .costWeight(props.getCostWeight())
                .speedWeight(props.getSpeedWeight())
                .reliabilityWeight(props.getReliabilityWeight())
                .build();
    }

    /**
     * Mutable properties bean for Spring Boot's {@code @ConfigurationProperties} binding.
     * Defaults match {@link ChainSelectionWeights#defaults()}.
     */
    public static class ChainSelectionProperties {
        private double costWeight = ChainSelectionWeights.DEFAULT_COST_WEIGHT;
        private double speedWeight = ChainSelectionWeights.DEFAULT_SPEED_WEIGHT;
        private double reliabilityWeight = ChainSelectionWeights.DEFAULT_RELIABILITY_WEIGHT;

        public double getCostWeight() {
            return costWeight;
        }

        public void setCostWeight(double costWeight) {
            this.costWeight = costWeight;
        }

        public double getSpeedWeight() {
            return speedWeight;
        }

        public void setSpeedWeight(double speedWeight) {
            this.speedWeight = speedWeight;
        }

        public double getReliabilityWeight() {
            return reliabilityWeight;
        }

        public void setReliabilityWeight(double reliabilityWeight) {
            this.reliabilityWeight = reliabilityWeight;
        }
    }
}
