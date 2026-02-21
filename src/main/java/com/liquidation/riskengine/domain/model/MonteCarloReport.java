package com.liquidation.riskengine.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MonteCarloReport {

    private String symbol;
    private double currentPrice;
    private double liquidationPrice;
    private String positionSide;
    private double sigma;
    private int pathCount;
    private List<HorizonResult> horizons;
    private McRiskLevel riskLevel;
    private long timestamp;
    private long calcDurationMicros;

    public enum McRiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL;

        public static McRiskLevel fromProbability(double probability) {
            if (probability >= 0.50) return CRITICAL;
            if (probability >= 0.25) return HIGH;
            if (probability >= 0.10) return MEDIUM;
            return LOW;
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HorizonResult {
        private int minutes;
        private double liquidationProbability;
        private double pricePercentile5;
        private double pricePercentile25;
        private double priceMedian;
        private double pricePercentile75;
        private double pricePercentile95;
    }
}
