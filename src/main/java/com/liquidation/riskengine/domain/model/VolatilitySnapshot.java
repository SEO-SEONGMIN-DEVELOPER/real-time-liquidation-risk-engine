package com.liquidation.riskengine.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VolatilitySnapshot {

    private String symbol;
    private double sigma1m;
    private double sigma5m;
    private double sigma1h;
    private double sigma24h;
    private EstimationMethod method;
    private int sampleCount;
    private long timestamp;

    public enum EstimationMethod {
        EWMA, GARCH
    }

    public double getSigmaForLabel(String label) {
        return switch (label) {
            case "1m" -> sigma1m;
            case "5m" -> sigma5m;
            case "1h" -> sigma1h;
            case "24h" -> sigma24h;
            default -> sigma1h;
        };
    }
}
