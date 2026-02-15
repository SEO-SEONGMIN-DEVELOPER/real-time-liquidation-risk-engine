package com.liquidation.riskengine.infra.binance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenInterestResponse {

    private String symbol;
    private BigDecimal openInterest;
    private Long time;
}
