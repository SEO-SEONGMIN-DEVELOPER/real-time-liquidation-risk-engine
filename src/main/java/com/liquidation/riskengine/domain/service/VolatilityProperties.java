package com.liquidation.riskengine.domain.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "volatility")
public class VolatilityProperties {

    private String model = "ewma";
    private double garchAlpha = 0.05;
    private double garchBeta = 0.90;
    private boolean garchAutoFit = false;
}
