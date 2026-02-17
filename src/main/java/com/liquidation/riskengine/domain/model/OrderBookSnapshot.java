package com.liquidation.riskengine.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderBookSnapshot {

    private String symbol;
    private List<PriceLevel> bids;
    private List<PriceLevel> asks;
    private BigDecimal bestBid;
    private BigDecimal bestAsk;
    private BigDecimal spread;
    private BigDecimal bidTotalQuantity;
    private BigDecimal askTotalQuantity;
    private long timestamp;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PriceLevel {
        private BigDecimal price;
        private BigDecimal quantity;
    }
}
