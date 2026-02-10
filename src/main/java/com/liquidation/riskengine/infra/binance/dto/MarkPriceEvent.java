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
public class MarkPriceEvent {

    @JsonProperty("e")
    private String eventType;

    @JsonProperty("E")
    private Long eventTime;

    @JsonProperty("s")
    private String symbol;

    @JsonProperty("p")
    private BigDecimal markPrice;

    @JsonProperty("i")
    private BigDecimal indexPrice;

    @JsonProperty("P")
    private BigDecimal estimatedSettlePrice;

    @JsonProperty("r")
    private BigDecimal fundingRate;

    @JsonProperty("T")
    private Long nextFundingTime;
}
