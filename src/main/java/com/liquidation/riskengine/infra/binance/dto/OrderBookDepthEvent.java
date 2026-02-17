package com.liquidation.riskengine.infra.binance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@Getter
@NoArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderBookDepthEvent {

    @JsonProperty("e")
    private String eventType;

    @JsonProperty("E")
    private Long eventTime;

    @JsonProperty("T")
    private Long transactionTime;

    @JsonProperty("s")
    private String symbol;

    @JsonProperty("U")
    private Long firstUpdateId;

    @JsonProperty("u")
    private Long finalUpdateId;

    @JsonProperty("pu")
    private Long previousFinalUpdateId;

    @JsonProperty("b")
    private List<List<String>> bids;

    @JsonProperty("a")
    private List<List<String>> asks;
}
