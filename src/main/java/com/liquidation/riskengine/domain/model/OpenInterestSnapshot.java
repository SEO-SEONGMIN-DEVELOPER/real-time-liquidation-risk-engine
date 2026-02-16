package com.liquidation.riskengine.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenInterestSnapshot {

    private String symbol;
    private BigDecimal openInterest;
    private BigDecimal previousOpenInterest;
    private BigDecimal change;
    private BigDecimal changePercent;
    private long timestamp;
}
