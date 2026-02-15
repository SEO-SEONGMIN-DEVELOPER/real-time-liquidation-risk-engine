package com.liquidation.riskengine.infra.binance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class ForceOrderEvent {

    @JsonProperty("e")
    private String eventType;

    @JsonProperty("E")
    private Long eventTime;

    @JsonProperty("o")
    private Order order;

    @Getter
    @NoArgsConstructor
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Order {

        @JsonProperty("s")
        private String symbol;

        @JsonProperty("S")
        private String side;

        @JsonProperty("o")
        private String orderType;

        @JsonProperty("f")
        private String timeInForce;

        @JsonProperty("q")
        private BigDecimal originalQuantity;

        @JsonProperty("p")
        private BigDecimal price;

        @JsonProperty("ap")
        private BigDecimal averagePrice;

        @JsonProperty("X")
        private String orderStatus;

        @JsonProperty("l")
        private BigDecimal lastFilledQuantity;

        @JsonProperty("z")
        private BigDecimal accumulatedFilledQuantity;

        @JsonProperty("T")
        private Long tradeTime;
    }
}
