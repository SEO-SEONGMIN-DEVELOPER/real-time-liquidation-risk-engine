package com.liquidation.riskengine.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CascadeRiskReport {

    private String symbol;
    private BigDecimal currentPrice;
    private BigDecimal userLiquidationPrice;
    private String positionSide;

    private BigDecimal distance;
    private double distancePercent;
    private String direction;
    private BigDecimal priceRangeLow;
    private BigDecimal priceRangeHigh;

    private BigDecimal depthBetween;
    private BigDecimal notionalBetween;
    private int levelCount;
    private double depthRatio;

    private List<LiqCluster> clustersInPath;
    private int overlappingTierCount;
    private BigDecimal estimatedLiqVolume;

    private int oiPressureScore;
    private int liqIntensityScore;
    private int imbalanceScore;
    private int marketPressureTotal;

    private long timestamp;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LiqCluster {
        private int leverage;
        private BigDecimal price;
        private double weight;
        private BigDecimal estimatedVolume;
        private BigDecimal estimatedNotional;
        private double distanceFromCurrentPercent;
    }
}
