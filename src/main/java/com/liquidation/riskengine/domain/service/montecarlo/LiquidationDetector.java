package com.liquidation.riskengine.domain.service.montecarlo;

import com.liquidation.riskengine.domain.model.MonteCarloReport;
import com.liquidation.riskengine.domain.model.MonteCarloReport.HorizonResult;
import com.liquidation.riskengine.domain.model.MonteCarloReport.McRiskLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class LiquidationDetector {

    static final int[] DEFAULT_HORIZON_MINUTES = {10, 60, 240, 1440};
    private static final int RISK_LEVEL_HORIZON = 1440;

    public MonteCarloReport detect(String symbol, double[][] paths,
                                   double liquidationPrice, String positionSide,
                                   double sigma, int timeStepMinutes) {
        return detect(symbol, paths, liquidationPrice, positionSide,
                sigma, timeStepMinutes, DEFAULT_HORIZON_MINUTES);
    }

    public MonteCarloReport detect(String symbol, double[][] paths,
                                   double liquidationPrice, String positionSide,
                                   double sigma, int timeStepMinutes,
                                   int[] horizonMinutes) {
        int pathCount = paths.length;
        int totalSteps = paths[0].length - 1;
        boolean isLong = "LONG".equalsIgnoreCase(positionSide);

        long startNano = System.nanoTime();

        int[] firstPassageStep = findFirstPassage(paths, liquidationPrice, isLong, pathCount, totalSteps);

        List<HorizonResult> horizonResults = new ArrayList<>(horizonMinutes.length);
        for (int horizonMin : horizonMinutes) {
            int step = Math.min(horizonMin / timeStepMinutes, totalSteps);
            horizonResults.add(aggregateHorizon(paths, firstPassageStep, step, horizonMin, pathCount));
        }

        long calcDurationMicros = (System.nanoTime() - startNano) / 1_000;

        McRiskLevel riskLevel = deriveRiskLevel(horizonResults);

        log.debug("[LiqDetector] 감지 완료: symbol={}, side={}, liqPrice={}, riskLevel={}, elapsed={}μs",
                symbol, positionSide, liquidationPrice, riskLevel, calcDurationMicros);

        return MonteCarloReport.builder()
                .symbol(symbol)
                .currentPrice(paths[0][0])
                .liquidationPrice(liquidationPrice)
                .positionSide(positionSide)
                .sigma(sigma)
                .pathCount(pathCount)
                .horizons(horizonResults)
                .riskLevel(riskLevel)
                .timestamp(System.currentTimeMillis())
                .calcDurationMicros(calcDurationMicros)
                .build();
    }

    private int[] findFirstPassage(double[][] paths, double liqPrice,
                                   boolean isLong, int pathCount, int totalSteps) {
        int[] firstPassage = new int[pathCount];
        Arrays.fill(firstPassage, Integer.MAX_VALUE);

        for (int i = 0; i < pathCount; i++) {
            for (int t = 1; t <= totalSteps; t++) {
                boolean liquidated = isLong
                        ? paths[i][t] <= liqPrice
                        : paths[i][t] >= liqPrice;
                if (liquidated) {
                    firstPassage[i] = t;
                    break;
                }
            }
        }
        return firstPassage;
    }

    private HorizonResult aggregateHorizon(double[][] paths, int[] firstPassageStep,
                                           int step, int horizonMinutes, int pathCount) {
        int liquidatedCount = 0;
        double[] pricesAtStep = new double[pathCount];

        for (int i = 0; i < pathCount; i++) {
            if (firstPassageStep[i] <= step) {
                liquidatedCount++;
            }
            pricesAtStep[i] = paths[i][step];
        }

        Arrays.sort(pricesAtStep);

        return HorizonResult.builder()
                .minutes(horizonMinutes)
                .liquidationProbability((double) liquidatedCount / pathCount)
                .pricePercentile5(percentile(pricesAtStep, 5))
                .pricePercentile25(percentile(pricesAtStep, 25))
                .priceMedian(percentile(pricesAtStep, 50))
                .pricePercentile75(percentile(pricesAtStep, 75))
                .pricePercentile95(percentile(pricesAtStep, 95))
                .build();
    }

    private McRiskLevel deriveRiskLevel(List<HorizonResult> horizons) {
        return horizons.stream()
                .filter(h -> h.getMinutes() == RISK_LEVEL_HORIZON)
                .findFirst()
                .map(h -> McRiskLevel.fromProbability(h.getLiquidationProbability()))
                .orElse(McRiskLevel.fromProbability(
                        horizons.getLast().getLiquidationProbability()));
    }

    private double percentile(double[] sorted, int p) {
        double index = (p / 100.0) * (sorted.length - 1);
        int lower = (int) Math.floor(index);
        int upper = Math.min(lower + 1, sorted.length - 1);
        double fraction = index - lower;
        return sorted[lower] + fraction * (sorted[upper] - sorted[lower]);
    }
}
