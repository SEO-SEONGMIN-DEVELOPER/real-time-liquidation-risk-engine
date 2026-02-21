package com.liquidation.riskengine.domain.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "risk.cascade")
public class CascadeRiskProperties {

    private long throttleMs = 200;

    private Weights weights = new Weights();
    private Synthesis synthesis = new Synthesis();
    private Floor floor = new Floor();
    private double proximityThresholdPct = 10.0;

    @Getter
    @Setter
    public static class Weights {
        private double distance = 0.40;
        private double depth = 0.20;
        private double level = 0.15;
        private double cluster = 0.15;
        private double notional = 0.10;
    }

    @Getter
    @Setter
    public static class Synthesis {
        private double densityWeight = 0.6;
        private double pressureWeight = 0.4;
    }

    @Getter
    @Setter
    public static class Floor {
        private double criticalDistancePct = 1.0;
        private double highDistancePct = 2.0;
        private double mediumDistancePct = 5.0;
    }
}
