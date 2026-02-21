package com.liquidation.riskengine.domain.service.montecarlo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "montecarlo")
public class MonteCarloProperties {

    private boolean enabled = true;
    private int pathCount = 10_000;
    private int timeStepMinutes = 1;
    private List<Integer> horizons = List.of(10, 60, 240, 1440);
    private String volatilityWindow = "1h";
    private boolean useFatTail = false;
    private double degreesOfFreedom = 5.0;

    public int maxHorizonMinutes() {
        return horizons.stream().mapToInt(Integer::intValue).max().orElse(1440);
    }

    public int[] horizonsArray() {
        return horizons.stream().mapToInt(Integer::intValue).toArray();
    }
}
