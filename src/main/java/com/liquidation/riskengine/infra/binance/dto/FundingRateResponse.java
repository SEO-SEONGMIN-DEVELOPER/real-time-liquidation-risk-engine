package com.liquidation.riskengine.infra.binance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class FundingRateResponse {
    private String symbol;
    private String fundingRate;
    private long fundingTime;
}
