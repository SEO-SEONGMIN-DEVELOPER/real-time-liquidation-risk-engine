package com.liquidation.riskengine.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

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

    private long timestamp;
}
