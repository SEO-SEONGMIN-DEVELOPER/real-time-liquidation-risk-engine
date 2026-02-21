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
public class MonteCarloResult {

    private String symbol;
    private String positionSide;
    private double currentPrice;
    private double liquidationPrice;
    private int pathCount;
    private List<HorizonResult> horizons;
    private long timestamp;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HorizonResult {
        private String label;
        private int minutes;
        private double liquidationProbability;
        private int liquidatedPaths;
        private double pct5;
        private double pct25;
        private double pct50;
        private double pct75;
        private double pct95;
    }
}
