package com.liquidation.riskengine.domain.service.montecarlo;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SimulationRequest {

    private final double startPrice;
    private final double sigma;

    @Builder.Default
    private final double mu = 0.0;

    @Builder.Default
    private final int pathCount = 10_000;

    @Builder.Default
    private final int timeStepMinutes = 1;

    @Builder.Default
    private final int horizonMinutes = 60;

    @Builder.Default
    private final boolean useFatTail = false;

    @Builder.Default
    private final double degreesOfFreedom = 5.0;
}
