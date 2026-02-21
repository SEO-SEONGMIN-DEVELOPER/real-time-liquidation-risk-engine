package com.liquidation.riskengine.domain.service.montecarlo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "drift")
public class DriftProperties {

    private boolean enabled = true;
    private double fundingWeight = 0.6;
    private double momentumWeight = 0.4;
    private double maxAnnualDrift = 2.0;
    private String momentumWindow = "1h";
}
